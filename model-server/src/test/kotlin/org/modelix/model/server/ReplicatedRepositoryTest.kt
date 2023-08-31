/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.test.RandomModelChangeGenerator
import java.util.Collections
import java.util.SortedSet
import java.util.TreeSet
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class ReplicatedRepositoryTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
            ModelReplicationServer(InMemoryStoreClient()).init(this)
        }
        block()
    }

    @Test
    fun `sequential write from multiple clients`() = runTest {
        val url = "http://localhost/v2"
        val modelClient = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        val modelClient2 = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        modelClient.initRepository(repositoryId)

        val replicatedModel = modelClient.getReplicatedModel(repositoryId.getBranchReference())
        val replicatedModel2 = modelClient2.getReplicatedModel(repositoryId.getBranchReference())
        val branch1 = replicatedModel.start()
        val branch2 = replicatedModel2.start()

        val rand = Random(34554)

        for (changeId in 1..10) {
            println("change set $changeId")
            val branchToChange = if (rand.nextBoolean()) {
                println("changing branch 1")
                branch1
            } else {
                println("changing branch 2")
                branch2
            }
            branchToChange.runWrite {
                val changeGenerator = RandomModelChangeGenerator(branchToChange.getRootNode(), rand)
                for (i in 1..1000) changeGenerator.applyRandomChange()
                println("new tree: " + (branchToChange.transaction.tree as CLTree).hash)
            }

            val syncTime = measureTime {
                for (timeout in 1..1000) {
                    if (branch1.treeHash() == branch2.treeHash()) break
                    delay(1.milliseconds)
                }
            }
            println("synced after $syncTime")
            val data1 = branch1.computeRead {
                println("reading on branch 1: " + branch1.treeHash())
                branch1.getRootNode().asData()
            }
            val data2 = branch2.computeRead {
                println("reading on branch 2: " + branch2.treeHash())
                branch2.getRootNode().asData()
            }
            assertEquals(data1, data2)
        }
    }

    @Test
    fun `concurrent write`() = runTest {
        val url = "http://localhost/v2"
        val clients = (1..3).map {
            ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        }

        val repositoryId = RepositoryId("repo1")
        val initialVersion = clients[0].initRepository(repositoryId)
        val branchId = repositoryId.getBranchReference("my-branch")
        clients[0].push(branchId, initialVersion, initialVersion)
        val models = clients.map { client -> client.getReplicatedModel(branchId).also { it.start() } }

        val createdNodes: MutableSet<String> = Collections.synchronizedSet(TreeSet<String>())

        coroutineScope {
            suspend fun launchWriter(model: ReplicatedModel, seed: Int) {
                launch {
                    val rand = Random(seed)
                    for (i in 1..10) {
                        delay(rand.nextLong(50, 100))
                        model.getBranch().runWriteT { t ->
                            createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).toString(16)
                        }
                    }
                }
            }
            models.forEachIndexed { index, model ->
                launchWriter(model, 56456 + index)
                delay(200.milliseconds)
            }
        }

        fun getChildren(model: ReplicatedModel): SortedSet<String> {
            val branch = model.getBranch()
            return branch.computeRead {
                branch.getRootNode().allChildren.map { (it as PNodeAdapter).nodeId.toString(16) }.toSortedSet()
            }
        }

        assertEquals(clients.size * 10, createdNodes.size)

        runCatching {
            withTimeout(20.seconds) {
                models.forEach { model ->
                    while (getChildren(model) != createdNodes) delay(10.milliseconds)
                }
            }
        }

        for (model in models) {
            assertEquals(createdNodes, getChildren(model))
        }
    }
}

private fun IBranch.treeHash(): String = computeReadT { t -> (t.tree as CLTree).hash }

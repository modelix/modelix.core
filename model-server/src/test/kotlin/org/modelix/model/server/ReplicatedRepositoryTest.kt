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

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import org.junit.Test
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.IBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.test.RandomModelChangeGenerator
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
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
            ModelReplicationServer(InMemoryStoreClient()).init(this)
        }
        block()
    }

    @Test
    fun test_t1() = runTest {
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
}

private fun IBranch.treeHash(): String = computeReadT { t -> (t.tree as CLTree).hash }

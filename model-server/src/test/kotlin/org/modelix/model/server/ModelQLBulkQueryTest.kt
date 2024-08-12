/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import org.modelix.authorization.installAuthentication
import org.modelix.model.IKeyValueStore
import org.modelix.model.IVersion
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.NullChildLink
import org.modelix.model.api.PBranch
import org.modelix.model.api.TreePointer
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getRootNode
import org.modelix.model.async.SimpleBulkQuery
import org.modelix.model.client.IdGenerator
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.IModelClientV2Internal
import org.modelix.model.client2.lazyLoadVersion
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CacheConfiguration
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.server.api.v2.ObjectHash
import org.modelix.model.server.api.v2.SerializedObject
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forContextRepository
import org.modelix.model.server.store.forRepository
import org.modelix.modelql.core.IMonoUnboundQuery
import org.modelix.modelql.core.buildMonoQuery
import org.modelix.modelql.core.count
import org.modelix.modelql.untyped.createQueryExecutor
import org.modelix.modelql.untyped.descendants
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("ktlint:standard:annotation", "ktlint:standard:spacing-between-declarations-with-annotations")
class ModelQLBulkQueryTest {
    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            val store = InMemoryStoreClient().forContextRepository()
            ModelReplicationServer(store).init(this)
            IdsApiImpl(store).init(this)
        }
        block()
    }

    @Test
    fun test1() = runModelQLTest(buildMonoQuery { it.descendants(true).count() })

    private fun <T> runModelQLTest(query: IMonoUnboundQuery<INode, T>) = kotlinx.coroutines.test.runTest {
        val store = InMemoryStoreClient()
        val statistics = StoreClientWithStatistics(store)
        val localClient = LocalModelClient(statistics.forRepository(RepositoryId("my-repo")))
        val version = createModel(localClient, 1234)

        val store2 = LocalModelClient(statistics.forRepository(RepositoryId("my-repo"))).storeCache
        val model = TreePointer(CLVersion.loadFromHash(version.getContentHash(), store2).getTree())
        val rootNode = model.getRootNode()
        val requestCountBefore = statistics.getTotalRequests()
        val result = rootNode.getArea().runWithAdditionalScopeInCoroutine {
            SimpleBulkQuery.startQuery(store2) {
                rootNode.createQueryExecutor().createFlow(query)
            }.single()
        }
        val requestCountAfter = statistics.getTotalRequests()
        println("Number of requests: ${requestCountAfter - requestCountBefore}")
        println(result.value)
    }

    private suspend fun createModel(store: IKeyValueStore, numberOfNodes: Int): IVersion {
        val initialTree = CLTree.builder(ObjectStoreCache(store)).repositoryId(RepositoryId("xxx")).build()
        val branch = PBranch(initialTree, IdGenerator.newInstance(100))
        val rootNode = branch.getRootNode()
        branch.runWrite {
            fun createNodes(parentNode: INode, numberOfNodes: Int, rand: Random) {
                if (numberOfNodes == 0) return
                if (numberOfNodes == 1) {
                    parentNode.addNewChild(NullChildLink, 0)
                    return
                }
                val numChildren = rand.nextInt(2, 10.coerceAtMost(numberOfNodes) + 1)
                val subtreeSize = numberOfNodes / numChildren
                val remainder = numberOfNodes % numChildren
                for (i in 1..numChildren) {
                    createNodes(parentNode.addNewChild(NullChildLink, 0), subtreeSize - 1 + (if (i == 1) remainder else 0), rand)
                }
            }

            createNodes(rootNode, numberOfNodes, Random(10001))
        }
        val initialVersion = CLVersion.createRegularVersion(
            id = 1000L,
            time = null,
            author = null,
            tree = branch.computeReadT { it.tree } as CLTree,
            baseVersion = null,
            operations = emptyArray(),
        )
        initialVersion.write()
        return initialVersion
    }
}
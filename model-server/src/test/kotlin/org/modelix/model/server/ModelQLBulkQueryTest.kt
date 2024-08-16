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

import com.badoo.reaktive.maybe.blockingGet
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.single.blockingGet
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.authorization.installAuthentication
import org.modelix.model.IKeyValueStore
import org.modelix.model.IVersion
import org.modelix.model.api.INode
import org.modelix.model.api.NullChildLink
import org.modelix.model.api.PBranch
import org.modelix.model.api.TreePointer
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getAncestors
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.async.AsyncStoreAsStore
import org.modelix.model.async.SynchronousStoreAsAsyncStore
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CacheConfiguration
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.CPTree
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

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

    private fun <T> runModelQLTest(query: IMonoUnboundQuery<INode, T>) = kotlinx.coroutines.test.runTest(timeout = 20.minutes) {
        val db = InMemoryStoreClient()
        val statistics = StoreClientWithStatistics(db)
        val treeHash: String = suspend {
            val localClient = LocalModelClient(statistics.forRepository(RepositoryId("my-repo")))
            (createModel(localClient, 1234).getTree() as CLTree).hash
        }()

        val kvStore = LocalModelClient(statistics.forRepository(RepositoryId("my-repo")))
        val objectStore = ObjectStoreCache(kvStore, CacheConfiguration().also { it.prefetchCacheSize = 0 })
        val bulkQuery = objectStore.newBulkQuery()
        val asyncStore = SynchronousStoreAsAsyncStore(objectStore, bulkQuery)
        val model = TreePointer(asyncStore.get(KVEntryReference(treeHash, CPTree.DESERIALIZER)).blockingGet().let { CLTree(it!!, AsyncStoreAsStore(asyncStore)) })
        val rootNode = model.getRootNode()
        val requestCountBefore = statistics.getTotalRequests()
        val result = rootNode.getArea().runWithAdditionalScopeInCoroutine {
            val start = TimeSource.Monotonic.markNow()
            val result = query.bind(rootNode.createQueryExecutor()).asFlow().toList()
            bulkQuery.executeQuery()
            println(start.elapsedNow())
            result.blockingGet()
        }
        val requestCountAfter = statistics.getTotalRequests()
        println("Number of requests: ${requestCountAfter - requestCountBefore}")
        println(result)
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
                val numChildren = rand.nextInt(10, 20).coerceAtMost(numberOfNodes)
                val subtreeSize = numberOfNodes / numChildren
                val remainder = numberOfNodes % numChildren
                for (i in 1..numChildren) {
                    createNodes(parentNode.addNewChild(NullChildLink, 0), subtreeSize - 1 + (if (i == 1) remainder else 0), rand)
                }
            }

            createNodes(rootNode, numberOfNodes, Random(10001))

            println("Max depth: " + rootNode.getDescendants(true).maxOf { it.getAncestors(true).count() })
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
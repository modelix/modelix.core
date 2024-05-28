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
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.INode
import org.modelix.model.api.NullChildLink
import org.modelix.model.api.PBranch
import org.modelix.model.api.TreePointer
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
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
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.forContextRepository
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("ktlint:standard:annotation", "ktlint:standard:spacing-between-declarations-with-annotations")
class LazyLoadingTest {
    private var totalRequests: Long = 0
    private var totalObjects: Long = 0
    private var maxRequestSize: Int = 0

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

    private fun measureRequests(body: () -> Unit): Pair<Int, Int> {
        maxRequestSize = 0
        val before = totalRequests
        val objectsBefore = totalObjects
        body()
        val after = totalRequests
        val objectsAfter = totalObjects
        val requestCount = (after - before).toInt()
        val requestedObjectsCount = (objectsAfter - objectsBefore).toInt()
        println("Requests: $requestCount")
        println("Requested Objects: $requestedObjectsCount")
        println("Max Request Size: $maxRequestSize")
        return Pair(requestCount, requestedObjectsCount)
    }

    @Test fun compare_batch_size_10() = compare_batch_size(10, 22, 201, 187, 137, 1996, 1863)
    @Test fun compare_batch_size_25() = compare_batch_size(25, 22, 103, 82, 285, 2575, 2050)
    @Test fun compare_batch_size_50() = compare_batch_size(50, 22, 87, 72, 510, 4350, 3600)
    @Test fun compare_batch_size_100() = compare_batch_size(100, 22, 60, 49, 905, 6000, 4900)
    @Test fun compare_batch_size_200() = compare_batch_size(200, 22, 142, 160, 1605, 28400, 32000)
    @Test fun compare_batch_size_400() = compare_batch_size(400, 22, 743, 138, 2627, 216698, 18369)
    @Test fun compare_batch_size_800() = compare_batch_size(800, 22, 717, 185, 2700, 205909, 23806)
    @Test fun compare_batch_size_1600() = compare_batch_size(1600, 22, 717, 185, 2700, 205909, 23806)
    fun compare_batch_size(batchSize: Int, vararg expected: Int) = runLazyLoadingTest(DepthFirstSearchPattern, 1_000, 1_000, batchSize, batchSize, *expected)

    @Test fun compare_cache_size_100() = compare_cache_size(100, 22, 861, 891, 510, 43050, 44550)
    @Test fun compare_cache_size_200() = compare_cache_size(200, 22, 539, 490, 510, 26950, 24500)
    @Test fun compare_cache_size_400() = compare_cache_size(400, 22, 217, 139, 510, 10850, 6950)
    @Test fun compare_cache_size_800() = compare_cache_size(800, 22, 57, 73, 510, 2850, 3650)
    @Test fun compare_cache_size_1600() = compare_cache_size(1600, 22, 44, 41, 510, 2200, 2050)
    @Test fun compare_cache_size_3200() = compare_cache_size(3200, 22, 34, 0, 510, 1561, 0)
    private fun compare_cache_size(cacheSize: Int, vararg expected: Int) = runLazyLoadingTest(DepthFirstSearchPattern, 1_000, cacheSize, 50, 50, *expected)

    @Test fun compare_prefetch_size_0() = compare_prefetch_size(0, 22, 2055, 2073, 22, 2055, 2073)
    @Test fun compare_prefetch_size_2() = compare_prefetch_size(2, 22, 1043, 1046, 38, 2086, 2092)
    @Test fun compare_prefetch_size_4() = compare_prefetch_size(3, 22, 693, 708, 53, 2079, 2124)
    @Test fun compare_prefetch_size_10() = compare_prefetch_size(10, 22, 288, 333, 137, 2880, 3330)
    @Test fun compare_prefetch_size_25() = compare_prefetch_size(25, 22, 339, 296, 285, 8475, 7400)
    @Test fun compare_prefetch_size_50() = compare_prefetch_size(50, 22, 539, 490, 510, 26950, 24500)
    private fun compare_prefetch_size(prefetchSize: Int, vararg expected: Int) = runLazyLoadingTest(DepthFirstSearchPattern, 1_000, 200, 50, prefetchSize, *expected)

    @Test fun compare_access_pattern_dfs() = compare_access_pattern(DepthFirstSearchPattern, 22, 123, 133, 510, 6150, 6650)
    @Test fun compare_access_pattern_pdfs() = compare_access_pattern(ParallelDepthFirstSearchPattern, 22, 325, 174, 510, 16250, 8700)
    @Test fun compare_access_pattern_bfs() = compare_access_pattern(BreathFirstSearchPattern, 22, 320, 278, 510, 16000, 13900)
    @Test fun compare_access_pattern_random() = compare_access_pattern(RandomPattern(1_000, Random(987)), 22, 194, 154, 510, 5388, 3878)
    private fun compare_access_pattern(pattern: AccessPattern, vararg expected: Int) = runLazyLoadingTest(pattern, 1_000, 500, 50, 50, *expected)

    private fun runLazyLoadingTest(accessPattern: AccessPattern, numberOfNodes: Int, cacheSize: Int, batchSize: Int, prefetchSize: Int, vararg expectedRequests: Int) {
        runLazyLoadingTest(accessPattern, numberOfNodes, cacheSize, batchSize, prefetchSize, expectedRequests.toList())
    }

    private fun runLazyLoadingTest(accessPattern: AccessPattern, numberOfNodes: Int, cacheSize: Int, batchSize: Int, prefetchSize: Int, expectedRequests: List<Int>) = runTest {
        val client = ModelClientWithStatistics(createModelClient())
        val branchRef = RepositoryId("my-repo").getBranchReference()
        createModel(client, branchRef, numberOfNodes)

        val version = client.lazyLoadVersion(
            branchRef,
            CacheConfiguration().also {
                it.cacheSize = cacheSize
                it.requestBatchSize = batchSize
                it.prefetchBatchSize = prefetchSize
            },
        )
        val rootNode = TreePointer(version.getTree()).getRootNode()

        val actualRequestCount = ArrayList<Int>()

        // Traverse to the first leaf node. This should load some data, but not the whole model.
        actualRequestCount += measureRequests {
            generateSequence(rootNode) { it.allChildren.firstOrNull() }.count()
        }.toList()

        // Traverse the whole model.
        actualRequestCount += measureRequests {
            accessPattern.runPattern(rootNode)
        }.toList()

        // Traverse the whole model a second time. The model doesn't fit into the cache and some parts are already
        // unloaded during the first traversal. The unloaded parts need to be requested again.
        // But the navigation to the first leaf is like a warmup of the cache for the whole model traversal.
        // The previous traversal can benefit from that, but the next one cannot and is expected to need more requests.
        actualRequestCount += measureRequests {
            accessPattern.runPattern(rootNode)
        }.toList()

        // move request count before object count
        val reorderedActualRequests = actualRequestCount.withIndex().sortedBy { it.index % 2 }.map { it.value }

        assertEquals(expectedRequests, reorderedActualRequests)
    }

    /**
     * Creates a CLTree with fixed ID and a version without timestamp.
     * This ensures that exactly the same data is created for each test run which avoids non-deterministic test results.
     */
    private suspend fun createModel(client: IModelClientV2, branchRef: BranchReference, numberOfNodes: Int) {
        val initialTree = CLTree.builder(ObjectStoreCache(MapBasedStore())).repositoryId(RepositoryId("xxx")).build()
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
        client.push(branchRef, initialVersion, null)
    }

    private inner class ModelClientWithStatistics(val client: IModelClientV2Internal) : IModelClientV2Internal by client {
        override suspend fun getObjects(repository: RepositoryId, keys: Sequence<String>): Map<ObjectHash, SerializedObject> {
            totalRequests++
            totalObjects += keys.count()
            return client.getObjects(repository, keys)
        }
    }
}

private interface AccessPattern {
    fun runPattern(rootNode: INode)
}

private object DepthFirstSearchPattern : AccessPattern {
    override fun runPattern(rootNode: INode) {
        rootNode.getDescendants(true).count()
    }
}

private object ParallelDepthFirstSearchPattern : AccessPattern {
    override fun runPattern(rootNode: INode) {
        rootNode.getDescendants(true).zip(rootNode.getDescendants(true).drop(100)).count()
    }
}

private object BreathFirstSearchPattern : AccessPattern {
    override fun runPattern(rootNode: INode) {
        val queue = ArrayDeque<INode>()
        queue.addLast(rootNode)
        while (queue.isNotEmpty()) {
            queue.addAll(queue.removeFirst().allChildren)
        }
    }
}

private class RandomPattern(val maxAccessOperations: Int, val random: kotlin.random.Random) : AccessPattern {
    override fun runPattern(rootNode: INode) {
        var currentNode = rootNode

        for (i in 1..maxAccessOperations) {
            val nextNode = when (random.nextInt(2)) {
                0 -> currentNode.parent ?: currentNode.allChildren.toList().random(random)
                else -> currentNode.allChildren.toList().let {
                    if (it.isEmpty()) currentNode.parent!! else it.random(random)
                }
            }
            currentNode = nextNode
        }
    }
}

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
import org.modelix.model.api.TreePointer
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.lazyLoadVersion
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LazyLoadingTest {

    private lateinit var statistics: StoreClientWithStatistics

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            statistics = StoreClientWithStatistics(InMemoryStoreClient())
            ModelReplicationServer(statistics).init(this)
        }
        block()
    }

    private fun assertRequestCount(expected: IntRange, body: () -> Unit) {
        val requestCount = measureRequests(body)
        assertContains(expected, requestCount)
    }

    private fun measureRequests(body: () -> Unit): Int {
        val before = statistics.getTotalRequests()
        val objectsBefore = statistics.getTotalRequestedObjects()
        body()
        val after = statistics.getTotalRequests()
        val objectsAfter = statistics.getTotalRequestedObjects()
        val requestCount = (after - before).toInt()
        println("Requests: $requestCount")
        println("Requested Objects: ${objectsAfter - objectsBefore}")
        return requestCount
    }

    @Test fun lazy_loading_500_10000_500_500() = runLazyLoadingTest(DepthFirstSearchPattern, 500, 10_000, 500, 500, 28, 2, 0)
    @Test fun lazy_loading_5000_10000_500_500() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 10_000, 500, 500, 39, 15, 56)

    /*
     * For 50_000 nodes there are ~100_000 objects in the database.
     * With a batch size of 500 at least 200 request are required, but 100 % efficiency is not achievable.
     */
    @Test fun lazy_loading_50000_10000_500_500_dfs() = runLazyLoadingTest(DepthFirstSearchPattern, 50_000, 10_000, 500, 500, 56, 792, 825)
    @Test fun lazy_loading_50000_10000_500_500_random() = runLazyLoadingTest(RandomPattern(5000, Random(236767)), 50_000, 10_000, 500, 500, 56, 792, 825)
    @Test fun lazy_loading_50000_10000_500_500_pdfs() = runLazyLoadingTest(ParallelDepthFirstSearchPattern, 50_000, 10_000, 500, 500, 56, 825, 852)
    @Test fun lazy_loading_50000_10000_500_500_bfs() = runLazyLoadingTest(BreathFirstSearchPattern, 50_000, 10_000, 500, 500, 55, 181204, 179691)

    @Test fun lazy_loading_500_10000_50_50() = runLazyLoadingTest(DepthFirstSearchPattern, 500, 10_000, 50, 50, 28, 13, 0)
    @Test fun lazy_loading_5000_10000_50_50() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 10_000, 50, 50, 39, 191, 81)
    @Test fun lazy_loading_50000_10000_50_50() = runLazyLoadingTest(DepthFirstSearchPattern, 50_000, 10_000, 50, 50, 56, 2125, 2173)

    @Test fun lazy_loading_5000_500_500_500() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 500, 500, 500, 99, 1443, 1443)
    @Test fun lazy_loading_5000_50000_500_500() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 50_000, 500, 500, 41, 15, 0)

    @Test fun lazy_loading_2000_100_5_5() = runLazyLoadingTest(DepthFirstSearchPattern, 2_000, 100, 5, 5, 36, 1073, 1059)
    @Test fun lazy_loading_20000_1000_50_50() = runLazyLoadingTest(DepthFirstSearchPattern, 20_000, 1_000, 50, 50, 49, 2071, 2081)

    @Test fun lazy_loading_5000_5000_500_0() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 0, 39, 6183, 6218)
    @Test fun lazy_loading_5000_5000_500_1() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 1, 41, 6183, 6218)
    @Test fun lazy_loading_5000_5000_500_2() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 2, 40, 5133, 5170)
    @Test fun lazy_loading_5000_5000_500_4() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 4, 39, 2554, 2591)
    @Test fun lazy_loading_5000_5000_500_8() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 8, 41, 1268, 1307)
    @Test fun lazy_loading_5000_5000_500_16() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 16, 40, 625, 664)
    @Test fun lazy_loading_5000_5000_500_32() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 32, 39, 307, 351)
    @Test fun lazy_loading_5000_5000_500_64() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 64, 40, 149, 200)
    @Test fun lazy_loading_5000_5000_500_125() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 125, 40, 74, 130)
    @Test fun lazy_loading_5000_5000_500_250() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 250, 40, 70, 94)
    @Test fun lazy_loading_5000_5000_500_500() = runLazyLoadingTest(DepthFirstSearchPattern, 5_000, 5_000, 500, 500, 41, 85, 127)


    private fun runLazyLoadingTest(accessPattern: AccessPattern, numberOfNodes: Int, cacheSize: Int, batchSize: Int, prefetchSize: Int, vararg expectedRequests: Int) {
        runLazyLoadingTest(accessPattern, numberOfNodes, cacheSize, batchSize, prefetchSize, expectedRequests.toList())
    }

    private fun runLazyLoadingTest(accessPattern: AccessPattern, numberOfNodes: Int, cacheSize: Int, batchSize: Int, prefetchSize: Int, expectedRequests: List<Int>) = runTest {
        // After optimizing the lazy loading to send less (but bigger) requests, this test might fail.
        // Just update the model size, cache size and expected request count to fix it.

        val client = createModelClient()
        val branchRef = RepositoryId("my-repo").getBranchReference()
        client.runWrite(branchRef) {
            fun createNodes(parentNode: INode, numberOfNodes: Int) {
                if (numberOfNodes == 0) return
                if (numberOfNodes == 1) {
                    parentNode.addNewChild(NullChildLink, 0)
                    return
                }
                val subtreeSize1 = numberOfNodes / 2
                val subtreeSize2 = numberOfNodes - subtreeSize1
                createNodes(parentNode.addNewChild(NullChildLink, 0), subtreeSize1 - 1)
                createNodes(parentNode.addNewChild(NullChildLink, 1), subtreeSize2 - 1)
            }

            createNodes(it, numberOfNodes)
        }
        val version = client.lazyLoadVersion(branchRef, cacheSize, batchSize, prefetchSize)

        val rootNode = TreePointer(version.getTree()).getRootNode()

        val actualRequestCount = ArrayList<Int>()

        // Traverse to the first leaf node. This should load some data, but not the whole model.
       actualRequestCount += measureRequests {
            generateSequence(rootNode) { it.allChildren.firstOrNull() }.count()
        }

        // Traverse the whole model.
        actualRequestCount += measureRequests {
            accessPattern.runPattern(rootNode)
        }

        // Traverse the whole model a second time. The model doesn't fit into the cache and some parts are already
        // unloaded during the first traversal. The unloaded parts need to be requested again.
        // But the navigation to the first leaf is like a warmup of the cache for the whole model traversal.
        // The previous traversal can benefit from that, but the next one cannot and is expected to need more requests.
        actualRequestCount += measureRequests {
            accessPattern.runPattern(rootNode)
        }

        //assertEquals(expectedRequests, actualRequestCount)
        assertEquals(expectedRequests.zip(actualRequestCount).map { minOf(it.first, it.second) }, actualRequestCount)
    }
}

private abstract class AccessPattern {
    abstract fun runPattern(rootNode: INode)
}

private object DepthFirstSearchPattern : AccessPattern() {
    override fun runPattern(rootNode: INode) {
        rootNode.getDescendants(true).count()
    }
}

private object ParallelDepthFirstSearchPattern : AccessPattern() {
    override fun runPattern(rootNode: INode) {
        rootNode.getDescendants(true).zip(rootNode.getDescendants(true).drop(100)).count()
    }
}

private object BreathFirstSearchPattern : AccessPattern() {
    override fun runPattern(rootNode: INode) {
        val queue = ArrayDeque<INode>()
        queue.addLast(rootNode)
        while (queue.isNotEmpty()) {
            queue.addAll(queue.removeFirst().allChildren)
        }
    }
}

private class RandomPattern(val maxAccessOperations: Int, val random: kotlin.random.Random) : AccessPattern() {
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

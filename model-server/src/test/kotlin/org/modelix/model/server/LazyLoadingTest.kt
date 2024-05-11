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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        body()
        val after = statistics.getTotalRequests()
        val requestCount = (after - before).toInt()
        println("Requests: $requestCount")
        return requestCount
    }

    @Test fun lazy_loading_500_10000_500_500() = runLazyLoadingTest(500, 10_000, 500, 500, 27, 1, 0)
    @Test fun lazy_loading_5000_10000_500_500() = runLazyLoadingTest(5_000, 10_000, 500, 500, 40, 50, 55)
    @Test fun lazy_loading_50000_10000_500_500() = runLazyLoadingTest(50_000, 10_000, 500, 500, 56, 1882, 1715)

    @Test fun lazy_loading_500_10000_50_50() = runLazyLoadingTest(500, 10_000, 50, 50, 28, 14, 0)
    @Test fun lazy_loading_5000_10000_50_50() = runLazyLoadingTest(5_000, 10_000, 50, 50, 42, 202, 61)
    @Test fun lazy_loading_50000_10000_50_50() = runLazyLoadingTest(50_000, 10_000, 50, 50, 55, 4204, 4758)

    @Test fun lazy_loading_5000_500_500_500() = runLazyLoadingTest(5_000, 500, 500, 500, 87, 1441, 1421)
    @Test fun lazy_loading_5000_5000_500_500() = runLazyLoadingTest(5_000, 5_000, 500, 500, 40, 161, 181)
    @Test fun lazy_loading_5000_50000_500_500() = runLazyLoadingTest(5_000, 50_000, 500, 500, 39, 14, 0)

    @Test fun lazy_loading_2000_100_5_5() = runLazyLoadingTest(2_000, 100, 5, 5, 40, 1874, 1838)
    @Test fun lazy_loading_20000_1000_50_50() = runLazyLoadingTest(20_000, 1_000, 50, 50, 50, 6396, 5521)

    @Test fun lazy_loading_5000_10000_5000_0() = runLazyLoadingTest(5_000, 10_000, 5_000, 0, 40, 6176, 6182)
    @Test fun lazy_loading_5000_10000_5000_1() = runLazyLoadingTest(5_000, 10_000, 5_000, 1, 39, 6180, 6183)
    @Test fun lazy_loading_5000_10000_5000_2() = runLazyLoadingTest(5_000, 10_000, 5_000, 2, 39, 6054, 1510)
    @Test fun lazy_loading_5000_10000_5000_4() = runLazyLoadingTest(5_000, 10_000, 5_000, 4, 40, 3017, 532)
    @Test fun lazy_loading_5000_10000_5000_8() = runLazyLoadingTest(5_000, 10_000, 5_000, 8, 39, 1837, 355)
    @Test fun lazy_loading_5000_10000_5000_16() = runLazyLoadingTest(5_000, 10_000, 5_000, 16, 41, 1184, 225)
    @Test fun lazy_loading_5000_10000_5000_32() = runLazyLoadingTest(5_000, 10_000, 5_000, 32, 40, 653, 172)
    @Test fun lazy_loading_5000_10000_5000_64() = runLazyLoadingTest(5_000, 10_000, 5_000, 64, 39, 381, 108)
    @Test fun lazy_loading_5000_10000_5000_128() = runLazyLoadingTest(5_000, 10_000, 5_000, 128, 40, 176, 67)
    @Test fun lazy_loading_5000_10000_5000_256() = runLazyLoadingTest(5_000, 10_000, 5_000, 256, 40, 92, 56)
    @Test fun lazy_loading_5000_10000_5000_512() = runLazyLoadingTest(5_000, 10_000, 5_000, 512, 40, 52, 53)
    @Test fun lazy_loading_5000_10000_5000_1024() = runLazyLoadingTest(5_000, 10_000, 5_000, 1024, 40, 16, 57)
    @Test fun lazy_loading_5000_10000_5000_2048() = runLazyLoadingTest(5_000, 10_000, 5_000, 2048, 39, 61, 56)
    @Test fun lazy_loading_5000_10000_5000_5000() = runLazyLoadingTest(5_000, 10_000, 5_000, 5000, 39, 61, 56)

    fun runLazyLoadingTest(numberOfNodes: Int, cacheSize: Int, batchSize: Int, prefetchSize: Int, vararg expectedRequests: Int) {
        runLazyLoadingTest(numberOfNodes, cacheSize, batchSize, prefetchSize, expectedRequests.toList())
    }

    fun runLazyLoadingTest(numberOfNodes: Int, cacheSize: Int, batchSize: Int, prefetchSize: Int, expectedRequests: List<Int>) = runTest {
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
            rootNode.getDescendants(true).count()
        }

        // Traverse the whole model a second time. The model doesn't fit into the cache and some parts are already
        // unloaded during the first traversal. The unloaded parts need to be requested again.
        // But the navigation to the first leaf is like a warmup of the cache for the whole model traversal.
        // The previous traversal can benefit from that, but the next one cannot and is expected to need more requests.
        actualRequestCount += measureRequests {
            rootNode.getDescendants(true).count()
        }

        assertEquals(expectedRequests, actualRequestCount)
    }
}

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

    private fun assertRequestCount(atLeast: Long, body: () -> Unit): Long {
        val requestCount = measureRequests(body)
        assertTrue(requestCount >= atLeast, "At least $atLeast requests expected, but was $requestCount")
        return requestCount
    }

    private fun measureRequests(body: () -> Unit): Long {
        val before = statistics.getTotalRequests()
        body()
        val after = statistics.getTotalRequests()
        val requestCount = after - before
        println("Requests: $requestCount")
        return requestCount
    }

    @Test
    fun `model data is loaded on demand`() = runTest {
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

            createNodes(it, 5_000)
        }
        val version = client.lazyLoadVersion(branchRef, cacheSize = 500)

        val rootNode = TreePointer(version.getTree()).getRootNode()

        // Traverse to the first leaf node. This should load some data, but not the whole model.
        assertRequestCount(1) {
            generateSequence(rootNode) { it.allChildren.firstOrNull() }.count()
        }

        // Traverse the whole model.
        val requestCountFirstTraversal = assertRequestCount(10) {
            rootNode.getDescendants(true).count()
        }

        // Traverse the whole model a second time. The model doesn't fit into the cache and some parts are already
        // unloaded during the first traversal. The unloaded parts need to be requested again.
        // But the navigation to the first leaf is like a warmup of the cache for the whole model traversal.
        // The previous traversal can benefit from that, but the next one cannot and is expected to need more requests.
        assertRequestCount(requestCountFirstTraversal + 1) {
            rootNode.getDescendants(true).count()
        }
    }
}

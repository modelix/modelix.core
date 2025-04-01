package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.model.api.INode
import org.modelix.model.api.NullChildLink
import org.modelix.model.api.TreePointer
import org.modelix.model.api.addNewChild
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.streams.querySuspending
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("ktlint:standard:annotation", "ktlint:standard:spacing-between-declarations-with-annotations")
class LazyLoadingTest {
    private val serverSideStore = StoreClientWithStatistics(InMemoryStoreClient())

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installDefaultServerPlugins()
            val repoManager = RepositoriesManager(serverSideStore)
            ModelReplicationServer(repoManager).init(this)
            IdsApiImpl(repoManager).init(this)
        }
        block()
    }

    private suspend fun measureRequests(body: suspend () -> Unit): Pair<Int, Int> {
        serverSideStore.resetMaxRequestSize()
        val before = serverSideStore.getTotalRequests()
        val objectsBefore = serverSideStore.getTotalObjects()
        body()
        val after = serverSideStore.getTotalRequests()
        val objectsAfter = serverSideStore.getTotalObjects()
        val requestCount = (after - before).toInt()
        val requestedObjectsCount = (objectsAfter - objectsBefore).toInt()
        println("Requests: $requestCount")
        println("Requested Objects: $requestedObjectsCount")
        println("Max Request Size: ${serverSideStore.getMaxRequestSize()}")
        return Pair(requestCount, requestedObjectsCount)
    }

    @Test fun compare_access_pattern_dfs() = compare_access_pattern(DepthFirstSearchPattern, 40, 4102, 0, 40, 4102, 0)
    @Test fun compare_access_pattern_pdfs() = compare_access_pattern(ParallelDepthFirstSearchPattern, 42, 4100, 0, 42, 4100, 0)
    @Test fun compare_access_pattern_bfs() = compare_access_pattern(BreathFirstSearchPattern, 42, 4100, 0, 42, 4100, 0)
    @Test fun compare_access_pattern_streams() = compare_access_pattern(StreamBasedApi, 40, 36, 0, 40, 36, 0)
    @Test fun compare_access_pattern_random() = compare_access_pattern(RandomPattern(1_000, Random(987)), 40, 926, 446, 40, 926, 446)
    private fun compare_access_pattern(pattern: AccessPattern, vararg expected: Int) = runLazyLoadingTest(pattern, 1_000, 500, 50, 50, *expected)

    private fun runLazyLoadingTest(accessPattern: AccessPattern, numberOfNodes: Int, cacheSize: Int, batchSize: Int, prefetchSize: Int, vararg expectedRequests: Int) {
        runLazyLoadingTest(accessPattern, numberOfNodes, cacheSize, batchSize, prefetchSize, expectedRequests.toList())
    }

    private fun runLazyLoadingTest(accessPattern: AccessPattern, numberOfNodes: Int, cacheSize: Int, batchSize: Int, prefetchSize: Int, expectedRequests: List<Int>) = runTest {
        val branchRef = RepositoryId("my-repo").getBranchReference()
        createModel(createModelClient(), branchRef, numberOfNodes)

        val version = createModelClient().lazyLoadVersion(
            branchRef,
//            CacheConfiguration().also {
//                it.cacheSize = cacheSize
//                it.requestBatchSize = batchSize
//            },
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

        // allow some tolerance to fix flaky tests
        if (expectedRequests.zip(reorderedActualRequests).any { abs(it.first - it.second) > 2 }) {
            assertEquals(expectedRequests, reorderedActualRequests)
        }
    }

    /**
     * Creates a CLTree with fixed ID and a version without timestamp.
     * This ensures that exactly the same data is created for each test run which avoids non-deterministic test results.
     */
    private suspend fun createModel(client: IModelClientV2, branchRef: BranchReference, numberOfNodes: Int) {
        val emptyVersion = client.initRepository(RepositoryId("xxx"))
        val initialVersion = emptyVersion.runWrite(client) { branch ->
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
        }!!
        client.push(branchRef, initialVersion, null)
    }
}

private interface AccessPattern {
    suspend fun runPattern(rootNode: INode)
}

private object DepthFirstSearchPattern : AccessPattern {
    override suspend fun runPattern(rootNode: INode) {
        rootNode.getDescendants(true).count()
    }
}

private object StreamBasedApi : AccessPattern {
    override suspend fun runPattern(rootNode: INode) {
        val asyncNode = rootNode.asAsyncNode()
        asyncNode.querySuspending { asyncNode.getDescendants(true).count() }
    }
}

private object ParallelDepthFirstSearchPattern : AccessPattern {
    override suspend fun runPattern(rootNode: INode) {
        rootNode.getDescendants(true).zip(rootNode.getDescendants(true).drop(100)).count()
    }
}

private object BreathFirstSearchPattern : AccessPattern {
    override suspend fun runPattern(rootNode: INode) {
        val queue = ArrayDeque<INode>()
        queue.addLast(rootNode)
        while (queue.isNotEmpty()) {
            queue.addAll(queue.removeFirst().allChildren)
        }
    }
}

private class RandomPattern(val maxAccessOperations: Int, val random: kotlin.random.Random) : AccessPattern {
    override suspend fun runPattern(rootNode: INode) {
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

package org.modelix.model.client2

import org.modelix.model.api.ITree
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test

class GarbageCollectionTest {

    @Test
    fun unused_history_is_garbage_collected() {
        val client = ModelClientV2.builder().build()
        val branch = RepositoryId("repo1").getBranchReference()
        val graph = ModelClientGraph(client, branch.repositoryId)
        var tree: ITree = CLTree.builder(graph).build()
        val idGenerator = IdGenerator.newInstance(0xf)
        var version: CLVersion? = null
        var nameSequence: Long = 1
        val rand = Random(8358)
        val nodeIds = 1000L..2000L
        val memory = ManagementFactory.getMemoryMXBean()
        val maxHeapSize = memory.heapMemoryUsage.max
        var totalCollectedBytes = 0L

        for (nodeId in nodeIds) {
            tree = tree.addNewChild(ITree.ROOT_ID, "children", -1, nodeId, NullConcept)
        }

        var previousMemoryUsage = 0L

        // With the default heap size of 512 MB, the JVM runs out of memory after ~6680 iterations
        repeat(10_000) {
            val currentMemoryUsage = memory.heapMemoryUsage.used
            val delta = currentMemoryUsage - previousMemoryUsage
            if (delta < 0L) {
                totalCollectedBytes += -delta
                println(
                    "Garbage collected. previous: $previousMemoryUsage, " +
                        "current: $currentMemoryUsage, " +
                        "delta: $delta, " +
                        "total collected: $totalCollectedBytes",
                )
            }
            previousMemoryUsage = currentMemoryUsage
            if (totalCollectedBytes > maxHeapSize * 5) {
                // Enough evidence that it works. Keep the test execution time low.
                println("stopped after $it iterations")
                return
            }

            repeat(100) {
                tree = tree.setProperty(nodeIds.random(rand), "name", "Abc" + nameSequence++)
            }

            val newVersion = CLVersion.createRegularVersion(
                id = idGenerator.generate(),
                time = null,
                author = null,
                tree = tree,
                baseVersion = version,
                operations = emptyArray(),
                graph = graph,
            )
            version = CLVersion(
                graph.loadVersion(
                    newVersion.getObjectHash(),
                    mapOf(newVersion.getObjectHash() to newVersion.obj.data.serialize()),
                ),
            )
        }
    }
}

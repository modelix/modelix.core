import org.modelix.datastructures.model.ChildrenChangedEvent
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.ReferenceChangedEvent
import org.modelix.datastructures.objects.FullyLoadedObjectGraph
import org.modelix.model.IVersion
import org.modelix.model.TreeId
import org.modelix.model.VersionMerger
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.runWriteOnTree
import org.modelix.model.mutable.IGenericMutableModelTree
import org.modelix.model.mutable.ModelixIdGenerator
import org.modelix.model.mutable.addNewChild
import org.modelix.model.mutable.getRootNodeId
import org.modelix.model.mutable.removeNode
import org.modelix.model.mutable.treeId
import org.modelix.streams.getBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This simulates the case when two instances of the mps-sync-plugin synchronize the same state of an MPS project
 * concurrently.
 */
class DuplicateImportConflictTest : TreeTestBase() {

    @Test
    fun `add new child`() = duplicateImportTest(
        {},
        {
            it.getWriteTransaction().addNewChild(
                it.getTransaction().getRootNodeId(),
                IChildLinkReference.fromName("role1"),
                -1,
                PNodeReference(it.getTransaction().treeId().id, 2),
                NullConcept.getReference(),
            )
        },
    )

    @Test
    fun `remove node`() = duplicateImportTest(
        {
            val t = it.getWriteTransaction()
            t.addNewChild(
                t.getRootNodeId(),
                IChildLinkReference.fromName("role1"),
                -1,
                PNodeReference(t.treeId().id, 2),
                NullConcept.getReference(),
            )
        },
        {
            val t = it.getWriteTransaction()
            t.removeNode(PNodeReference(t.treeId().id, 2))
        },
    )

    @Test fun `random changes 1`() = runRadomChangesTest(100, 1000, 498935, 92730)

    @Test fun `random changes 2`() = runRadomChangesTest(100, 100, 1002, 3002)

    @Test fun `random changes 3`() = runRadomChangesTest(100, 100, 1003, 3003)

    @Test fun `random changes 4`() = runRadomChangesTest(100, 100, 1004, 3004)

    @Test fun `random changes 5`() = runRadomChangesTest(100, 100, 1005, 3005)

    @Test fun `random changes 6`() = runRadomChangesTest(100, 100, 1006, 3006)

    @Test fun `random changes 7`() = runRadomChangesTest(100, 100, 1007, 3007)

    @Test fun `random changes 8`() = runRadomChangesTest(100, 100, 1008, 3008)

    @Test fun `random changes 9`() = runRadomChangesTest(100, 100, 1009, 3009)

    @Test fun `random changes 10`() = runRadomChangesTest(100, 100, 1010, 3010)

    @Test fun `random changes 11`() = runRadomChangesTest(100, 100, 1011, 3011)

    @Test fun `random changes 12`() = runRadomChangesTest(100, 100, 1012, 3012)

    @Test fun `random changes 13`() = runRadomChangesTest(100, 100, 1013, 3013)

    @Test fun `random changes 14`() = runRadomChangesTest(100, 100, 1014, 3014)

    @Test fun `random changes 15`() = runRadomChangesTest(100, 100, 1015, 3015)

    @Test fun `random changes 16`() = runRadomChangesTest(100, 100, 1016, 3016)

    @Test fun `random changes 17`() = runRadomChangesTest(100, 100, 1017, 3017)

    @Test fun `random changes 18`() = runRadomChangesTest(100, 100, 1018, 3018)

    @Test fun `random changes 19`() = runRadomChangesTest(100, 100, 1019, 3019)

    @Test fun `random changes 20`() = runRadomChangesTest(100, 100, 1020, 3020)

    fun runRadomChangesTest(initialChanges: Int, importChanges: Int, seed1: Int, seed2: Int) = duplicateImportTest(
        { mutableTree ->
            val randomTreeChangeGenerator = RandomTreeChangeGenerator(IdGenerator.newInstance(4), Random(seed1)).growingOperationsOnly()
            repeat(initialChanges) {
                randomTreeChangeGenerator.applyRandomChange(mutableTree, null)
            }
        },
        { mutableTree ->
            val randomTreeChangeGenerator = RandomTreeChangeGenerator(IdGenerator.newInstance(5), Random(seed2))
            repeat(importChanges) {
                randomTreeChangeGenerator.applyRandomChange(mutableTree, null)
            }
        },
    )

    fun duplicateImportTest(
        baseChanges: (IGenericMutableModelTree<INodeReference>) -> Unit,
        importChanges: (IGenericMutableModelTree<INodeReference>) -> Unit,
    ) {
        val merger = VersionMerger()
        val emptyTree = IGenericModelTree.builder()
            .withInt64Ids()
            .treeId(TreeId.fromUUID("69dcb381-3dba-4251-b3ae-7aafe587c28e"))
            .graph(FullyLoadedObjectGraph())
            .build()
        val emptyVersion = IVersion.builder().tree(emptyTree).build()
        val baseVersion = emptyVersion.runWriteOnTree(ModelixIdGenerator(IdGenerator.newInstance(1), emptyTree.getId()), author = "base") {
            baseChanges(it)
        }
        val import1Version = baseVersion.runWriteOnTree(ModelixIdGenerator(IdGenerator.newInstance(3), emptyTree.getId()), author = "importer1") {
            importChanges(it)
        }
        val import2Version = baseVersion.runWriteOnTree(ModelixIdGenerator(IdGenerator.newInstance(3), emptyTree.getId()), author = "importer2") {
            importChanges(it)
        }
        val mergedVersion = merger.mergeChange(import1Version, import2Version)

        assertEquals<IVersion?>(import1Version, mergedVersion.getMergedVersion1())
        assertEquals<IVersion?>(import2Version, mergedVersion.getMergedVersion2())
        assertSameTree(import1Version.getModelTree(), import2Version.getModelTree())
        assertSameTree(import1Version.getModelTree(), mergedVersion.getModelTree())
    }

    private fun <NodeId> assertSameTree(tree1: IGenericModelTree<NodeId>, tree2: IGenericModelTree<NodeId>) {
        val changes = tree2.getChanges(tree1, false).toList().getBlocking(tree2)

        for (changeEvent in changes) {
            when (changeEvent) {
                is ChildrenChangedEvent<NodeId> -> {
                    val children1 = tree1.getChildren(changeEvent.nodeId, changeEvent.role).toList().getBlocking(tree1)
                    val children2 = tree2.getChildren(changeEvent.nodeId, changeEvent.role).toList().getBlocking(tree2)
                    println(changeEvent)
                    println("    children1: $children1")
                    println("    children2: $children2")
                }
                is ReferenceChangedEvent<NodeId> -> {
                    val target1 = tree1.getReferenceTarget(changeEvent.nodeId, changeEvent.role).getBlocking(tree1)
                    val target2 = tree2.getReferenceTarget(changeEvent.nodeId, changeEvent.role).getBlocking(tree2)
                    println(changeEvent)
                    println("    target1: $target1")
                    println("    target2: $target2")
                }
                else -> {
                    println(changeEvent)
                }
            }
        }

        assertEquals(listOf(), changes, "Trees are not the same: " + changes.joinToString("\n"))
    }
}

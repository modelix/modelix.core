import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.datastructures.objects.FullyLoadedObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.asObject
import org.modelix.model.IVersion
import org.modelix.model.TreeId
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.OperationsCompressor
import org.modelix.model.mutable.DummyIdGenerator
import org.modelix.model.mutable.ModelixIdGenerator
import org.modelix.model.mutable.VersionedModelTree
import org.modelix.model.mutable.getRootNodeId
import org.modelix.model.mutable.treeId
import org.modelix.model.operations.AddNewChildSubtreeOp
import org.modelix.model.operations.IOperation
import org.modelix.model.persistent.CPTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [OperationsCompressor] compacts a bulk import that is recorded as [AddNewChildrenOp]s
 * (the operation actually produced by [ModelImporter]/[VersionedModelTree]), not only the singular
 * [AddNewChildOp].
 */
class OperationsCompressorTest {

    private val childrenRole = IChildLinkReference.fromName("children")

    private fun emptyVersion(): IVersion {
        val emptyTree = IGenericModelTree.builder()
            .withInt64Ids()
            .treeId(TreeId.fromUUID("7a3c1e42-9b0d-4c8e-8f21-6d5b4a3c2e10"))
            .graph(FullyLoadedObjectGraph())
            .build()
        return IVersion.builder().tree(emptyTree).build()
    }

    /**
     * Builds an import-like write and returns both the fine-grained operations that were recorded
     * and the resulting tree object.
     */
    private fun recordImport(): Pair<Array<IOperation>, Object<CPTree>> {
        val base = emptyVersion()
        val mutableTree = VersionedModelTree(base, ModelixIdGenerator(IdGenerator.newInstance(1), base.getModelTree().getId()))
        mutableTree.runWrite { t ->
            val treeIdStr = t.treeId().id
            val root = t.getRootNodeId()
            val topA = PNodeReference(treeIdStr, 100)
            val topB = PNodeReference(treeIdStr, 200)

            // A single AddNewChildrenOp that creates two subtree roots at once (the multi-child case
            // that the compressor used to bail out on).
            t.mutate(
                MutationParameters.AddNew(
                    nodeId = root,
                    role = childrenRole,
                    index = 0,
                    newIdAndConcept = listOf(
                        topA to NullConcept.getReference(),
                        topB to NullConcept.getReference(),
                    ),
                ),
            )

            // Fill subtree A with several grandchildren in one more AddNewChildrenOp.
            val grandChildren = (300L..307L).map { PNodeReference(treeIdStr, it) }
            t.mutate(
                MutationParameters.AddNew(
                    nodeId = topA,
                    role = childrenRole,
                    index = 0,
                    newIdAndConcept = grandChildren.map { it to NullConcept.getReference() },
                ),
            )

            // Properties on created nodes (should be absorbed into the subtree ops).
            t.mutate(MutationParameters.Property(topA, IPropertyReference.fromName("name"), "A"))
            t.mutate(MutationParameters.Property(topB, IPropertyReference.fromName("name"), "B"))
            grandChildren.forEachIndexed { i, id ->
                t.mutate(MutationParameters.Property(id, IPropertyReference.fromName("name"), "g$i"))
            }

            // A reference within the imported subtree.
            t.mutate(MutationParameters.Reference(grandChildren[0], IReferenceLinkReference.fromName("ref"), grandChildren[1]))
        }

        val (applied, resultTree) = mutableTree.getPendingChanges()
        val ops = applied.map { it.getOriginalOp() }.toTypedArray()

        @Suppress("UNCHECKED_CAST")
        val resultTreeObject = resultTree.asObject() as Object<CPTree>
        return ops to resultTreeObject
    }

    @Test
    fun `bulk import recorded as AddNewChildrenOp is compressed and round-trips`() {
        val (ops, resultTreeObject) = recordImport()

        // Sanity: this is an import-like sequence that the compressor is supposed to optimize.
        assertTrue(ops.size > 10, "expected more than INLINED_OPS_LIMIT ops but was ${ops.size}")
        assertTrue(ops.none { it is AddNewChildSubtreeOp }, "input should be fine-grained")

        val compressed = OperationsCompressor(resultTreeObject).compressOperations(ops)

        // (a) It is actually compressed.
        assertTrue(
            compressed.any { it is AddNewChildSubtreeOp },
            "expected the compressor to emit AddNewChildSubtreeOp, but got: ${compressed.joinToString("\n")}",
        )
        assertTrue(
            compressed.size < ops.size,
            "expected fewer ops after compression (${compressed.size} vs ${ops.size})",
        )

        // (b) Round-trip: applying the compressed ops reproduces the same tree.
        val roundTripTree = applyToEmpty(compressed)
        assertEquals(
            resultTreeObject.getHash(),
            roundTripTree.asObject().getHash(),
            "decompressing the compressed ops did not reproduce the original tree",
        )
    }

    private fun applyToEmpty(ops: Array<IOperation>): IGenericModelTree<INodeReference> {
        val target = VersionedModelTree(emptyVersion(), DummyIdGenerator())
        target.runWrite {
            for (op in ops) {
                op.apply(target)
            }
        }
        return target.getPendingChanges().second
    }
}

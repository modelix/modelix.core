package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.getDescendants
import org.modelix.datastructures.model.toGlobal
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.persistent.CPTree
import org.modelix.streams.IStream
import org.modelix.streams.getBlocking
import org.modelix.streams.iterateBlocking
import org.modelix.streams.plus

class AddNewChildSubtreeOp(
    val resultTreeHash: ObjectReference<CPTree>,
    val position: PositionInRole,
    val childId: INodeReference,
    val concept: ConceptReference,
) : AbstractOperation() {

    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf(resultTreeHash)

    fun withPosition(newPos: PositionInRole): AddNewChildSubtreeOp {
        return if (newPos == position) this else AddNewChildSubtreeOp(resultTreeHash, newPos, childId, concept)
    }

    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        decompress().iterateBlocking(resultTreeHash.graph) { it.apply(tree) }
        return Applied()
    }

    fun decompress(): IStream.Many<IOperation> {
        val resultTree = getResultTree()
        return resultTree.getDescendants(childId.toGlobal(resultTree.getId()), true).flatMapOrdered { node ->
            val parent = resultTree.getParent(node).getBlocking(resultTree)!!
            val roleInParent = resultTree.getRoleInParent(node).getBlocking(resultTree)!!
            val pos = PositionInRole(
                parent,
                roleInParent,
                resultTree.getChildren(parent, roleInParent).asSequence().indexOf(node),
            )
            decompressNode(resultTree, node, pos, false)
        } +
            resultTree.getDescendants(childId.toGlobal(resultTree.getId()), true).flatMapOrdered { node ->
                val parent = resultTree.getParent(node).getBlocking(resultTree)!!
                val roleInParent = resultTree.getRoleInParent(node).getBlocking(resultTree)!!
                val pos = PositionInRole(
                    parent,
                    roleInParent,
                    resultTree.getChildren(parent, roleInParent).asSequence().indexOf(node),
                )
                decompressNode(resultTree, node, pos, true)
            }
    }

    private fun getResultTree(): IModelTree = resultTreeHash.resolveNow().data.getModelTree()

    private fun decompressNode(tree: IModelTree, node: INodeReference, position: PositionInRole?, references: Boolean): IStream.Many<IOperation> {
        return if (references) {
            tree.getReferenceTargets(node).map { (role, target) ->
                SetReferenceOp(node, role, target)
            }
        } else {
            IStream.of(AddNewChildOp(position!!, node, tree.getConceptReference(node).getBlocking(tree))) +
                tree.getProperties(node).map { (property, value) -> SetPropertyOp(node, property, value) }
        }
    }

    override fun toString(): String {
        return "AddNewChildSubtreeOp ${resultTreeHash.getHash()}, $childId, $position, $concept"
    }

    inner class Applied() : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@AddNewChildSubtreeOp

        override fun invert(): List<IOperation> {
            val resultTree = getResultTree()
            return resultTree
                .getDescendants(childId.toGlobal(resultTree.getId()), true)
                .map { DeleteNodeOp(it) }
                .toList()
                .getBlocking(resultTree.asObject().graph)
                .asReversed()
        }
    }

    override fun captureIntend(tree: IModelTree): IOperationIntend {
        val children = tree.getChildren(position.nodeId.toGlobal(tree.getId()), position.role).toList().getBlocking(tree)
        return Intend(CapturedInsertPosition(position.index, children))
    }

    inner class Intend(val capturedPosition: CapturedInsertPosition) : IOperationIntend {
        override fun restoreIntend(tree: IModelTree): List<IOperation> {
            if (tree.containsNode(position.nodeId.toGlobal(tree.getId())).getBlocking(tree)) {
                val newIndex = capturedPosition.findIndex(tree.getChildren(position.nodeId.toGlobal(tree.getId()), position.role).toList().getBlocking(tree))
                return listOf(withPosition(position.withIndex(newIndex)))
            } else {
                return listOf(withPosition(getDetachedNodesEndPosition(tree)))
            }
        }

        override fun getOriginalOp() = this@AddNewChildSubtreeOp
    }
}

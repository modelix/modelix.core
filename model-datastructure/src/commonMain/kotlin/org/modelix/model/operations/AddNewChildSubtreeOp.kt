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
        decompress() { it.apply(tree) }
        return Applied()
    }

    fun decompress(opsVisitor: (IOperation) -> Unit) {
        val resultTree = getResultTree()
        for (node in resultTree.getDescendants(childId.toGlobal(resultTree.getId()), true).asSequence()) {
            val parent = resultTree.getParent(node).getSynchronous()!!
            val roleInParent = resultTree.getRoleInParent(node).getSynchronous()!!
            val pos = PositionInRole(
                parent,
                roleInParent,
                resultTree.getChildren(parent, roleInParent).asSequence().indexOf(node),
            )
            decompressNode(resultTree, node, pos, false, opsVisitor)
        }
        for (node in resultTree.getDescendants(childId.toGlobal(resultTree.getId()), true).asSequence()) {
            decompressNode(resultTree, node, null, true, opsVisitor)
        }
    }

    private fun getResultTree(): IModelTree = resultTreeHash.resolveNow().data.getModelTree()

    private fun decompressNode(tree: IModelTree, node: INodeReference, position: PositionInRole?, references: Boolean, opsVisitor: (IOperation) -> Unit) {
        if (references) {
            for ((role, target) in tree.getReferenceTargets(node).asSequence()) {
                opsVisitor(SetReferenceOp(node, role, target))
            }
        } else {
            opsVisitor(AddNewChildOp(position!!, node, tree.getConceptReference(node).getSynchronous()))
            for ((property, value) in tree.getProperties(node).asSequence()) {
                opsVisitor(SetPropertyOp(node, property, value))
            }
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
                .getSynchronous()
                .asReversed()
        }
    }

    override fun captureIntend(tree: IModelTree): IOperationIntend {
        val children = tree.getChildren(position.nodeId.toGlobal(tree.getId()), position.role).toList().getBlocking()
        return Intend(CapturedInsertPosition(position.index, children))
    }

    inner class Intend(val capturedPosition: CapturedInsertPosition) : IOperationIntend {
        override fun restoreIntend(tree: IModelTree): List<IOperation> {
            if (tree.containsNode(position.nodeId.toGlobal(tree.getId())).getBlocking()) {
                val newIndex = capturedPosition.findIndex(tree.getChildren(position.nodeId.toGlobal(tree.getId()), position.role).toList().getBlocking())
                return listOf(withPosition(position.withIndex(newIndex)))
            } else {
                return listOf(withPosition(getDetachedNodesEndPosition(tree)))
            }
        }

        override fun getOriginalOp() = this@AddNewChildSubtreeOp
    }
}

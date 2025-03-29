package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.asModelTree
import org.modelix.datastructures.model.getDescendants
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.async.getDescendants
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.SerializationUtil

class AddNewChildSubtreeOp(val resultTreeHash: ObjectReference<CPTree>, val position: PositionInRole, val childId: Long, val concept: IConceptReference?) : AbstractOperation() {

    override fun getObjectReferences(): List<ObjectReference<IObjectData>> = listOf(resultTreeHash)

    fun withPosition(newPos: PositionInRole): AddNewChildSubtreeOp {
        return if (newPos == position) this else AddNewChildSubtreeOp(resultTreeHash, newPos, childId, concept)
    }

    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        decompress() { it.apply(transaction) }
        return Applied()
    }

    fun decompress(opsVisitor: (IOperation) -> Unit) {
        val resultTree = getResultTree()
        for (node in resultTree.getDescendants(childId, true).asSequence()) {
            val parent = resultTree.getParent(node).getSynchronous()!!
            val roleInParent = resultTree.getRoleInParent(node).getSynchronous()!!
            val pos = PositionInRole(
                parent,
                roleInParent,
                resultTree.getChildren(parent, roleInParent).asSequence().indexOf(node),
            )
            decompressNode(resultTree, node, pos, false, opsVisitor)
        }
        for (node in resultTree.getDescendants(childId, true).asSequence()) {
            decompressNode(resultTree, node, null, true, opsVisitor)
        }
    }

    private fun getResultTree(): IModelTree<Long> = resultTreeHash.resolveNow().data.getLegacyModelTree()

    private fun decompressNode(tree: IModelTree<Long>, node: Long, position: PositionInRole?, references: Boolean, opsVisitor: (IOperation) -> Unit) {
        if (references) {
            for ((role, target) in tree.getReferenceTargets(node).asSequence()) {
                opsVisitor(SetReferenceOp(node, role.getIdOrName(), target))
            }
        } else {
            opsVisitor(AddNewChildOp(position!!, node, tree.getConceptReference(node).getSynchronous()))
            for ((property, value) in tree.getProperties(node).asSequence()) {
                opsVisitor(SetPropertyOp(node, property.getIdOrName(), value))
            }
        }
    }

    override fun toString(): String {
        return "AddNewChildSubtreeOp ${resultTreeHash.getHash()}, ${SerializationUtil.longToHex(childId)}, $position, $concept"
    }

    inner class Applied() : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@AddNewChildSubtreeOp

        override fun invert(): List<IOperation> {
            val resultTree = getResultTree()
            return resultTree
                .getDescendants(childId, true)
                .map { DeleteNodeOp(it) }
                .toList()
                .getSynchronous()
                .asReversed()
        }
    }

    override fun captureIntend(tree: ITree): IOperationIntend {
        val children = tree.asModelTree().getChildren(position.nodeId, position.role).asSequence()
        return Intend(
            CapturedInsertPosition(position.index, children.toList().toLongArray()),
        )
    }

    inner class Intend(val capturedPosition: CapturedInsertPosition) : IOperationIntend {
        override fun restoreIntend(tree: ITree): List<IOperation> {
            if (tree.containsNode(position.nodeId)) {
                val newIndex = capturedPosition.findIndex(tree.getChildren(position.nodeId, position.role.getIdOrName()).toList().toLongArray())
                return listOf(withPosition(position.withIndex(newIndex)))
            } else {
                return listOf(withPosition(getDetachedNodesEndPosition(tree)))
            }
        }

        override fun getOriginalOp() = this@AddNewChildSubtreeOp
    }
}

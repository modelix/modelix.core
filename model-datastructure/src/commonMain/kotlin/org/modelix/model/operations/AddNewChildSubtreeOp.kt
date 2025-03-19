package org.modelix.model.operations

import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.async.getDescendants
import org.modelix.model.lazy.CLTree
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.ObjectReference
import org.modelix.model.persistent.CPNode
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
        for (node in resultTree.getDescendants(childId, true)) {
            val pos = PositionInRole(
                node.parentId,
                node.roleInParent,
                resultTree.getChildren(node.parentId, node.roleInParent).indexOf(node.id),
            )
            decompressNode(resultTree, node.getData(), pos, false, opsVisitor)
        }
        for (node in resultTree.getDescendants(childId, true)) {
            decompressNode(resultTree, node.getData(), null, true, opsVisitor)
        }
    }

    private fun getResultTree(): CLTree = CLTree(resultTreeHash.resolveLater().query())

    private fun decompressNode(tree: ITree, node: CPNode, position: PositionInRole?, references: Boolean, opsVisitor: (IOperation) -> Unit) {
        if (references) {
            for (role in node.referenceRoles) {
                opsVisitor(SetReferenceOp(node.id, role, tree.getReferenceTarget(node.id, role)))
            }
        } else {
            opsVisitor(AddNewChildOp(position!!, node.id, tree.getConceptReference(node.id)))
            for (property in node.propertyRoles) {
                opsVisitor(SetPropertyOp(node.id, property, node.getPropertyValue(property)))
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
            val asyncTree = resultTree.asAsyncTree()
            return asyncTree.getStreamExecutor().query {
                asyncTree
                    .getDescendants(childId, true).map { DeleteNodeOp(it) }
                    .toList()
            }.asReversed()
        }
    }

    override fun captureIntend(tree: ITree): IOperationIntend {
        val children = tree.getChildren(position.nodeId, position.role)
        return Intend(
            CapturedInsertPosition(position.index, children.toList().toLongArray()),
        )
    }

    inner class Intend(val capturedPosition: CapturedInsertPosition) : IOperationIntend {
        override fun restoreIntend(tree: ITree): List<IOperation> {
            if (tree.containsNode(position.nodeId)) {
                val newIndex = capturedPosition.findIndex(tree.getChildren(position.nodeId, position.role).toList().toLongArray())
                return listOf(withPosition(position.withIndex(newIndex)))
            } else {
                return listOf(withPosition(getDetachedNodesEndPosition(tree)))
            }
        }

        override fun getOriginalOp() = this@AddNewChildSubtreeOp
    }
}

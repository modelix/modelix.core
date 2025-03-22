package org.modelix.model.operations

import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.persistent.SerializationUtil

class DeleteNodeOp(val childId: Long) : AbstractOperation(), IOperationIntend {

    override fun apply(t: IWriteTransaction): IAppliedOperation {
        if (t.getAllChildren(childId).count() != 0) {
            throw RuntimeException("Attempt to delete non-leaf node: ${childId.toString(16)}")
        }

        val concept = t.getConceptReference(childId)
        val position = getNodePosition(t.tree, childId)
        val properties = t.getPropertyRoles(childId).associateWith { t.getProperty(childId, it) }
        val references = t.getReferenceRoles(childId).associateWith { t.getReferenceTarget(childId, it) }
        t.deleteNode(childId)
        return Applied(position, concept, properties, references)
    }

    override fun toString(): String {
        return "DeleteNodeOp ${SerializationUtil.longToHex(childId)}"
    }

    override fun restoreIntend(tree: ITree): List<IOperation> {
        if (!tree.containsNode(childId)) return listOf(NoOp())
        val allChildren = tree.getAllChildren(childId).toList()
        if (allChildren.isNotEmpty()) {
            val targetPos = getDetachedNodesEndPosition(tree)
            return allChildren
                .reversed()
                .map { MoveNodeOp(it, targetPos) }
                .plus(this)
        }
        return listOf(this)
    }

    override fun captureIntend(tree: ITree): IOperationIntend {
        return this
    }

    override fun getOriginalOp() = this

    inner class Applied(
        val position: PositionInRole,
        val concept: IConceptReference?,
        val properties: Map<String, String?>,
        val references: Map<String, INodeReference?>,
    ) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@DeleteNodeOp

        override fun invert(): List<IOperation> {
            return listOf(AddNewChildOp(position, childId, concept)) +
                properties.map { SetPropertyOp(childId, it.key, it.value) } +
                references.map { SetReferenceOp(childId, it.key, it.value) }
        }

        override fun toString(): String {
            return super.toString() + ", concept: " + concept
        }
    }
}

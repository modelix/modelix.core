package org.modelix.model.operations

import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.persistent.SerializationUtil

class SetReferenceOp(val sourceId: Long, val role: String, val target: INodeReference?) : AbstractOperation(), IOperationIntend {
    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        val oldValue = transaction.getReferenceTarget(sourceId, role)
        transaction.setReferenceTarget(sourceId, role, target)
        return Applied(oldValue)
    }

    override fun toString(): String {
        return "SetReferenceOp ${SerializationUtil.longToHex(sourceId)}.$role = $target"
    }

    override fun restoreIntend(tree: ITree): List<IOperation> {
        return if (tree.containsNode(sourceId)) listOf(this) else listOf(NoOp())
    }

    override fun captureIntend(tree: ITree) = this

    override fun getOriginalOp() = this

    fun withTarget(newTarget: INodeReference?): SetReferenceOp {
        return if (newTarget == target) this else SetReferenceOp(sourceId, role, newTarget)
    }

    inner class Applied(private val oldValue: INodeReference?) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@SetReferenceOp

        override fun invert(): List<IOperation> {
            return listOf(SetReferenceOp(sourceId, role, oldValue))
        }

        override fun toString(): String {
            return super.toString() + ", oldValue: " + oldValue
        }
    }
}

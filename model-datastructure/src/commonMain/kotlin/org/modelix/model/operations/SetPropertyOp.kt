package org.modelix.model.operations

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.persistent.SerializationUtil

class SetPropertyOp(val nodeId: Long, val role: String, val value: String?) : AbstractOperation(), IOperationIntend {
    override fun apply(transaction: IWriteTransaction): IAppliedOperation {
        val oldValue = transaction.getProperty(nodeId, role)
        transaction.setProperty(nodeId, role, value)
        return Applied(oldValue)
    }

    override fun toString(): String {
        return "SetPropertOp ${SerializationUtil.longToHex(nodeId)}.$role = $value"
    }

    override fun restoreIntend(tree: ITree): List<IOperation> {
        return if (tree.containsNode(nodeId)) listOf(this) else listOf(NoOp())
    }

    override fun captureIntend(tree: ITree) = this

    override fun getOriginalOp() = this

    inner class Applied(private val oldValue: String?) : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@SetPropertyOp

        override fun invert(): List<IOperation> {
            return listOf(SetPropertyOp(nodeId, role, oldValue))
        }

        override fun toString(): String {
            return super.toString() + ", oldValue: " + oldValue
        }
    }
}

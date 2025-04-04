package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.toGlobal
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asModel

class SetPropertyOp(val nodeId: INodeReference, val role: IPropertyReference, val value: String?) : AbstractOperation(), IOperationIntend {
    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        val node = tree.asModel().resolveNode(nodeId.toGlobal(tree.getId()))
        val oldValue = node.getPropertyValue(role)
        node.setPropertyValue(role, value)
        return Applied(oldValue)
    }

    override fun toString(): String {
        return "SetPropertOp $nodeId.$role = $value"
    }

    override fun restoreIntend(tree: IModelTree): List<IOperation> {
        return if (tree.containsNode(nodeId.toGlobal(tree.getId())).getBlocking()) listOf(this) else listOf(NoOp())
    }

    override fun captureIntend(tree: IModelTree) = this

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

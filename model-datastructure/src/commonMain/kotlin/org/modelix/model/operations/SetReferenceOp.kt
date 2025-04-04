package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.toGlobal
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asModel

class SetReferenceOp(val sourceId: INodeReference, val role: IReferenceLinkReference, val target: INodeReference?) : AbstractOperation(), IOperationIntend {

    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        val sourceNode = tree.asModel().resolveNode(sourceId.toGlobal(tree.getId()))
        val oldValue = sourceNode.getReferenceTargetRef(role)
        sourceNode.setReferenceTargetRef(role, target)
        return Applied(oldValue)
    }

    override fun toString(): String {
        return "SetReferenceOp $sourceId.$role = $target"
    }

    override fun restoreIntend(tree: IModelTree): List<IOperation> {
        return if (tree.containsNode(sourceId.toGlobal(tree.getId())).getBlocking()) listOf(this) else listOf(NoOp())
    }

    override fun captureIntend(tree: IModelTree) = this

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

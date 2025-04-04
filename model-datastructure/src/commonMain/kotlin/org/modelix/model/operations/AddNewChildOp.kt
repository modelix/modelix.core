package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.datastructures.model.toGlobal
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.streams.getBlocking

class AddNewChildOp(
    position: PositionInRole,
    childId: INodeReference,
    concept: ConceptReference,
) : AddNewChildrenOp(position, listOf(childId to concept)) {
    val childId: INodeReference get() = childIdsAndConcepts[0].first
    val concept: ConceptReference get() = childIdsAndConcepts[0].second

    fun withConcept(newConcept: ConceptReference): AddNewChildOp {
        return if (concept == newConcept) this else AddNewChildOp(position, childId, newConcept)
    }

    override fun withPosition(newPos: PositionInRole): AddNewChildOp {
        return if (newPos == position) this else AddNewChildOp(newPos, childId, concept)
    }

    override fun toString(): String {
        return "AddNewChildOp $childId, $position, $concept"
    }
}

open class AddNewChildrenOp(val position: PositionInRole, val childIdsAndConcepts: List<Pair<INodeReference, ConceptReference>>) : AbstractOperation() {

    open fun withPosition(newPos: PositionInRole): AddNewChildrenOp {
        return if (newPos == position) this else AddNewChildrenOp(newPos, childIdsAndConcepts)
    }

    override fun apply(tree: IMutableModelTree): IAppliedOperation {
        tree.getWriteTransaction().mutate(
            MutationParameters.AddNew(
                nodeId = position.nodeId.toGlobal(tree.getId()),
                role = position.role,
                index = position.index,
                newIdAndConcept = childIdsAndConcepts.map { it.first.toGlobal(tree.getId()) to it.second },
            ),
        )
        return Applied()
    }

    override fun toString(): String {
        return "AddNewChildrenOp ${childIdsAndConcepts.map { it.first }}, $position, ${childIdsAndConcepts.map { it.second }}"
    }

    inner class Applied : AbstractOperation.Applied(), IAppliedOperation {
        override fun getOriginalOp() = this@AddNewChildrenOp

        override fun invert(): List<IOperation> {
            return childIdsAndConcepts.map { DeleteNodeOp(it.first) }
        }
    }

    override fun captureIntend(tree: IModelTree): IOperationIntend {
        val children = tree.getChildren(position.nodeId.toGlobal(tree.getId()), position.role).toList().getBlocking(tree)
        return Intend(CapturedInsertPosition(position.index, children.toList()))
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

        override fun getOriginalOp() = this@AddNewChildrenOp
    }
}

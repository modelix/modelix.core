package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.datastructures.model.getAncestors
import org.modelix.datastructures.model.toGlobal
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.streams.IStream
import org.modelix.streams.contains
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
            val parentExists = tree.containsNode(position.nodeId.toGlobal(tree.getId())).getBlocking(tree)
            val childExists = IStream.many(childIdsAndConcepts).flatMap { tree.containsNode(it.first) }.toList().getBlocking(tree)
            val targetPosition = if (parentExists) {
                val newIndex = if (position.index < 0) {
                    position.index
                } else {
                    capturedPosition.findIndex(
                        tree.getChildren(position.nodeId.toGlobal(tree.getId()), position.role).toList().getBlocking(tree),
                        position.index,
                    )
                }
                position.withIndex(newIndex)
            } else {
                getDetachedNodesEndPosition(tree)
            }
            return if (childExists.none { it }) {
                // default behaviour
                listOf(withPosition(targetPosition))
            } else {
                // handle already existing child IDs
                childIdsAndConcepts.zip(childExists).asReversed().flatMap { (idAndConcept, exists) ->
                    val (childId, childConcept) = idAndConcept
                    val currentContainment = tree.getContainment(childId).getBlocking(tree)

                    // If the containment is correct, ignore the index to avoid unnecessary changes. As part of the
                    // conflict resolution algorithm we are allowed to make any decision. There are no right or wrong
                    // transformations.
                    if (position.index < 0 &&
                        currentContainment != null &&
                        currentContainment.first == targetPosition.nodeId &&
                        currentContainment.second.matches(targetPosition.role)
                    ) {
                        return@flatMap emptyList()
                    }

                    if (exists) {
                        if (tree.getAncestors(targetPosition.nodeId.toGlobal(tree.getId()), false).contains(childId.toGlobal(tree.getId())).getBlocking(tree)) {
                            emptyList()
                        } else {
                            listOf(MoveNodeOp(childId, targetPosition))
                        }
                    } else {
                        listOf(AddNewChildOp(targetPosition, childId, childConcept))
                    }
                }
            }
        }

        override fun getOriginalOp() = this@AddNewChildrenOp
    }
}

package org.modelix.model.sync.bulk

import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode

/**
 * A trivial implementation that expects the source and target nodes to have the same identity,
 * meaning they can both be resolved by the same INodeReference.
 */
class IdentityPreservingNodeAssociation(
    val targetModel: IMutableModel,
    val overrides: Map<INodeReference, INodeReference>,
) : INodeAssociation {

    override fun resolveTarget(sourceNode: IReadableNode): IWritableNode? {
        val sourceReference = sourceNode.getNodeReference()
        val targetReference = overrides[sourceReference] ?: sourceReference
        return targetModel.resolveNode(targetReference)
    }

    override fun associate(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ) {
        val sourceReference = sourceNode.getNodeReference()
        val expectedTargetReference = overrides[sourceReference] ?: sourceReference
        val actualTargetReference = targetNode.getNodeReference()
        require(expectedTargetReference == actualTargetReference) {
            "Cannot associate $sourceReference with $actualTargetReference, expected: $expectedTargetReference"
        }
    }
}

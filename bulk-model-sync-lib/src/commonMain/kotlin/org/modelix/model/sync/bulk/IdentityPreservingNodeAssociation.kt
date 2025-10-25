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
        return resolveTarget({ sourceNode }, { sourceNode.getNodeReference() })
    }

    override fun resolveTarget(
        sourceNode: () -> IReadableNode?,
        sourceNodeRef: () -> INodeReference,
    ): IWritableNode? {
        val sourceReference = sourceNodeRef()
        val targetReference = overrides[sourceReference] ?: sourceReference
        return targetModel.tryResolveNode(targetReference)
    }

    override fun associate(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ) {
        tryAssociate(sourceNode, targetNode)?.let { throw IllegalArgumentException(it) }
    }

    override fun matches(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ): Boolean {
        return tryAssociate(sourceNode, targetNode) == null
    }

    private fun tryAssociate(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ): String? {
        val sourceReference = sourceNode.getNodeReference()
        val expectedTargetReference = overrides[sourceReference] ?: sourceReference
        val actualTargetReference = targetNode.getNodeReference()
        return if (expectedTargetReference == actualTargetReference) {
            null
        } else {
            "Cannot associate $sourceReference with $actualTargetReference, expected: $expectedTargetReference"
        }
    }
}

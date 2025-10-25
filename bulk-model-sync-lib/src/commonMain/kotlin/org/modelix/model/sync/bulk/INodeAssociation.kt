package org.modelix.model.sync.bulk

import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.getOriginalOrCurrentReference

/**
 * A node association is responsible for storing the mapping between a source node and the imported target node.
 * Provides efficient lookup of the mapping from previous synchronization runs.
 */
interface INodeAssociation {
    fun resolveTarget(sourceNode: IReadableNode): IWritableNode?
    fun resolveTarget(sourceNode: () -> IReadableNode?, sourceNodeRef: () -> INodeReference): IWritableNode? =
        sourceNode()?.let { resolveTarget(it) }
    fun associate(sourceNode: IReadableNode, targetNode: IWritableNode)
    fun matches(sourceNode: IReadableNode, targetNode: IWritableNode): Boolean {
        return sourceNode.getOriginalOrCurrentReference() == targetNode.getOriginalOrCurrentReference() ||
            resolveTarget(sourceNode) == targetNode
    }
}

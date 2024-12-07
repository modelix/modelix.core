package org.modelix.model.sync.bulk

import org.modelix.model.api.INode

/**
 * A node association is responsible for storing the mapping between a source node and the imported target node.
 * Provides efficient lookup of the mapping from previous synchronization runs.
 */
interface INodeAssociation {
    fun resolveTarget(sourceNode: INode): INode?
    fun associate(sourceNode: INode, targetNode: INode)
}

package org.modelix.model.api

/**
 * Extension of [ITreeChangeVisitor], which can also handle removed and added nodes.
 */
interface ITreeChangeVisitorEx : ITreeChangeVisitor {
    /**
     * Called when a node has been removed.
     *
     * @param nodeId id of the deleted node
     */
    fun nodeRemoved(nodeId: Long)

    /**
     * Called when a node has been added.
     *
     * @param nodeId id of the added node
     */
    fun nodeAdded(nodeId: Long)
}

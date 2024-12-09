package org.modelix.model.api

/**
 * Wrapper for [INode]
 */
interface INodeWrapper : INode {
    /**
     * Returns the wrapped node.
     *
     * @return node wrapped by this wrapper
     */
    fun getWrappedNode(): INode
}

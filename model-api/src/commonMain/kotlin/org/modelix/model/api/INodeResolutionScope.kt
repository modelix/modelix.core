package org.modelix.model.api

import kotlin.coroutines.CoroutineContext

interface INodeResolutionScope : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    fun resolveNode(ref: INodeReference): INode?

    companion object Key : CoroutineContext.Key<INodeResolutionScope>
}

class CompositeNodeResolutionScope(val scopes: List<INodeResolutionScope>) : INodeResolutionScope {
    override fun resolveNode(ref: INodeReference): INode? {
        return scopes.asSequence().mapNotNull { it.resolveNode(ref) }.firstOrNull()
    }
}

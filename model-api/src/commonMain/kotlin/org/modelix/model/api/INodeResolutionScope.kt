package org.modelix.model.api

import kotlin.coroutines.CoroutineContext

interface INodeResolutionScope : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    fun resolveNode(ref: INodeReference): INode?

    companion object Key : CoroutineContext.Key<INodeResolutionScope>
}

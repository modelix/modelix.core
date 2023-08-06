package org.modelix.model.api

import kotlin.coroutines.CoroutineContext

interface INodeResolutionScope : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = Key

    fun resolveNode(ref: INodeReference): INode?

    companion object Key : CoroutineContext.Key<INodeResolutionScope>

    override fun plus(context: CoroutineContext): CoroutineContext {
        // coroutines are not compiled with -Xjvm-default=all-compatibility
        // to not break existing IAreas implemented in Java, this override is necessary
        return super.plus(context)
    }

    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R {
        // coroutines are not compiled with -Xjvm-default=all-compatibility
        // to not break existing IAreas implemented in Java, this override is necessary
        return super.fold(initial, operation)
    }

    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? {
        // coroutines are not compiled with -Xjvm-default=all-compatibility
        // to not break existing IAreas implemented in Java, this override is necessary
        return super.get(key)
    }

    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext {
        // coroutines are not compiled with -Xjvm-default=all-compatibility
        // to not break existing IAreas implemented in Java, this override is necessary
        return super.minusKey(key)
    }
}

class CompositeNodeResolutionScope(val scopes: List<INodeResolutionScope>) : INodeResolutionScope {
    override fun resolveNode(ref: INodeReference): INode? {
        return scopes.asSequence().mapNotNull { it.resolveNode(ref) }.firstOrNull()
    }
}

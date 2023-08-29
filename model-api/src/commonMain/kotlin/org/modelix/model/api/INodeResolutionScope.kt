package org.modelix.model.api

import org.modelix.kotlin.utils.ContextValue

interface INodeResolutionScope {

    /**
     * Implementers should accept instances of NodeReference (a.k.a. SerializedNodeReference) and deserialize them
     * into supported references if possible. This removes the need to register an INodeReferenceSerializer and also
     * postpones the deserialization until it's actually necessary.
     *
     * For backwards compatability reasons this method should not be called directly, but INodeReference.resolveIn
     * should be used, which tries to deserialize the reference first, in case an implementation of this method doesn't
     * do the deserialization itself yet.
     */
    @Deprecated("Don't call this method directly. Use INodeReference.resolveIn instead.")
    fun resolveNode(ref: INodeReference): INode?

    /**
     * All node references inside the body are resolved against this scope. Compared to runWithAlso, the existing scopes
     * in the current context are not used, meaning they are replaced.
     */
    fun <T> runWithOnly(body: () -> T): T = contextScope.computeWith(this, body)

    /**
     * Does the same as runWithOnly, but with support for suspendable functions.
     */
    suspend fun <T> runWithOnlyInCoroutine(body: suspend () -> T): T = contextScope.runInCoroutine(this, body)

    /**
     * All node references inside the body are resolved against this scope and if that fails against any other scope in
     * the current context.
     */
    fun <T> runWithAlso(body: () -> T): T = runWithAlso(this, body)

    /**
     * Does the same as runWithAlso, but with support for suspendable functions.
     */
    suspend fun <T> runWithAlsoInCoroutine(body: suspend () -> T): T = runWithAlsoInCoroutine(this, body)

    companion object {
        internal val contextScope = ContextValue<INodeResolutionScope>()

        private fun combineScopes(scopeToAdd: INodeResolutionScope): List<INodeResolutionScope> {
            return listOf(scopeToAdd) + (getCurrentScopes() - scopeToAdd)
        }

        fun <T> runWithAlso(scope: INodeResolutionScope, body: () -> T): T {
            val newScopes = combineScopes(scope)
            return when (newScopes.size) {
                0 -> throw RuntimeException("Impossible case")
                1 -> contextScope.computeWith(newScopes.single(), body)
                else -> contextScope.computeWith(CompositeNodeResolutionScope(newScopes), body)
            }
        }

        suspend fun <T> runWithAlsoInCoroutine(scope: INodeResolutionScope, body: suspend () -> T): T {
            val newScopes = combineScopes(scope)
            return when (newScopes.size) {
                0 -> throw RuntimeException("Impossible case")
                1 -> contextScope.runInCoroutine(newScopes.single(), body)
                else -> contextScope.runInCoroutine(CompositeNodeResolutionScope(newScopes), body)
            }
        }

        /**
         * Returns the current scope that INodeReferences should be resolved in.
         */
        fun getCurrentScope(): INodeResolutionScope {
            return contextScope.getValueOrNull() ?: NullNodeResolutionScope
        }

        /**
         * All the scopes that should be used for node reference resolution. The first element of the list should be
         * tried first.
         */
        fun getCurrentScopes(): List<INodeResolutionScope> {
            return when (val current = contextScope.getValueOrNull()) {
                null -> emptyList()
                is CompositeNodeResolutionScope -> current.scopes
                else -> listOf(current)
            }
        }

        /**
         * Like runWithAlso, but doesn't change the resolution order if it already exists in the context.
         */
        fun <T> ensureInContext(scope: INodeResolutionScope, body: () -> T): T {
            val current = getCurrentScopes()
            return if (current.contains(scope)) {
                body()
            } else {
                scope.runWithAlso(body)
            }
        }
    }
}

object NullNodeResolutionScope : INodeResolutionScope {
    override fun resolveNode(ref: INodeReference): INode? {
        throw IllegalStateException("INodeResolutionScope not set")
    }
}

class CompositeNodeResolutionScope(val scopes: List<INodeResolutionScope>) : INodeResolutionScope {
    init {
        require(scopes.all { it !is CompositeNodeResolutionScope }) {
            "Nesting of CompositeNodeResolutionScope not allowed"
        }
    }
    override fun resolveNode(ref: INodeReference): INode? {
        return scopes.asSequence().mapNotNull { it.resolveNode(ref) }.firstOrNull()
    }
}

package org.modelix.model.api

import org.modelix.kotlin.utils.ContextValue

interface INodeResolutionScope {

    @Deprecated("Don't call this method directly. Use INodeReference.resolveIn instead.")
    fun resolveNode(ref: INodeReference): INode?

    fun <T> runWithOnly(body: () -> T): T = contextScope.computeWith(this, body)

    suspend fun <T> runWithOnlyInCoroutine(body: () -> T): T = contextScope.runInCoroutine(this, body)

    fun <T> runWithAlso(body: () -> T): T = runWithAlso(this, body)

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

        fun getCurrentScope(): INodeResolutionScope {
            return contextScope.getValueOrNull() ?: NullNodeResolutionScope
        }

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
                scope.runWithOnly(body)
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

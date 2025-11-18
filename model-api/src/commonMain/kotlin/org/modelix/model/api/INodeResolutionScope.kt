package org.modelix.model.api

@Deprecated("Use IModel.Companion")
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
    fun <T> runWith(body: () -> T): T = IModel.runWith(this.asModel(), body)

    /**
     * Does the same as runWithOnly, but with support for suspendable functions.
     */
    suspend fun <T> runWithInCoroutine(body: suspend () -> T): T = IModel.runWithSuspended(this.asModel(), body)

    /**
     * All node references inside the body are resolved against this scope and if that fails against any other scope in
     * the current context.
     */
    fun <T> runWithAdditionalScope(body: () -> T): T = runWithAdditionalScope(this, body)

    /**
     * Does the same as runWithAlso, but with support for suspendable functions.
     */
    suspend fun <T> runWithAdditionalScopeInCoroutine(body: suspend () -> T): T = runWithAdditionalScopeInCoroutine(this, body)

    fun asModel(): IModel

    companion object {
        @Deprecated("Use IMode.runWith")
        fun <T> runWithAdditionalScope(scope: INodeResolutionScope, body: () -> T): T {
            return IModel.runWith(scope.asModel(), body)
        }

        @Deprecated("Use IMode.runWithSuspended")
        suspend fun <T> runWithAdditionalScopeInCoroutine(scope: INodeResolutionScope, body: suspend () -> T): T {
            return IModel.runWithSuspended(scope.asModel(), body)
        }

        /**
         * Returns the current scope that INodeReferences should be resolved in.
         */
        @Deprecated("Use IModel.CONTEXT_MODEL")
        fun getCurrentScope(): INodeResolutionScope {
            return IModel.CONTEXT_MODEL.getValueOrNull() as INodeResolutionScope? ?: NullNodeResolutionScope
        }

        /**
         * All the scopes that should be used for node reference resolution. The first element of the list should be
         * tried first.
         */
        @Deprecated("Use IModel.CONTEXT_MODEL")
        fun getCurrentScopes(): List<INodeResolutionScope> {
            return IModel.getContextModels().map { it as INodeResolutionScope }
        }

        /**
         * Like runWithAlso, but doesn't change the resolution order if it already exists in the context.
         */
        fun <T> ensureInContext(scope: INodeResolutionScope, body: () -> T): T {
            return IModel.runWith(scope.asModel(), body)
        }
    }
}

object NullNodeResolutionScope : INodeResolutionScope {
    override fun resolveNode(ref: INodeReference): INode? {
        throw IllegalStateException("INodeResolutionScope not set")
    }

    override fun asModel(): IModel {
        throw UnsupportedOperationException("Not a model")
    }
}

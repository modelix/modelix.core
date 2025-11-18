package org.modelix.model.api

import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference

interface IModel : ITransactionManager {
    fun getRootNode(): IReadableNode
    fun getRootNodes(): List<IReadableNode>
    fun tryResolveNode(ref: INodeReference): IReadableNode?
    fun resolveNode(ref: INodeReference): IReadableNode = tryResolveNode(ref) ?: throw NodeNotFoundException(ref, this)

    companion object {
        val CONTEXT_MODEL = ContextValue<IModel>()

        fun getContextModels(): List<IModel> {
            return when (val m = CONTEXT_MODEL.getValueOrNull()) {
                null -> emptyList()
                is CompositeModel -> m.models
                else -> listOf(m)
            }
        }

        fun resolveNode(ref: INodeReference): IReadableNode {
            return CONTEXT_MODEL.getValue().resolveNode(ref)
        }

        fun tryResolveNode(ref: INodeReference): IReadableNode? {
            return CONTEXT_MODEL.getValueOrNull()?.tryResolveNode(ref)
        }

        fun <R> runWith(model: IModel, body: () -> R): R {
            if (getContextModels().contains(model)) return body()
            return CONTEXT_MODEL.computeWith(mergeContext(model), body)
        }

        suspend fun <R> runWithSuspended(model: IModel, body: suspend () -> R): R {
            if (getContextModels().contains(model)) return body()
            return CONTEXT_MODEL.runInCoroutine(mergeContext(model), body)
        }

        private fun mergeContext(model: IModel): IModel {
            val existing = CONTEXT_MODEL.getValueOrNull()
            return if (existing == null) model else CompositeModel(listOf(model as IMutableModel, existing))
        }
    }
}

interface IMutableModel : IModel {
    fun asArea(): IArea = ModelAsArea(this)
    override fun getRootNode(): IWritableNode
    override fun getRootNodes(): List<IWritableNode>
    override fun tryResolveNode(ref: INodeReference): IWritableNode?
    override fun resolveNode(ref: INodeReference): IWritableNode {
        return super.resolveNode(ref) as IWritableNode
    }

    companion object {
        fun resolveNode(ref: INodeReference): IWritableNode {
            return IModel.resolveNode(ref) as IWritableNode
        }

        fun tryResolveNode(ref: INodeReference): IWritableNode? {
            return IModel.tryResolveNode(ref) as IWritableNode?
        }
    }
}

data class AreaAsModel(val area: IArea) : IMutableModel {
    override fun asArea(): IArea = area
    override fun getRootNode(): IWritableNode = area.getRoot().asWritableNode()
    override fun getRootNodes(): List<IWritableNode> = listOf(getRootNode())
    override fun tryResolveNode(ref: INodeReference): IWritableNode? = area.resolveNode(ref)?.asWritableNode()
    override fun <R> executeRead(body: () -> R): R = area.executeRead(body)
    override fun <R> executeWrite(body: () -> R): R = area.executeWrite(body)
    override fun canRead(): Boolean = area.canRead()
    override fun canWrite(): Boolean = area.canWrite()
}

data class ModelAsArea(val model: IMutableModel) : IArea, IAreaReference {
    override fun asModel(): IMutableModel = model

    override fun getRoot(): INode = model.getRootNode().asLegacyNode()

    override fun resolveConcept(ref: IConceptReference): IConcept? = ILanguageRepository.resolveConcept(ref)

    override fun resolveNode(ref: INodeReference): INode? = model.tryResolveNode(ref)?.asLegacyNode()

    override fun resolveOriginalNode(ref: INodeReference): INode? = resolveNode(ref)

    override fun resolveBranch(id: String): IBranch? = null

    override fun collectAreas(): List<IArea> = listOf(this)

    override fun getReference(): IAreaReference = this

    override fun resolveArea(ref: IAreaReference): IArea? = this.takeIf { it == ref }

    override fun <T> executeRead(body: () -> T): T = model.executeRead(body)

    override fun <T> executeWrite(body: () -> T): T = model.executeWrite(body)

    override fun canRead(): Boolean = model.canRead()

    override fun canWrite(): Boolean = model.canWrite()

    override fun addListener(l: IAreaListener) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(l: IAreaListener) {
        throw UnsupportedOperationException()
    }
}

class NodeNotFoundException(nodeRef: INodeReference, model: IModel) : NoSuchElementException("Node not found: $nodeRef")

fun INodeReference.resolveInContext(originModel: IModel): IReadableNode? {
    return originModel.tryResolveNode(this) ?: IModel.tryResolveNode(this)
}

fun INodeReference.resolveInContext(originModel: IMutableModel): IWritableNode? {
    return originModel.tryResolveNode(this) ?: IMutableModel.tryResolveNode(this)
}

fun INodeReference.resolveInContext(): IWritableNode? {
    return IMutableModel.tryResolveNode(this)
}

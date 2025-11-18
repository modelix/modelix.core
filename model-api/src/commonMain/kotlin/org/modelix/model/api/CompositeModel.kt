package org.modelix.model.api

class CompositeModel(models: List<IModel>) : IModel {
    val models: List<IModel> = models.flatMap {
        when (it) {
            is CompositeModel -> it.models
            else -> listOf(it)
        }
    }

    override fun getRootNode(): IWritableNode {
        throw UnsupportedOperationException("Use getRootNodes for CompositeModels")
    }

    override fun getRootNodes(): List<IReadableNode> {
        return models.flatMap { it.getRootNodes() }
    }

    override fun tryResolveNode(ref: INodeReference): IReadableNode? {
        return models.asSequence().mapNotNull { it.tryResolveNode(ref) }.firstOrNull()
    }

    override fun <R> executeRead(body: () -> R): R {
        return executeRead(models, body)
    }

    override fun <R> executeWrite(body: () -> R): R {
        return executeWrite(models, body)
    }

    private fun <R> executeRead(remainingModels: List<IModel>, body: () -> R): R {
        if (remainingModels.isEmpty()) return body()
        return remainingModels.first().executeRead {
            executeRead(remainingModels.subList(1, remainingModels.size), body)
        }
    }

    private fun <R> executeWrite(remainingModels: List<IModel>, body: () -> R): R {
        if (remainingModels.isEmpty()) return body()
        return remainingModels.first().executeWrite {
            executeWrite(remainingModels.subList(1, remainingModels.size), body)
        }
    }

    override fun canRead(): Boolean {
        return models.all { it.canRead() }
    }

    override fun canWrite(): Boolean {
        return models.all { it.canWrite() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CompositeModel

        return models == other.models
    }

    override fun hashCode(): Int {
        return models.hashCode()
    }
}

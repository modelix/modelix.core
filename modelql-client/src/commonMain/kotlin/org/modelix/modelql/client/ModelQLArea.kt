package org.modelix.modelql.client

import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference

data class ModelQLArea(val client: ModelQLClient) : IArea {
    override fun getRoot(): INode {
        return ModelQLRootNode(client)
    }

    @Deprecated("use ILanguageRepository.resolveConcept")
    override fun resolveConcept(ref: IConceptReference): IConcept? {
        TODO("Not yet implemented")
    }

    override fun resolveNode(ref: INodeReference): INode? {
        if (ref is ModelQLRootNodeReference) return ModelQLRootNode(client)
        return ModelQLNodeWithConceptQuery(client, ref.toSerializedRef())
    }

    override fun resolveOriginalNode(ref: INodeReference): INode? {
        return ModelQLNodeWithConceptQuery(client, ref.toSerializedRef())
    }

    override fun resolveBranch(id: String): IBranch? {
        return null
    }

    override fun collectAreas(): List<IArea> {
        return listOf(this)
    }

    override fun getReference(): IAreaReference {
        TODO("Not yet implemented")
    }

    override fun resolveArea(ref: IAreaReference): IArea? {
        TODO("Not yet implemented")
    }

    override fun <T> executeRead(f: () -> T): T {
        return f()
    }

    override fun <T> executeWrite(f: () -> T): T {
        throw UnsupportedOperationException("readonly")
    }

    override fun canRead(): Boolean {
        return true
    }

    override fun canWrite(): Boolean {
        return false
    }

    override fun addListener(l: IAreaListener) {
        TODO("Not yet implemented")
    }

    override fun removeListener(l: IAreaListener) {
        TODO("Not yet implemented")
    }
}

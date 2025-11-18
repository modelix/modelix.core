package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNodeId
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.ISyncTargetNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NewNodeSpec
import org.modelix.model.api.upcast
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.multiplatform.model.MPSNodeReference

abstract class MPSGenericNodeAdapter<E> : IWritableNode, ISyncTargetNode {

    protected abstract fun getElement(): E
    protected abstract fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<E>>>
    protected abstract fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<E>>>
    protected abstract fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<E>>>

    protected fun tryGetPropertyAccessor(role: IPropertyReference): IPropertyAccessor<E>? = getPropertyAccessors().find { it.first.matches(role) }?.second
    protected fun tryGetReferenceAccessor(role: IReferenceLinkReference): IReferenceAccessor<E>? = getReferenceAccessors().find { it.first.matches(role) }?.second
    protected fun tryGetChildAccessor(role: IChildLinkReference): IChildAccessor<E>? = getChildAccessors().find { it.first.matches(role) }?.second

    protected fun getPropertyAccessor(role: IPropertyReference) = requireNotNull(tryGetPropertyAccessor(role)) { "Unknown property [role = $role, node = $this]" }
    protected fun getReferenceAccessor(role: IReferenceLinkReference) = requireNotNull(tryGetReferenceAccessor(role)) { "Unknown reference link [role = $role, node = $this]" }
    protected fun getChildAccessor(role: IChildLinkReference) = requireNotNull(tryGetChildAccessor(role)) { "Unknown child link [role = $role, node = $this]" }

    override fun getModel(): IMutableModel {
        return MPSArea(ModelixMpsApi.getRepository()).asModel()
    }

    override fun getAllChildren(): List<IWritableNode> {
        return getChildAccessors().flatMap { it.second.read(getElement()) }
    }

    override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
        return tryGetChildAccessor(role)?.read(getElement()) ?: emptyList()
    }

    override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        return tryGetReferenceAccessor(role)?.read(getElement())
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
        return tryGetReferenceAccessor(role)?.readRef(getElement())
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
        return getReferenceAccessors().mapNotNull { it.first to (it.second.read(getElement()) ?: return@mapNotNull null) }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
        return getReferenceAccessors().mapNotNull { it.first to (it.second.readRef(getElement()) ?: return@mapNotNull null) }
    }

    override fun changeConcept(newConcept: ConceptReference): IWritableNode {
        throw UnsupportedOperationException("Concept is immutable [node = $this, newConcept = $newConcept]")
    }

    override fun setPropertyValue(property: IPropertyReference, value: String?) {
        getPropertyAccessor(property).write(getElement(), value)
    }

    override fun moveChild(role: IChildLinkReference, index: Int, child: IWritableNode) {
        getChildAccessor(role).move(getElement(), index, child)
    }

    override fun removeChild(child: IWritableNode) {
        getChildAccessor(child.getContainmentLink()).remove(getElement(), child)
    }

    override fun addNewChild(role: IChildLinkReference, index: Int, concept: ConceptReference): IWritableNode {
        return addNewChildren(role, index, listOf(concept)).single()
    }

    override fun addNewChildren(role: IChildLinkReference, index: Int, concepts: List<ConceptReference>): List<IWritableNode> {
        return doSyncNewChildren(role, index, concepts.map { it to null })
    }

    override fun syncNewChildren(role: IChildLinkReference, index: Int, sourceNodes: List<NewNodeSpec>): List<IWritableNode> {
        return doSyncNewChildren(role, index, sourceNodes.map { it.conceptRef to it })
    }

    private fun doSyncNewChildren(role: IChildLinkReference, index: Int, sourceNodes: List<Pair<ConceptReference, NewNodeSpec?>>): List<IWritableNode> {
        val accessor = getChildAccessor(role)
        val repo = ModelixMpsApi.getRepository()
        val resolvedConcepts = sourceNodes.map { it.first }.distinct().associateWith { concept ->
            repo.resolveConcept(concept)
        }

        return sourceNodes.map { sourceNode ->
            accessor.addNew(getElement(), index, SpecWithResolvedConcept(resolvedConcepts[sourceNode.first]!!, sourceNode.second))
        }
    }

    override fun isOrdered(role: IChildLinkReference): Boolean {
        return tryGetChildAccessor(role)?.isOrdered() != false
    }

    override fun setReferenceTarget(role: IReferenceLinkReference, target: IWritableNode?) {
        getReferenceAccessor(role).write(getElement(), target)
    }

    override fun setReferenceTargetRef(role: IReferenceLinkReference, target: INodeReference?) {
        setReferenceTarget(role, target?.let { checkNotNull(getModel().tryResolveNode(it)) { "Target not found: $target" } })
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun getConceptReference(): ConceptReference {
        return getConcept().getReference().upcast()
    }

    override fun getPropertyValue(property: IPropertyReference): String? {
        return tryGetPropertyAccessor(property)?.read(getElement())
    }

    override fun getPropertyLinks(): List<IPropertyReference> {
        return getPropertyAccessors().map { it.first }
    }

    override fun getAllProperties(): List<Pair<IPropertyReference, String>> {
        return getPropertyAccessors().mapNotNull { it.first to (it.second.read(getElement()) ?: return@mapNotNull null) }
    }

    override fun getReferenceLinks(): List<IReferenceLinkReference> {
        return getReferenceAccessors().map { it.first }
    }

    interface IPropertyAccessor<in E> {
        fun read(element: E): String?
        fun write(element: E, value: String?)
    }

    interface IReferenceAccessor<in E> {
        fun read(element: E): IWritableNode?
        fun readRef(element: E): INodeReference?
        fun write(element: E, value: IWritableNode?)
        fun write(element: E, value: INodeReference?)
    }

    interface IChildAccessor<in E> {
        fun read(element: E): List<IWritableNode>
        fun addNew(element: E, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode
        fun move(element: E, index: Int, child: IWritableNode): Unit = throw UnsupportedOperationException("unordered")
        fun remove(element: E, child: IWritableNode)
        fun isOrdered(): Boolean = false
    }

    class SpecWithResolvedConcept(val concept: SConcept, val spec: NewNodeSpec?) {
        fun getNode(): IReadableNode = spec?.node!!
        fun getConceptReference(): ConceptReference = MPSConcept(concept).getReference()
        override fun toString(): String {
            return "SourceNodeAndConcept[$concept, $spec]"
        }
    }
}

fun NewNodeSpec.getPreferredSNodeId(contextModel: SModelReference?): SNodeId? {
    // Either use the original SNodeId that it had before it was synchronized to the model server
    // or if the node was created outside of MPS, generate an ID based on the ID on the model server.
    // The goal is to create a node with the same ID on all clients.
    return preferredOrCurrentRef
        ?.let { MPSNodeReference.tryConvert(it) }
        ?.takeIf { contextModel == null || it.toMPS().modelReference == contextModel }
        ?.toMPS()
        ?.nodeId
        ?: preferredOrCurrentRef?.encodeAsForeignId()
}

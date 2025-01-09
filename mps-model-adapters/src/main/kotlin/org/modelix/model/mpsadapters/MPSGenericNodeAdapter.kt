package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.adapter.MetaAdapterByDeclaration
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.upcast

abstract class MPSGenericNodeAdapter<E> : IWritableNode {

    protected abstract fun getElement(): E
    protected abstract fun getRepository(): SRepository?
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
        return MPSArea(getRepository() ?: MPSModuleRepository.getInstance()).asModel()
    }

    override fun getAllChildren(): List<IWritableNode> {
        return getChildAccessors().flatMap { it.second.read(getElement()) }
    }

    override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
        return tryGetChildAccessor(role)?.read(getElement()) ?: emptyList()
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        return tryGetReferenceAccessor(role)?.read(getElement())
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
        return getReferenceAccessors().mapNotNull { it.first to (it.second.read(getElement()) ?: return@mapNotNull null) }
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
        val accessor = getChildAccessor(role)
        val repo = getRepository() ?: MPSModuleRepository.getInstance()
        val resolvedConcepts = concepts.distinct().associateWith { concept ->
            requireNotNull(
                concept.let {
                    MPSLanguageRepository(repo).resolveConcept(it.getUID())
                        ?: MPSConcept.tryParseUID(it.getUID())
                }?.concept?.let { MetaAdapterByDeclaration.asInstanceConcept(it) },
            ) {
                // A null value for the concept would default to BaseConcept, but then BaseConcept should be used explicitly.
                "MPS concept not found: $concept"
            }.let { MPSConcept(it) }
        }

        return accessor.addNew(getElement(), index, concepts.map { resolvedConcepts[it]!! })
    }

    override fun setReferenceTarget(role: IReferenceLinkReference, target: IWritableNode?) {
        getReferenceAccessor(role).write(getElement(), target)
    }

    override fun setReferenceTargetRef(role: IReferenceLinkReference, target: INodeReference?) {
        setReferenceTarget(role, target?.let { checkNotNull(getModel().resolveNode(it)) { "Target not found: $target" } })
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

    override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
        return getReferenceTarget(role)?.getNodeReference()
    }

    override fun getReferenceLinks(): List<IReferenceLinkReference> {
        return getReferenceAccessors().map { it.first }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
        return getAllReferenceTargets().map { it.first to it.second.getNodeReference() }
    }

    interface IPropertyAccessor<in E> {
        fun read(element: E): String?
        fun write(element: E, value: String?): Unit = throw UnsupportedOperationException()
    }

    interface IReferenceAccessor<in E> {
        fun read(element: E): IWritableNode?
        fun write(element: E, value: IWritableNode?): Unit = throw UnsupportedOperationException()
        fun write(element: E, value: INodeReference?): Unit = throw UnsupportedOperationException()
    }

    interface IChildAccessor<in E> {
        fun read(element: E): List<IWritableNode>
        fun addNew(element: E, index: Int, childConcepts: List<IConcept>): List<IWritableNode> {
            throw UnsupportedOperationException("$this")
        }
        fun move(element: E, index: Int, child: IWritableNode) {
            throw UnsupportedOperationException()
        }
        fun remove(element: E, child: IWritableNode): Unit = throw UnsupportedOperationException()
    }
}

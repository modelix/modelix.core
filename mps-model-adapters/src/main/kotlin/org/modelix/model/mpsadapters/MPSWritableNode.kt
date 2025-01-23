package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.adapter.MetaAdapterByDeclaration
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.incremental.DependencyTracking
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReferenceByName
import org.modelix.model.api.IRoleReferenceByUID
import org.modelix.model.api.ISyncTargetNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NewNodeSpec
import org.modelix.model.api.meta.NullConcept
import org.modelix.mps.api.ModelixMpsApi

data class MPSWritableNode(val node: SNode) : IWritableNode, ISyncTargetNode {
    override fun getModel(): IMutableModel {
        return MPSArea(node.model?.repository ?: MPSModuleRepository.getInstance()).asModel()
    }

    override fun getAllChildren(): List<IWritableNode> {
        DependencyTracking.accessed(MPSAllChildrenDependency(node))
        return node.children.map { MPSWritableNode(it) }
    }

    override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
        val mpsRole = MPSChildLink.tryFromReference(role)
        if (mpsRole != null) {
            DependencyTracking.accessed(MPSChildrenDependency(node, mpsRole.link))
        } else {
            DependencyTracking.accessed(MPSAllChildrenDependency(node))
        }
        return node.children.map { MPSWritableNode(it) }.filter {
            it.getContainmentLink().matches(role)
        }
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        val mpsRole = MPSReferenceLink.tryFromReference(role)
        if (mpsRole != null) {
            DependencyTracking.accessed(MPSReferenceDependency(node, mpsRole.link))
        } else {
            DependencyTracking.accessed(MPSAllReferencesDependency(node))
        }
        return node.references.firstOrNull { MPSReferenceLink(it.link).toReference().matches(role) }
            ?.targetNode?.let { MPSWritableNode(it) }
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
        DependencyTracking.accessed(MPSAllReferencesDependency(node))
        return node.references.mapNotNull {
            MPSReferenceLink(it.link).toReference() to MPSWritableNode(it.targetNode ?: return@mapNotNull null)
        }
    }

    override fun getParent(): IWritableNode? {
        DependencyTracking.accessed((MPSContainmentDependency(node)))
        return node.parent?.let { MPSWritableNode(it) }
    }

    override fun changeConcept(newConcept: ConceptReference): IWritableNode {
        require(newConcept != NullConcept.getReference()) { "Cannot replace node `$node` with a null concept. Explicitly specify a concept (e.g., `BaseConcept`)." }
        val mpsConcept = MPSConcept.tryParseUID(newConcept.uid)
        requireNotNull(mpsConcept) { "Concept UID `${newConcept.uid}` cannot be parsed as MPS concept." }
        val sConcept = MetaAdapterByDeclaration.asInstanceConcept(mpsConcept.concept)

        val maybeModel = node.model
        val maybeParent = node.parent
        val containmentLink = node.containmentLink
        val maybeNextSibling = node.nextSibling
        // The existing node needs to be deleted before the replacing node is created,
        // because `SModel.createNode` will not use the provided ID if it already exists.
        node.delete()

        val newNode = if (maybeModel != null) {
            maybeModel.createNode(sConcept, node.nodeId)
        } else {
            jetbrains.mps.smodel.SNode(sConcept, node.nodeId)
        }

        if (maybeParent != null && containmentLink != null) {
            // When `maybeNextSibling` is `null`, `replacingNode` is inserted as a last child.
            maybeParent.insertChildBefore(containmentLink, newNode, maybeNextSibling)
        } else if (maybeModel != null) {
            maybeModel.addRootNode(newNode)
        }

        node.properties.forEach { newNode.setProperty(it, node.getProperty(it)) }
        node.references.forEach { ModelixMpsApi.setReference(newNode, it.link, it.targetNodeReference) }
        node.children.forEach { child ->
            val link = checkNotNull(child.containmentLink) { "Containment link of child node not found" }
            node.removeChild(child)
            newNode.addChild(link, child)
        }

        return MPSWritableNode(newNode)
    }

    override fun setPropertyValue(property: IPropertyReference, value: String?) {
        node.setProperty(resolve(property), value)
    }

    override fun moveChild(role: IChildLinkReference, index: Int, child: IWritableNode) {
        require(child is MPSWritableNode)
        val sChild = child.node
        val link = resolve(role)
        val maxValidIndex = if (sChild.parent == this.node) {
            val currentIndex = node.getChildren(link).indexOf(sChild)
            if (currentIndex == -1) throw RuntimeException("Inconsistent containment relation between $sChild and ${sChild.parent}")
            if (currentIndex == index) return
            node.getChildren(link).count() - 1
        } else {
            node.getChildren(link).count()
        }

        val children = node.getChildren(link).toList()
        if (index > maxValidIndex) throw IndexOutOfBoundsException("$index > $maxValidIndex")

        sChild.parent?.removeChild(sChild)

        if (index == -1 || index == children.size) {
            node.addChild(link, sChild)
        } else {
            node.insertChildBefore(link, sChild, children[index])
        }
    }

    override fun removeChild(child: IWritableNode) {
        require(child is MPSWritableNode) { "child must be an MPSWritableNode" }
        node.removeChild(child.node)
    }

    override fun addNewChild(role: IChildLinkReference, index: Int, concept: ConceptReference): IWritableNode {
        return addNewChildren(role, index, listOf(concept)).single()
    }

    override fun addNewChildren(role: IChildLinkReference, index: Int, concepts: List<ConceptReference>): List<IWritableNode> {
        return syncNewChildren(role, index, concepts.map { NewNodeSpec(conceptRef = it) })
    }

    override fun syncNewChildren(role: IChildLinkReference, index: Int, specs: List<NewNodeSpec>): List<IWritableNode> {
        val repo = node.model?.repository ?: MPSModuleRepository.getInstance()
        val resolvedConcepts = specs.distinct().associate { spec ->
            spec.conceptRef to repo.resolveConcept(spec.conceptRef)
        }

        val link = resolve(role)

        val children = node.getChildren(link).toList()
        require(index <= children.size) { "index out of bounds: $index > ${children.size}" }
        val anchor = if (index == -1 || index == children.size) null else children[index]
        val model = node.model

        return specs.map { spec ->
            val resolvedConcept = checkNotNull(resolvedConcepts[spec.conceptRef])
            val preferredId = spec.preferredNodeReference?.let { MPSNodeReference.tryConvert(it) }?.ref?.nodeId
            val newChild = if (model == null) {
                if (preferredId == null) {
                    jetbrains.mps.smodel.SNode(resolvedConcept)
                } else {
                    jetbrains.mps.smodel.SNode(resolvedConcept, preferredId)
                }
            } else {
                model.createNode(resolvedConcept, preferredId)
            }

            if (anchor == null) {
                node.addChild(link, newChild)
            } else {
                node.insertChildBefore(link, newChild, anchor)
            }
            MPSWritableNode(newChild)
        }
    }

    override fun setReferenceTarget(role: IReferenceLinkReference, target: IWritableNode?) {
        require(target is MPSWritableNode?) { "`target` has to be an `MPSWritableNode` or `null`." }
        node.setReferenceTarget(resolve(role), target?.node)
    }

    override fun setReferenceTargetRef(
        role: IReferenceLinkReference,
        target: INodeReference?,
    ) {
        setReferenceTarget(role, target?.let { checkNotNull(getModel().resolveNode(it)) { "Target not found: $target" } })
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun getNodeReference(): INodeReference {
        // no dependency tracking because it's immutable
        return MPSNodeReference(node.reference)
    }

    override fun getConcept(): IConcept {
        DependencyTracking.accessed(MPSNodeDependency(node))
        return MPSConcept(node.concept)
    }

    override fun getConceptReference(): ConceptReference {
        DependencyTracking.accessed(MPSNodeDependency(node))
        return MPSConcept(node.concept).getReference()
    }

    override fun getContainmentLink(): IChildLinkReference {
        DependencyTracking.accessed(MPSNodeDependency(node))
        return (node.containmentLink?.let { MPSChildLink(it) } ?: BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).toReference()
    }

    override fun getPropertyValue(property: IPropertyReference): String? {
        DependencyTracking.accessed(MPSProperty.tryFromReference(property)?.let { MPSPropertyDependency(node, it.property) } ?: MPSAllReferencesDependency(node))
        val mpsProperty = node.properties.firstOrNull { MPSProperty(it).toReference().matches(property) } ?: return null
        return node.getProperty(mpsProperty)
    }

    override fun getPropertyLinks(): List<IPropertyReference> {
        DependencyTracking.accessed(MPSAllPropertiesDependency(node))
        return node.properties.map { MPSProperty(it).toReference() }
    }

    override fun getAllProperties(): List<Pair<IPropertyReference, String>> {
        DependencyTracking.accessed(MPSAllPropertiesDependency(node))
        return node.properties.mapNotNull {
            MPSProperty(it).toReference() to (node.getProperty(it) ?: return@mapNotNull null)
        }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
        DependencyTracking.accessed(MPSReferenceLink.tryFromReference(role)?.let { MPSReferenceDependency(node, it.link) } ?: MPSAllReferencesDependency(node))
        return node.references.firstOrNull { MPSReferenceLink(it.link).toReference().matches(role) }
            ?.targetNodeReference?.let { MPSNodeReference(it) }
    }

    override fun getReferenceLinks(): List<IReferenceLinkReference> {
        DependencyTracking.accessed(MPSAllReferencesDependency(node))
        return node.references.map { MPSReferenceLink(it.link).toReference() }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
        DependencyTracking.accessed(MPSAllReferencesDependency(node))
        return node.references.mapNotNull {
            MPSReferenceLink(it.link).toReference() to MPSNodeReference(it.targetNodeReference ?: return@mapNotNull null)
        }
    }

    private fun resolve(property: IPropertyReference): SProperty {
        if (property is IRoleReferenceByUID && property is IRoleReferenceByName) {
            return MPSProperty.fromReference(property).property
        }
        return node.properties.find { MPSProperty(it).toReference().matches(property) }
            ?: node.concept.properties.find { MPSProperty(it).toReference().matches(property) }
            ?: MPSProperty.fromReference(property).property
    }

    private fun resolve(link: IReferenceLinkReference): SReferenceLink {
        if (link is IRoleReferenceByUID && link is IRoleReferenceByName) {
            return MPSReferenceLink.fromReference(link).link
        }
        return node.references.find { MPSReferenceLink(it.link).toReference().matches(link) }?.link
            ?: node.concept.referenceLinks.find { MPSReferenceLink(it).toReference().matches(link) }
            ?: MPSReferenceLink.fromReference(link).link
    }

    private fun resolve(link: IChildLinkReference): SContainmentLink {
        if (link is IRoleReferenceByUID && link is IRoleReferenceByName) {
            return MPSChildLink.fromReference(link).link
        }
        return node.children.find { MPSChildLink(it.containmentLink!!).toReference().matches(link) }?.containmentLink
            ?: node.concept.containmentLinks.find { MPSChildLink(it).toReference().matches(link) }
            ?: MPSChildLink.fromReference(link).link
    }
}

fun SRepository.resolveConcept(concept: ConceptReference): SConcept {
    return requireNotNull(
        concept.let {
            MPSLanguageRepository(this).resolveConcept(it.getUID())
                ?: MPSConcept.tryParseUID(it.getUID())
        }?.concept?.let { MetaAdapterByDeclaration.asInstanceConcept(it) },
    ) {
        // A null value for the concept would default to BaseConcept, but then BaseConcept should be used explicitly.
        "MPS concept not found: $concept"
    }
}

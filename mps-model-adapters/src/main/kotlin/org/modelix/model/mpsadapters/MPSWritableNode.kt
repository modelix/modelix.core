package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.DynamicReference
import jetbrains.mps.smodel.SNodeId
import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.adapter.MetaAdapterByDeclaration
import org.apache.commons.codec.binary.Hex
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.model.SReference
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.incremental.DependencyTracking
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReferenceByName
import org.modelix.model.api.IRoleReferenceByUID
import org.modelix.model.api.ISyncTargetNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NewNodeSpec
import org.modelix.model.api.NodeReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.mps.api.ModelixMpsApi

fun SNode.asReadableNode(): IReadableNode = MPSWritableNode(this)
fun SNode.asWritableNode(): IWritableNode = MPSWritableNode(this)

data class MPSWritableNode(val node: SNode) : IWritableNode, ISyncTargetNode {

    override fun isReadOnly(): Boolean {
        return node.model?.isReadOnly == true
    }

    override fun getModel(): IMutableModel {
        return MPSArea(node.model?.repository ?: ModelixMpsApi.getRepository()).asModel()
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
        return node.parent?.let { MPSWritableNode(it) } ?: node.model?.let { MPSModelAsNode(it) }
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
        val repo = node.model?.repository ?: ModelixMpsApi.getRepository()
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

            // Either use the original SNodeId that it had before it was synchronized to the model server
            // or if the node was created outside of MPS, generate an ID based on the ID on the model server.
            // The goal is to create a node with the same ID on all clients.
            val preferredId = spec.getPreferredSNodeId(node.model?.reference)

            val newChild = if (model == null) {
                if (preferredId == null) {
                    jetbrains.mps.smodel.SNode(resolvedConcept)
                } else {
                    jetbrains.mps.smodel.SNode(resolvedConcept, preferredId)
                }
            } else {
                model.createNode(resolvedConcept, preferredId)
            }

            newChild.copyNameFrom(spec)

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
        setReferenceTarget(role, target?.let { checkNotNull(getModel().tryResolveNode(it)) { "Target not found: $target" } })
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun getNodeReference(): INodeReference {
        // no dependency tracking because it's immutable
        return node.reference.toModelix()
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
//        if (property.matches(NodeData.ID_PROPERTY_REF)) {
//            // No dependency tracking for read only property necessary
//            return node.nodeId.tryDecodeModelixReference()?.serialize()
//        }

        DependencyTracking.accessed(MPSProperty.tryFromReference(property)?.let { MPSPropertyDependency(node, it.property) } ?: MPSAllReferencesDependency(node))
        val mpsProperty = node.properties.firstOrNull { MPSProperty(it).toReference().matches(property) } ?: return null
        return node.getProperty(mpsProperty)
    }

    override fun setPropertyValue(property: IPropertyReference, value: String?) {
//        if (property.matches(NodeData.ID_PROPERTY_REF)) {
//            require(node.nodeId.tryDecodeModelixReference()?.serialize() == value) {
//                "Property is read only: $property"
//            }
//            return
//        }
        node.setProperty(resolve(property), value)
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
        return node.references.filterNot { it is DynamicReference }.firstOrNull { MPSReferenceLink(it.link).toReference().matches(role) }
            ?.let { getTargetRefSafe(it) }?.toModelix()
    }

    private fun getTargetRefSafe(ref: SReference): SNodeReference? {
        return if (ref is DynamicReference) {
            // Dynamic references only store the name and resolve the target on demand by using scopes.
            // They are mostly used in the generator, where reference macros can return a string instead of a node.
            // They are almost always a bad idea and a performance bottleneck.
            // In some cases it seems that there is a way that dynamic references get persisted and then not just appear
            // in the generator, but also during a git import for example. The necessary languages are probably not
            // built in that case and the resolution will very likely fail.
            // The import result then depends on the environment and not just the file itself.
            // For consistency reasons they are completely ignored here.
            null
        } else {
            ref.targetNodeReference
        }
    }

    override fun getReferenceLinks(): List<IReferenceLinkReference> {
        DependencyTracking.accessed(MPSAllReferencesDependency(node))
        return node.references.map { MPSReferenceLink(it.link).toReference() }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
        DependencyTracking.accessed(MPSAllReferencesDependency(node))
        return node.references.mapNotNull {
            MPSReferenceLink(it.link).toReference() to (it.targetNodeReference ?: return@mapNotNull null).toModelix()
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

fun org.jetbrains.mps.openapi.model.SNodeId.tryDecodeModelixReference(): NodeReference? {
    if (this !is SNodeId.Foreign) return null
    if (id.length < 2 || id.substring(0, 2) != "mx") return null
    val hex = id.substring(2)
    return NodeReference(String(Hex.decodeHex(hex)))
}

fun INodeReference.encodeAsForeignId(): SNodeId {
    return SNodeId.Foreign("~mx" + Hex.encodeHexString(serialize().toByteArray()))
}

/**
 * When a reference is set, the name of the target is stored as resolveInfo.
 * If the name isn't yet set, the resolveInfo will be empty.
 * That's why we set the name as early as possible.
 * And because resolveInfo is something MPS specific it is handled here instead of in ModelSynchronizer.
 */
internal fun SNode.copyNameFrom(spec: NewNodeSpec?) {
    if (spec == null) return
    val name = spec.node?.getPropertyValue(MPSProperty(SNodeUtil.property_INamedConcept_name).toReference())
    if (name != null) setProperty(SNodeUtil.property_INamedConcept_name, name)
}

package org.modelix.model.api

import org.modelix.model.api.meta.NullConcept
import org.modelix.model.area.IArea

data class WritableNodeAsLegacyNode(val node: IWritableNode) : INode, IReplaceableNode {
    init {
        require(node !is LegacyNodeAsWritableNode) { "Unnecessary adapter for: $node" }
    }

    override fun asReadableNode(): IReadableNode = node

    override fun asWritableNode(): IWritableNode = node

    override fun getArea(): IArea = node.getModel().asArea()

    override val isValid: Boolean
        get() = node.isValid()
    override val reference: INodeReference
        get() = node.getNodeReference()
    override val concept: IConcept?
        get() = node.getConcept()
    override val roleInParent: String?
        get() = node.getContainmentLink().stringForLegacyApi()
    override val parent: INode?
        get() = node.getParent()?.asLegacyNode()

    override fun getConceptReference(): IConceptReference? {
        return node.getConceptReference()
    }

    override fun getChildren(role: String?): Iterable<INode> {
        return node.getChildren(IChildLinkReference.fromString(role)).map { it.asLegacyNode() }
    }

    override val allChildren: Iterable<INode>
        get() = node.getAllChildren().map { it.asLegacyNode() }

    override fun moveChild(role: String?, index: Int, child: INode) {
        node.moveChild(IChildLinkReference.fromString(role), index, child.asWritableNode())
    }

    override fun addNewChild(
        role: String?,
        index: Int,
        concept: IConcept?,
    ): INode {
        return node.addNewChild(IChildLinkReference.fromString(role), index, concept.getReference()).asLegacyNode()
    }

    override fun removeChild(child: INode) {
        return node.removeChild(child.asWritableNode())
    }

    override fun getReferenceTarget(role: String): INode? {
        return node.getReferenceTarget(IReferenceLinkReference.fromString(role))?.asLegacyNode()
    }

    override fun setReferenceTarget(role: String, target: INode?) {
        node.setReferenceTarget(IReferenceLinkReference.fromString(role), target?.asWritableNode())
    }

    override fun getPropertyValue(role: String): String? {
        return node.getPropertyValue(IPropertyReference.fromString(role))
    }

    override fun setPropertyValue(role: String, value: String?) {
        return node.setPropertyValue(IPropertyReference.fromString(role), value)
    }

    override fun getPropertyRoles(): List<String> {
        return node.getPropertyLinks().map { it.stringForLegacyApi() }
    }

    override fun getReferenceRoles(): List<String> {
        return node.getReferenceLinks().map { it.stringForLegacyApi() }
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        return node.getChildren(link.toReference()).map { it.asLegacyNode() }
    }

    override fun moveChild(
        role: IChildLink,
        index: Int,
        child: INode,
    ) {
        node.moveChild(role.toReference(), index, child.asWritableNode())
    }

    override fun addNewChild(
        role: String?,
        index: Int,
        concept: IConceptReference?,
    ): INode {
        return node.addNewChild(
            IChildLinkReference.fromString(role),
            index,
            concept.upcast(),
        ).asLegacyNode()
    }

    override fun addNewChild(
        role: IChildLink,
        index: Int,
        concept: IConcept?,
    ): INode {
        return node.addNewChild(role.toReference(), index, concept.getReference()).asLegacyNode()
    }

    override fun addNewChild(
        role: IChildLink,
        index: Int,
        concept: IConceptReference?,
    ): INode {
        return node.addNewChild(role.toReference(), index, concept.upcast()).asLegacyNode()
    }

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        return node.getReferenceTarget(link.toReference())?.asLegacyNode()
    }

    override fun setReferenceTarget(role: String, target: INodeReference?) {
        node.setReferenceTargetRef(IReferenceLinkReference.fromString(role), target)
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        node.setReferenceTarget(link.toReference(), target?.asWritableNode())
    }

    override fun setReferenceTarget(
        role: IReferenceLink,
        target: INodeReference?,
    ) {
        node.setReferenceTargetRef(role.toReference(), target)
    }

    override fun getPropertyValue(property: IProperty): String? {
        return node.getPropertyValue(property.toReference())
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        return node.setPropertyValue(property.toReference(), value)
    }

    override fun tryGetConcept(): IConcept? {
        return node.tryGetConcept()
    }

    override fun addNewChildren(
        role: String?,
        index: Int,
        concepts: List<IConceptReference?>,
    ): List<INode> {
        return node.addNewChildren(IChildLinkReference.fromString(role), index, concepts.map { it.upcast() }).map { it.asLegacyNode() }
    }

    override fun addNewChildren(
        link: IChildLink,
        index: Int,
        concepts: List<IConceptReference?>,
    ): List<INode> {
        return node.addNewChildren(link.toReference(), index, concepts.map { it.upcast() }).map { it.asLegacyNode() }
    }

    override fun getReferenceTargetRef(role: String): INodeReference? {
        return node.getReferenceTargetRef(IReferenceLinkReference.fromString(role))
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        return node.getReferenceTargetRef(role.toReference())
    }

    override fun getOriginalReference(): String? {
        return node.getOriginalReference()
    }

    override fun usesRoleIds(): Boolean {
        return node.asLegacyNode().takeIf { it != this }?.usesRoleIds() ?: true
    }

    override fun getContainmentLink(): IChildLink? {
        return node.getContainmentLink().toLegacy()
    }

    override fun removeReference(role: IReferenceLink) {
        node.setReferenceTarget(role.toReference(), null)
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        return node.getReferenceLinks().map { it.toLegacy() }
    }

    override fun getPropertyLinks(): List<IProperty> {
        return node.getPropertyLinks().map { it.toLegacy() }
    }

    override fun getAllProperties(): List<Pair<IProperty, String>> {
        return node.getAllProperties().map { it.first.toLegacy() to it.second }
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLink, INode>> {
        return node.getAllReferenceTargets().map { it.first.toLegacy() to it.second.asLegacyNode() }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLink, INodeReference>> {
        return node.getAllReferenceTargetRefs().map { it.first.toLegacy() to it.second }
    }

    override fun replaceNode(concept: ConceptReference?): INode {
        return node.changeConcept(concept ?: NullConcept.getReference()).asLegacyNode()
    }
}

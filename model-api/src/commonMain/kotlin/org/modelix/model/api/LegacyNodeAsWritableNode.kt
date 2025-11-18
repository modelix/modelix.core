package org.modelix.model.api

import org.modelix.model.api.meta.NullConcept

data class LegacyNodeAsWritableNode(val node: INode) : IWritableNode {

    init {
        require(node !is WritableNodeAsLegacyNode) { "Unnecessary adapter for: $node" }
    }

    override fun asLegacyNode(): INode {
        return node
    }

    override fun getAllChildren(): List<IWritableNode> {
        return node.allChildren.map { it.asWritableNode() }
    }

    override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
        return node.getChildren(role.toLegacy()).map { it.asWritableNode() }
    }

    override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        return node.getReferenceTarget(role.toLegacy())?.asWritableNode()
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
        return node.getAllReferenceTargets().map { it.first.toReference() to it.second.asWritableNode() }
    }

    override fun getParent(): IWritableNode? {
        return node.parent?.asWritableNode()
    }

    override fun changeConcept(newConcept: ConceptReference): IWritableNode {
        return (node as IReplaceableNode).replaceNode(newConcept).asWritableNode()
    }

    override fun setPropertyValue(property: IPropertyReference, value: String?) {
        node.setPropertyValue(property.toLegacy(), value)
    }

    override fun moveChild(
        role: IChildLinkReference,
        index: Int,
        child: IWritableNode,
    ) {
        node.moveChild(role.toLegacy(), index, child.asLegacyNode())
    }

    override fun removeChild(child: IWritableNode) {
        node.removeChild(child.asLegacyNode())
    }

    override fun addNewChild(
        role: IChildLinkReference,
        index: Int,
        concept: ConceptReference,
    ): IWritableNode {
        return node.addNewChild(role.toLegacy(), index, concept).asWritableNode()
    }

    override fun addNewChildren(
        role: IChildLinkReference,
        index: Int,
        concepts: List<ConceptReference>,
    ): List<IWritableNode> {
        return node.addNewChildren(role.toLegacy(), index, concepts).map { it.asWritableNode() }
    }

    override fun setReferenceTarget(
        role: IReferenceLinkReference,
        target: IWritableNode?,
    ) {
        node.setReferenceTarget(role.toLegacy(), target?.asLegacyNode())
    }

    override fun setReferenceTargetRef(
        role: IReferenceLinkReference,
        target: INodeReference?,
    ) {
        node.setReferenceTarget(role.toLegacy(), target)
    }

    override fun getModel(): IMutableModel = node.getArea().asModel()

    override fun isValid(): Boolean = node.isValid

    override fun getNodeReference(): INodeReference = node.reference

    override fun getConcept(): IConcept = node.concept ?: NullConcept

    override fun getConceptReference(): ConceptReference = (node.getConceptReference() ?: NullConcept.getReference()) as ConceptReference

    override fun getContainmentLink(): IChildLinkReference = node.getContainmentLink()?.toReference() ?: NullChildLinkReference

    override fun getPropertyValue(property: IPropertyReference): String? = node.getPropertyValue(property.toLegacy())

    override fun getPropertyLinks(): List<IPropertyReference> = node.getPropertyLinks().map { it.toReference() }

    override fun getAllProperties(): List<Pair<IPropertyReference, String>> = node.getAllProperties().map { it.first.toReference() to it.second }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? = node.getReferenceTargetRef(role.toLegacy())

    override fun getReferenceLinks(): List<IReferenceLinkReference> = node.getReferenceLinks().map { it.toReference() }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> = node.getAllReferenceTargetRefs().map { it.first.toReference() to it.second }
}

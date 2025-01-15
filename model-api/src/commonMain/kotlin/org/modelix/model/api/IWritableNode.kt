package org.modelix.model.api

import org.modelix.model.data.NodeData

interface IReadableNode {
    fun asLegacyNode(): INode

    fun getModel(): IModel
    fun isValid(): Boolean
    fun getNodeReference(): INodeReference

    fun getConcept(): IConcept
    fun tryGetConcept(): IConcept? = getConceptReference().tryResolve()
    fun getConceptReference(): ConceptReference

    fun getParent(): IReadableNode?
    fun getContainmentLink(): IChildLinkReference

    fun getAllChildren(): List<IReadableNode>
    fun getChildren(role: IChildLinkReference): List<IReadableNode>

    fun getPropertyValue(property: IPropertyReference): String?
    fun getPropertyLinks(): List<IPropertyReference>
    fun getAllProperties(): List<Pair<IPropertyReference, String>>

    /**
     * Is allowed to be null, even if getReferenceTargetRef is not null.
     */
    fun getReferenceTarget(role: IReferenceLinkReference): IReadableNode?
    fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference?
    fun getReferenceLinks(): List<IReferenceLinkReference>
    fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IReadableNode>>
    fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>>
}

/**
 * @return the serialized reference of the source node, if this one was created during an import
 */
fun IReadableNode.getOriginalReference(): String? = getPropertyValue(NodeData.ID_PROPERTY_REF)
    ?: getPropertyValue(IPropertyReference.fromIdAndName("#mpsNodeId#", "#mpsNodeId#")) // for backwards compatibility

fun IReadableNode.getOriginalOrCurrentReference(): String = getOriginalReference() ?: getNodeReference().serialize()
fun INode.getOriginalOrCurrentReference(): String = getOriginalReference() ?: reference.serialize()

interface IWritableNode : IReadableNode {
    override fun asLegacyNode(): INode = WritableNodeAsLegacyNode(this)

    override fun getModel(): IMutableModel
    override fun getAllChildren(): List<IWritableNode>
    override fun getChildren(role: IChildLinkReference): List<IWritableNode>
    override fun getReferenceTarget(role: IReferenceLinkReference): IWritableNode?
    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>>
    override fun getParent(): IWritableNode?

    fun changeConcept(newConcept: ConceptReference): IWritableNode

    fun setPropertyValue(property: IPropertyReference, value: String?)

    fun moveChild(role: IChildLinkReference, index: Int, child: IWritableNode)
    fun removeChild(child: IWritableNode)
    fun addNewChild(role: IChildLinkReference, index: Int, concept: ConceptReference): IWritableNode
    fun addNewChildren(role: IChildLinkReference, index: Int, concepts: List<ConceptReference>): List<IWritableNode>

    fun setReferenceTarget(role: IReferenceLinkReference, target: IWritableNode?)
    fun setReferenceTargetRef(role: IReferenceLinkReference, target: INodeReference?)
}

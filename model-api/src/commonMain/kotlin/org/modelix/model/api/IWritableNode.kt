package org.modelix.model.api

import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.data.NodeData

interface IReadableNode {
    fun asLegacyNode(): INode
    fun asAsyncNode(): IAsyncNode = asLegacyNode().asAsyncNode()

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
     * Is allowed to be null, even if getReferenceTargetRef is not null. Target nodes are only resolved if they are part
     * of the same model. For cross model references use [getReferenceTarget].
     */
    fun getLocalReferenceTarget(role: IReferenceLinkReference): IReadableNode?
    fun getReferenceTarget(role: IReferenceLinkReference): IReadableNode? {
        return getLocalReferenceTarget(role) ?: getReferenceTargetRef(role)?.let { IModel.tryResolveNode(it) }
    }
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
    fun isReadOnly(): Boolean = false

    override fun getModel(): IMutableModel
    override fun getAllChildren(): List<IWritableNode>
    override fun getChildren(role: IChildLinkReference): List<IWritableNode>

    override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode?
    override fun getReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        return getLocalReferenceTarget(role) ?: getReferenceTargetRef(role)?.let { IMutableModel.tryResolveNode(it) }
    }
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

interface ISyncTargetNode : IWritableNode {
    fun syncNewChildren(role: IChildLinkReference, index: Int, specs: List<NewNodeSpec>): List<IWritableNode>
    fun isOrdered(role: IChildLinkReference): Boolean = true
}

fun IReadableNode.isOrdered(role: IChildLinkReference): Boolean {
    return when (this) {
        is ISyncTargetNode -> this.isOrdered(role)
        else -> role.tryResolve(this.getConceptReference())?.isOrdered != false
    }
}

fun IWritableNode.syncNewChildren(role: IChildLinkReference, index: Int, sourceNodes: List<NewNodeSpec>): List<IWritableNode> {
    return when (this) {
        is ISyncTargetNode -> syncNewChildren(role, index, sourceNodes)
        else -> addNewChildren(role, index, sourceNodes.map { it.conceptRef })
    }
}

fun IWritableNode.syncNewChild(role: IChildLinkReference, index: Int, sourceNode: NewNodeSpec): IWritableNode {
    return when (this) {
        is ISyncTargetNode -> syncNewChildren(role, index, listOf(sourceNode)).single()
        else -> addNewChild(role, index, sourceNode.conceptRef)
    }
}

data class NewNodeSpec(
    val conceptRef: ConceptReference,
    val node: IReadableNode? = null,
    val preferredNodeReference: INodeReference? = null,
) {
    constructor(node: IReadableNode) : this(node.getConceptReference(), node, node.getOriginalReference()?.let { NodeReference(it) })

    val preferredOrCurrentRef: INodeReference? get() = preferredNodeReference ?: node?.getNodeReference()
}

fun <T : IReadableNode> T.ancestors(includeSelf: Boolean = false): Sequence<T> {
    return generateSequence(if (includeSelf) this else getParent() as T?) { it.getParent() as T? }
}

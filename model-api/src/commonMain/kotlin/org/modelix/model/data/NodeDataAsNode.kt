package org.modelix.model.data

import org.modelix.kotlin.utils.AtomicLong
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.WritableNodeAsLegacyNode
import org.modelix.model.api.getDescendants
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.api.resolve

private val idSequence = AtomicLong(0)

fun NodeData.ensureHasId(): NodeData {
    val newChildren = this.children.map { it.ensureHasId() }
    val childrenChanged = this.children.zip(newChildren).any { it.first !== it.second }
    if (this.id != null && !childrenChanged) return this
    return copy(
        id = this.id ?: ("data:tmp" + idSequence.incrementAndGet()),
        children = if (childrenChanged) newChildren else this.children,
    )
}

class NodeDataAsNode(val data: NodeData, val parent: NodeDataAsNode?) : IWritableNode {
    private val index: Map<String, NodeDataAsNode>? = if (parent != null) {
        null
    } else {
        asLegacyNode().getDescendants(true)
            .map { it.asWritableNode() as NodeDataAsNode }
            .filter { it.data.id != null }
            .associateBy { it.data.id!! }
    }

    private fun getIndex(): Map<String, NodeDataAsNode> = (parent?.getIndex() ?: index)!!

    private fun tryResolveNode(ref: INodeReference): IWritableNode? {
        return getIndex()[ref.serialize()]
    }

    override fun asLegacyNode(): INode {
        return WritableNodeAsLegacyNode(this)
    }

    override fun getModel(): IMutableModel {
        TODO("Not yet implemented")
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun getNodeReference(): INodeReference {
        return NodeReference(checkNotNull(data.id) { "No ID specified" })
    }

    override fun getConcept(): IConcept {
        return data.concept?.let { ConceptReference(it).resolve() } ?: NullConcept
    }

    override fun getConceptReference(): ConceptReference {
        return data.concept?.let { ConceptReference(it) } ?: NullConcept.getReference()
    }

    override fun getParent(): IWritableNode? {
        return parent
    }

    override fun getContainmentLink(): IChildLinkReference {
        return IChildLinkReference.fromString(data.role)
    }

    override fun getAllChildren(): List<IWritableNode> {
        return data.children.map { NodeDataAsNode(it, this) }
    }

    override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
        return getAllChildren().filter { it.getContainmentLink().matches(role) }
    }

    override fun getPropertyValue(property: IPropertyReference): String? {
        return data.properties.entries
            .find { IPropertyReference.fromString(it.key).matches(property) }
            ?.value
    }

    override fun getPropertyLinks(): List<IPropertyReference> {
        return data.properties.keys.map { IPropertyReference.fromString(it) }
    }

    override fun getAllProperties(): List<Pair<IPropertyReference, String>> {
        return data.properties.map { IPropertyReference.fromString(it.key) to it.value }
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        return getReferenceTargetRef(role)?.let { tryResolveNode(it) }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
        return data.references.entries
            .find { IReferenceLinkReference.fromString(it.key).matches(role) }
            ?.value
            ?.let { NodeReference(it) }
    }

    override fun getReferenceLinks(): List<IReferenceLinkReference> {
        return data.references.keys.map { IReferenceLinkReference.fromString(it) }
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
        return getAllReferenceTargetRefs().mapNotNull { it.first to (tryResolveNode(it.second) ?: return@mapNotNull null) }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
        return data.references.map { IReferenceLinkReference.fromString(it.key) to NodeReference(it.value) }
    }

    override fun changeConcept(newConcept: ConceptReference): IWritableNode {
        throw UnsupportedOperationException("Immutable")
    }

    override fun setPropertyValue(property: IPropertyReference, value: String?) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun moveChild(
        role: IChildLinkReference,
        index: Int,
        child: IWritableNode,
    ) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun removeChild(child: IWritableNode) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun addNewChild(
        role: IChildLinkReference,
        index: Int,
        concept: ConceptReference,
    ): IWritableNode {
        throw UnsupportedOperationException("Immutable")
    }

    override fun addNewChildren(
        role: IChildLinkReference,
        index: Int,
        concepts: List<ConceptReference>,
    ): List<IWritableNode> {
        throw UnsupportedOperationException("Immutable")
    }

    override fun setReferenceTarget(
        role: IReferenceLinkReference,
        target: IWritableNode?,
    ) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun setReferenceTargetRef(
        role: IReferenceLinkReference,
        target: INodeReference?,
    ) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NodeDataAsNode) return false
        if (data.id == null) return false
        return other.data.id == data.id && parent == other.parent
    }

    override fun hashCode(): Int {
        return data.id.hashCode() + parent.hashCode()
    }
}

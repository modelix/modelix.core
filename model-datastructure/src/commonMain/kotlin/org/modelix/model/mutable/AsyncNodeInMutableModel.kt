package org.modelix.model.mutable

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.model.getAncestors
import org.modelix.datastructures.model.getDescendants
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.NodeNotFoundException
import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.resolve
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor

class AsyncNodeInMutableModel(
    val tree: IModelTree,
    private val nodeId: INodeReference,
) : IAsyncNode {

    private fun INodeReference.wrap() = AsyncNodeInMutableModel(tree, this)

    override fun asRegularNode(): INode {
        throw NotImplementedError()
    }

    override fun getConcept(): IStream.One<IConcept> {
        return getConceptRef().map { it.resolve() }
    }

    override fun getConceptRef(): IStream.One<ConceptReference> {
        return tree.getConceptReference(nodeId)
    }

    override fun getRoleInParent(): IStream.ZeroOrOne<IChildLinkReference> {
        return tree.getRoleInParent(nodeId)
    }

    override fun getParent(): IStream.ZeroOrOne<IAsyncNode> {
        return tree.getParent(nodeId).wrap()
    }

    override fun getPropertyValue(role: IPropertyReference): IStream.ZeroOrOne<String> {
        return tree.getProperty(nodeId, role)
    }

    override fun getAllPropertyValues(): IStream.Many<Pair<IPropertyReference, String>> {
        return tree.getProperties(nodeId)
    }

    override fun getAllChildren(): IStream.Many<IAsyncNode> {
        return tree.getChildren(nodeId).wrap()
    }

    override fun getChildren(role: IChildLinkReference): IStream.Many<IAsyncNode> {
        return tree.getChildren(nodeId, role).wrap()
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IStream.ZeroOrOne<IAsyncNode> {
        return getReferenceTargetRef(role).wrap()
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference> {
        return tree.getReferenceTarget(nodeId, role)
    }

    override fun getAllReferenceTargetRefs(): IStream.Many<Pair<IReferenceLinkReference, INodeReference>> {
        return tree.getReferenceTargets(nodeId)
    }

    override fun getAllReferenceTargets(): IStream.Many<Pair<IReferenceLinkReference, IAsyncNode>> {
        return getAllReferenceTargetRefs().map { (role, nodeId) -> role to nodeId.wrap() }
    }

    override fun getAncestors(includeSelf: Boolean): IStream.Many<IAsyncNode> {
        return tree.getAncestors(nodeId, includeSelf).wrap()
    }

    override fun getDescendants(includeSelf: Boolean): IStream.Many<IAsyncNode> {
        return tree.getDescendants(nodeId, includeSelf).wrap()
    }

    override fun getStreamExecutor(): IStreamExecutor = tree.getStreamExecutor()

    private fun IStream.Many<INodeReference>.wrap(): IStream.Many<IAsyncNode> = map { it.wrap() }
    private fun IStream.ZeroOrOne<INodeReference>.wrap(): IStream.ZeroOrOne<IAsyncNode> = map { it.wrap() }
}

fun IModelTree.tryResolveNode(ref: INodeReference): IStream.One<IAsyncNode?> {
    return containsNode(ref).map { if (it) AsyncNodeInMutableModel(this, ref) else null }
}

fun IModelTree.resolveNode(ref: INodeReference): IStream.One<IAsyncNode> {
    return tryResolveNode(ref).map { it ?: throw NodeNotFoundException(ref, this.asReadOnlyModel()) }
}

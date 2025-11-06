package org.modelix.model.api.async

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.resolve
import org.modelix.model.api.resolveInCurrentContext
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.flatten

class AsyncNode(
    private val regularNode: INode,
    private val nodeId: Long,
    private val tree: () -> IAsyncTree,
    private val createNodeAdapter: (Long) -> IAsyncNode,
) : IAsyncNode {

    override fun getStreamExecutor(): IStreamExecutor {
        return tree().getStreamExecutor()
    }

    override fun asRegularNode(): INode = regularNode

    private fun Long.asNode(): IAsyncNode = createNodeAdapter(this)

    override fun getParent(): IStream.ZeroOrOne<IAsyncNode> {
        return tree().getParent(nodeId).map { it.asNode() }
    }

    override fun getConcept(): IStream.One<IConcept> {
        return tree().getConceptReference(nodeId).map { it.resolve() }
    }

    override fun getConceptRef(): IStream.One<ConceptReference> {
        return tree().getConceptReference(nodeId)
    }

    override fun getRoleInParent(): IStream.One<IChildLinkReference> {
        return tree().getRole(nodeId)
    }

    override fun getPropertyValue(role: IPropertyReference): IStream.ZeroOrOne<String> {
        return tree().getPropertyValue(nodeId, role)
    }

    override fun getAllPropertyValues(): IStream.Many<Pair<IPropertyReference, String>> {
        return tree().getAllPropertyValues(nodeId)
    }

    override fun getAllChildren(): IStream.Many<IAsyncNode> {
        return tree().getAllChildren(nodeId).map { it.asNode() }
    }

    override fun getChildren(role: IChildLinkReference): IStream.Many<IAsyncNode> {
        return tree().getChildren(nodeId, role).map { it.asNode() }
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IStream.ZeroOrOne<IAsyncNode> {
        return getReferenceTargetRef(role).mapNotNull {
            INodeResolutionScope.runWithAdditionalScope(regularNode.getArea()) {
                it.resolveInCurrentContext()?.asAsyncNode()
            }
        }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference> {
        return tree().getReferenceTarget(nodeId, role)
    }

    override fun getAllReferenceTargetRefs(): IStream.Many<Pair<IReferenceLinkReference, INodeReference>> {
        return tree().getAllReferenceTargetRefs(nodeId)
    }

    override fun getAllReferenceTargets(): IStream.Many<Pair<IReferenceLinkReference, IAsyncNode>> {
        return tree().getAllReferenceTargetRefs(nodeId).mapNotNull {
            it.first to (it.second.resolveInCurrentContext() ?: return@mapNotNull null).asAsyncNode()
        }
    }

    override fun getAncestors(includeSelf: Boolean): IStream.Many<IAsyncNode> {
        return tree().getAncestors(nodeId, includeSelf).map { it.asNode() }
    }

    override fun getDescendants(includeSelf: Boolean): IStream.Many<IAsyncNode> {
        return if (includeSelf) {
            IStream.of(IStream.of(this), getDescendants(false)).flatten()
        } else {
            getAllChildren().flatMapOrdered { it.getDescendants(true) }
        }
    }

    override fun getDescendantsUnordered(includeSelf: Boolean): IStream.Many<IAsyncNode> {
        return if (includeSelf) {
            IStream.of(IStream.of(this), getDescendantsUnordered(false)).flatten()
        } else {
            getAllChildren().flatMapUnordered { it.getDescendantsUnordered(true) }
        }
    }
}

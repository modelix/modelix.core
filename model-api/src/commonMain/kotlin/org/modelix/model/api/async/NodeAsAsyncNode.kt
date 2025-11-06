package org.modelix.model.api.async

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.maybeOf
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.singleOf
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.asProperty
import org.modelix.model.api.getAncestors
import org.modelix.model.api.getDescendants
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.api.toReference
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.SimpleStreamExecutor

open class NodeAsAsyncNode(val node: INode) : IAsyncNode {
    override fun getStreamExecutor(): IStreamExecutor {
        return SimpleStreamExecutor
    }

    private fun <T : Any> T?.asOptionalMono(): Maybe<T> = if (this != null) maybeOf(this) else maybeOfEmpty()
    private fun <T> T.asMono(): Single<T> = singleOf(this)

    override fun asRegularNode(): INode = node

    override fun getConcept(): IStream.One<IConcept> {
        return IStream.of((node.concept ?: NullConcept))
    }

    override fun getConceptRef(): IStream.One<ConceptReference> {
        return IStream.of(((node.getConceptReference() ?: NullConcept.getReference()) as ConceptReference))
    }

    override fun getParent(): IStream.ZeroOrOne<IAsyncNode> {
        return IStream.ofNotNull(node.parent?.asAsyncNode())
    }

    override fun getRoleInParent(): IStream.One<IChildLinkReference> {
        return IStream.of(node.getContainmentLink().toReference())
    }

    override fun getPropertyValue(role: IPropertyReference): IStream.ZeroOrOne<String> {
        return IStream.ofNotNull(node.getPropertyValue(role.asProperty()))
    }

    override fun getAllChildren(): IStream.Many<IAsyncNode> {
        return IStream.many(node.allChildren.map { it.asAsyncNode() })
    }

    override fun getChildren(role: IChildLinkReference): IStream.Many<IAsyncNode> {
        return IStream.many(node.getChildren(role.toLegacy()).map { it.asAsyncNode() })
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IStream.ZeroOrOne<IAsyncNode> {
        return IStream.ofNotNull(node.getReferenceTarget(role.toLegacy())?.asAsyncNode())
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference> {
        return IStream.ofNotNull(node.getReferenceTargetRef(role.toLegacy()))
    }

    override fun getAllReferenceTargetRefs(): IStream.Many<Pair<IReferenceLinkReference, INodeReference>> {
        return IStream.many(node.getAllReferenceTargetRefs().map { it.first.toReference() to it.second })
    }

    override fun getAllPropertyValues(): IStream.Many<Pair<IPropertyReference, String>> {
        return IStream.many(node.getAllProperties().map { it.first.toReference() to it.second })
    }

    override fun getAllReferenceTargets(): IStream.Many<Pair<IReferenceLinkReference, IAsyncNode>> {
        return IStream.many(node.getAllReferenceTargets().map { it.first.toReference() to it.second.asAsyncNode() })
    }

    override fun getAncestors(includeSelf: Boolean): IStream.Many<IAsyncNode> {
        return IStream.many(node.getAncestors(includeSelf).map { it.asAsyncNode() })
    }

    override fun getDescendants(includeSelf: Boolean): IStream.Many<IAsyncNode> {
        return IStream.many(node.getDescendants(includeSelf).map { it.asAsyncNode() })
    }
}

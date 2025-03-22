package org.modelix.model.api.async

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor

interface IAsyncNode {
    fun asRegularNode(): INode
    fun getStreamExecutor(): IStreamExecutor

    fun getConcept(): IStream.One<IConcept>
    fun getConceptRef(): IStream.One<ConceptReference>

    fun getRoleInParent(): IStream.One<IChildLinkReference>
    fun getParent(): IStream.ZeroOrOne<IAsyncNode>

    fun getPropertyValue(role: IPropertyReference): IStream.ZeroOrOne<String>
    fun getAllPropertyValues(): IStream.Many<Pair<IPropertyReference, String>>

    fun getAllChildren(): IStream.Many<IAsyncNode>
    fun getChildren(role: IChildLinkReference): IStream.Many<IAsyncNode>

    fun getReferenceTarget(role: IReferenceLinkReference): IStream.ZeroOrOne<IAsyncNode>
    fun getReferenceTargetRef(role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference>
    fun getAllReferenceTargetRefs(): IStream.Many<Pair<IReferenceLinkReference, INodeReference>>
    fun getAllReferenceTargets(): IStream.Many<Pair<IReferenceLinkReference, IAsyncNode>>

    fun getDescendants(includeSelf: Boolean): IStream.Many<IAsyncNode>
}

interface INodeWithAsyncSupport : INode {
    fun getAsyncNode(): IAsyncNode
}

fun INode.asAsyncNode(): IAsyncNode {
    return when (this) {
        is INodeWithAsyncSupport -> this.getAsyncNode()
        else -> NodeAsAsyncNode(this)
    }
}

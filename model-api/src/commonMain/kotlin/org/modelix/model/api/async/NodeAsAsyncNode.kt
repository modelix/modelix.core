/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.api.async

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.asProperty
import org.modelix.model.api.getDescendants
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.api.toReference
import org.modelix.streams.FlowBasedStreamFactory
import org.modelix.streams.IMonoStream
import org.modelix.streams.IOptionalMonoStream
import org.modelix.streams.IStream
import org.modelix.streams.SequenceAsStream

class NodeAsAsyncNode(val node: INode) : IAsyncNode {

    private fun <T : Any> T?.asOptionalMono(): IOptionalMonoStream<T> = if (this != null) streamFactory.constant(this) else streamFactory.empty()
    private fun <T> T.asMono(): IMonoStream<T> = streamFactory.constant(this)
    private fun <T> Iterable<T>.asStream(): IStream<T> = streamFactory.fromIterable(this)
    private fun <T> Sequence<T>.asStream(): IStream<T> = streamFactory.fromSequence(this)

    private val streamFactory = FlowBasedStreamFactory(null)

    override fun asRegularNode(): INode = node

    override fun asStream(): IMonoStream<IAsyncNode> {
        TODO("Not yet implemented")
    }

    override fun getConcept(): IMonoStream<IConcept> {
        return (node.concept ?: NullConcept).asMono()
    }

    override fun getConceptRef(): IMonoStream<ConceptReference> {
        return ((node.getConceptReference() ?: NullConcept.getReference()) as ConceptReference).asMono()
    }

    override fun getParent(): IOptionalMonoStream<IAsyncNode> {
        return node.parent?.asAsyncNode().asOptionalMono()
    }

    override fun getRoleInParent(): IMonoStream<IChildLinkReference> {
        return node.getContainmentLink().toReference().asMono()
    }

    override fun getPropertyValue(role: IPropertyReference): IOptionalMonoStream<String> {
        return node.getPropertyValue(role.asProperty()).asOptionalMono()
    }

    override fun getAllChildren(): IStream<IAsyncNode> {
        return node.allChildren.map { it.asAsyncNode() }.asStream()
    }

    override fun getChildren(role: IChildLinkReference): IStream<IAsyncNode> {
        return node.getChildren(role.toLegacy()).map { it.asAsyncNode() }.asStream()
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IOptionalMonoStream<IAsyncNode> {
        return node.getReferenceTarget(role.toLegacy())?.asAsyncNode().asOptionalMono()
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IOptionalMonoStream<INodeReference> {
        return node.getReferenceTargetRef(role.toLegacy()).asOptionalMono()
    }

    override fun getAllReferenceTargetRefs(): IStream<Pair<IReferenceLinkReference, INodeReference>> {
        return node.getAllReferenceTargetRefs().map { it.first.toReference() to it.second }.asStream()
    }

    override fun getAllReferenceTargets(): IStream<Pair<IReferenceLinkReference, IAsyncNode>> {
        return node.getAllReferenceTargets().map { it.first.toReference() to it.second.asAsyncNode() }.asStream()
    }

    override fun getDescendants(includeSelf: Boolean): IStream<IAsyncNode> {
        return node.getDescendants(includeSelf).map { it.asAsyncNode() }.asStream()
    }
}

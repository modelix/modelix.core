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

import org.modelix.streams.IMonoStream
import org.modelix.streams.SimpleMonoStream
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.resolve
import org.modelix.model.api.resolveInCurrentContext
import org.modelix.streams.IOptionalMonoStream
import org.modelix.streams.IStream
import org.modelix.streams.IStreamFactory

class AsyncNode(private val regularNode: INode, private val nodeId: Long, private val tree: () -> IAsyncTree, private val createNodeAdapter: (Long) -> IAsyncNode) : IAsyncNode {

    private fun getStreamFactory(): IStreamFactory = tree().asStream().getFactory()

    override fun asRegularNode(): INode = regularNode
    override fun asStream(): IMonoStream<IAsyncNode> {
        return SimpleMonoStream(this, tree().asStream().getFactory())
    }

    private fun Long.asNode(): IAsyncNode = createNodeAdapter(this)

    override fun getParent(): IOptionalMonoStream<IAsyncNode> {
        return tree().getParent(nodeId).map { it.asNode() }
    }

    override fun getConcept(): IMonoStream<IConcept> {
        return tree().getConceptReference(nodeId).map { it.resolve() }
    }

    override fun getConceptRef(): IMonoStream<ConceptReference> {
        return tree().getConceptReference(nodeId)
    }

    override fun getRoleInParent(): IMonoStream<IChildLinkReference> {
        return tree().getRole(nodeId)
    }

    override fun getPropertyValue(role: IPropertyReference): IOptionalMonoStream<String> {
        return tree().getProperty(nodeId, role)
    }

    override fun getAllChildren(): IStream<IAsyncNode> {
        return tree().getAllChildren(nodeId).map { it.asNode() }
    }

    override fun getChildren(role: IChildLinkReference): IStream<IAsyncNode> {
        return tree().getChildren(nodeId, role).map { it.asNode() }
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IOptionalMonoStream<IAsyncNode> {
        return getReferenceTargetRef(role).mapNotNull { it.resolveInCurrentContext()?.asAsyncNode() }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IOptionalMonoStream<INodeReference> {
        return tree().getReferenceTarget(nodeId, role).filterNotNull()
    }

    override fun getAllReferenceTargetRefs(): IStream<Pair<IReferenceLinkReference, INodeReference>> {
        return tree().getAllReferenceTargetRefs(nodeId)
    }

    override fun getAllReferenceTargets(): IStream<Pair<IReferenceLinkReference, IAsyncNode>> {
        return tree().getAllReferenceTargetRefs(nodeId).map {
            it.first to (it.second.resolveInCurrentContext() ?: return@map null).asAsyncNode()
        }.filterNotNull()
    }

    override fun getDescendants(includeSelf: Boolean): IStream<IAsyncNode> {
        return if (includeSelf) {
            getStreamFactory().flatten(listOf(getStreamFactory().constant(this), getDescendants(false)))
        } else {
            getAllChildren().flatMapConcat { it.getDescendants(true) }
        }
    }
}

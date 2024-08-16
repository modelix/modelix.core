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

class AsyncNode(private val regularNode: INode, private val nodeId: Long, private val tree: () -> IAsyncTree, private val createNodeAdapter: (Long) -> IAsyncNode) : IAsyncNode {
    override fun asRegularNode(): INode = regularNode
    override fun asStream(): IMonoStream<IAsyncNode> {
        return SimpleMonoStream(this, tree().asStream().getFactory())
    }

    private fun Long.asNode(): IAsyncNode = createNodeAdapter(this)

    override fun getParent(): IAsyncValue<IAsyncNode> {
        return tree().getParent(nodeId).map { it.asNode() }
    }

    override fun getConcept(): IAsyncValue<IConcept> {
        return tree().getConceptReference(nodeId).map { it.resolve() }
    }

    override fun getConceptRef(): IAsyncValue<ConceptReference> {
        return tree().getConceptReference(nodeId)
    }

    override fun getRoleInParent(): IAsyncValue<IChildLinkReference> {
        return tree().getRole(nodeId)
    }

    override fun getPropertyValue(role: IPropertyReference): IAsyncValue<String?> {
        return tree().getProperty(nodeId, role)
    }

    override fun getAllChildren(): IAsyncSequence<IAsyncNode> {
        return tree().getAllChildren(nodeId).map { it.asNode() }
    }

    override fun getChildren(role: IChildLinkReference): IAsyncSequence<IAsyncNode> {
        return tree().getChildren(nodeId, role).map { it.asNode() }
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IAsyncValue<IAsyncNode?> {
        return getReferenceTargetRef(role).map { it?.resolveInCurrentContext()?.asAsyncNode() }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IAsyncValue<INodeReference?> {
        return tree().getReferenceTarget(nodeId, role)
    }

    override fun getAllReferenceTargetRefs(): IAsyncSequence<Pair<IReferenceLinkReference, INodeReference>> {
        return tree().getAllReferenceTargetRefs(nodeId)
    }

    override fun getAllReferenceTargets(): IAsyncSequence<Pair<IReferenceLinkReference, IAsyncNode>> {
        return tree().getAllReferenceTargetRefs(nodeId).mapNotNull {
            it.first to (it.second.resolveInCurrentContext() ?: return@mapNotNull null).asAsyncNode()
        }
    }

    override fun getDescendants(includeSelf: Boolean): IAsyncSequence<IAsyncNode> {
        return if (includeSelf) {
            getDescendants(false).toList().flatMap { listOf(this) + it }
        } else {
            getAllChildren().thenRequestMany { it.getDescendants(true) }
        }
    }
}

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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.kotlin.utils.IOptionalMonoFlow
import org.modelix.kotlin.utils.filterNotNull
import org.modelix.kotlin.utils.mapValue
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

    private fun Long.asNode(): IAsyncNode = createNodeAdapter(this)

    override fun getParent(): IOptionalMonoFlow<IAsyncNode> {
        return tree().getParent(nodeId).mapValue { it.asNode() }
    }

    override fun getConcept(): IMonoFlow<IConcept> {
        return tree().getConceptReference(nodeId).mapValue { it.resolve() }
    }

    override fun getConceptRef(): IMonoFlow<ConceptReference> {
        return tree().getConceptReference(nodeId)
    }

    override fun getRoleInParent(): IOptionalMonoFlow<IChildLinkReference> {
        return tree().getRole(nodeId)
    }

    override fun getPropertyValue(role: IPropertyReference): IOptionalMonoFlow<String> {
        return tree().getProperty(nodeId, role)
    }

    override fun getAllChildren(): Flow<IAsyncNode> {
        return tree().getAllChildren(nodeId).map { it.asNode() }
    }

    override fun getChildren(role: IChildLinkReference): Flow<IAsyncNode> {
        return tree().getChildren(nodeId, role).map { it.asNode() }
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IOptionalMonoFlow<IAsyncNode> {
        return getReferenceTargetRef(role).mapValue { it.resolveInCurrentContext()?.asAsyncNode() }.filterNotNull()
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IOptionalMonoFlow<INodeReference> {
        return tree().getReferenceTarget(nodeId, role)
    }

    override fun getAllReferenceTargetRefs(): Flow<Pair<IReferenceLinkReference, INodeReference>> {
        return tree().getAllReferenceTargetRefs(nodeId)
    }

    override fun getAllReferenceTargets(): Flow<Pair<IReferenceLinkReference, IAsyncNode>> {
        return tree().getAllReferenceTargetRefs(nodeId).mapNotNull {
            it.first to (it.second.resolveInCurrentContext() ?: return@mapNotNull null).asAsyncNode()
        }
    }
}

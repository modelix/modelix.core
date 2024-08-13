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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.kotlin.utils.IOptionalMonoFlow
import org.modelix.kotlin.utils.monoFlowOf
import org.modelix.kotlin.utils.optionalMonoFlowOf
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.asProperty
import org.modelix.model.api.meta.NullConcept

class NodeAsAsyncNode(val node: INode) : IAsyncNode {
    override fun asRegularNode(): INode = node

    override fun getConcept(): IMonoFlow<IConcept> {
        return monoFlowOf(node.concept ?: NullConcept)
    }

    override fun getConceptRef(): IMonoFlow<ConceptReference> {
        return monoFlowOf((node.getConceptReference() ?: NullConcept.getReference()) as ConceptReference)
    }

    override fun getParent(): IOptionalMonoFlow<IAsyncNode> {
        return optionalMonoFlowOf(node.parent?.asAsyncNode())
    }

    override fun getRoleInParent(): IOptionalMonoFlow<IChildLinkReference> {
        return optionalMonoFlowOf(node.getContainmentLink()?.toReference())
    }

    override fun getPropertyValue(role: IPropertyReference): IOptionalMonoFlow<String> {
        return optionalMonoFlowOf(node.getPropertyValue(role.asProperty()))
    }

    override fun getAllChildren(): Flow<IAsyncNode> {
        return node.allChildren.asFlow().map { it.asAsyncNode() }
    }

    override fun getChildren(role: IChildLinkReference): Flow<IAsyncNode> {
        return node.getChildren(role.toLegacy()).asFlow().map { it.asAsyncNode() }
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IOptionalMonoFlow<IAsyncNode> {
        return optionalMonoFlowOf(node.getReferenceTarget(role.toLegacy())?.asAsyncNode())
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IOptionalMonoFlow<INodeReference> {
        return optionalMonoFlowOf(node.getReferenceTargetRef(role.toLegacy()))
    }

    override fun getAllReferenceTargetRefs(): Flow<Pair<IReferenceLinkReference, INodeReference>> {
        return node.getAllReferenceTargetRefs().asFlow().map { it.first.toReference() to it.second }
    }

    override fun getAllReferenceTargets(): Flow<Pair<IReferenceLinkReference, IAsyncNode>> {
        return node.getAllReferenceTargets().asFlow().map { it.first.toReference() to it.second.asAsyncNode() }
    }
}

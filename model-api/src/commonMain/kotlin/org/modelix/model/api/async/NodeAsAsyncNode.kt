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
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.api.toReference

class NodeAsAsyncNode(val node: INode) : IAsyncNode {
    override fun asRegularNode(): INode = node

    override fun getConcept(): IAsyncValue<IConcept> {
        return (node.concept ?: NullConcept).asAsync()
    }

    override fun getConceptRef(): IAsyncValue<ConceptReference> {
        return ((node.getConceptReference() ?: NullConcept.getReference()) as ConceptReference).asAsync()
    }

    override fun getParent(): IAsyncValue<IAsyncNode?> {
        return node.parent?.asAsyncNode().asAsync()
    }

    override fun getRoleInParent(): IAsyncValue<IChildLinkReference> {
        return node.getContainmentLink().toReference().asAsync()
    }

    override fun getPropertyValue(role: IPropertyReference): IAsyncValue<String?> {
        return node.getPropertyValue(role.asProperty()).asAsync()
    }

    override fun getAllChildren(): IAsyncValue<List<IAsyncNode>> {
        return node.allChildren.map { it.asAsyncNode() }.asAsync()
    }

    override fun getChildren(role: IChildLinkReference): IAsyncValue<List<IAsyncNode>> {
        return node.getChildren(role.toLegacy()).map { it.asAsyncNode() }.asAsync()
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IAsyncValue<IAsyncNode?> {
        return node.getReferenceTarget(role.toLegacy())?.asAsyncNode().asAsync()
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): IAsyncValue<INodeReference?> {
        return node.getReferenceTargetRef(role.toLegacy()).asAsync()
    }

    override fun getAllReferenceTargetRefs(): IAsyncValue<List<Pair<IReferenceLinkReference, INodeReference>>> {
        return node.getAllReferenceTargetRefs().map { it.first.toReference() to it.second }.asAsync()
    }

    override fun getAllReferenceTargets(): IAsyncValue<List<Pair<IReferenceLinkReference, IAsyncNode>>> {
        return node.getAllReferenceTargets().map { it.first.toReference() to it.second.asAsyncNode() }.asAsync()
    }
}
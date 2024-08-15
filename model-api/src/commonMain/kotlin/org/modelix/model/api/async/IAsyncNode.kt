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

interface IAsyncNode {
    fun asRegularNode(): INode
    fun getConcept(): IAsyncValue<IConcept>
    fun getConceptRef(): IAsyncValue<ConceptReference>
    fun getRoleInParent(): IAsyncValue<IChildLinkReference>
    fun getParent(): IAsyncValue<IAsyncNode?>
    fun getPropertyValue(role: IPropertyReference): IAsyncValue<String?>
    fun getAllChildren(): IAsyncSequence<IAsyncNode>
    fun getChildren(role: IChildLinkReference): IAsyncSequence<IAsyncNode>
    fun getReferenceTarget(role: IReferenceLinkReference): IAsyncValue<IAsyncNode?>
    fun getReferenceTargetRef(role: IReferenceLinkReference): IAsyncValue<INodeReference?>
    fun getAllReferenceTargetRefs(): IAsyncSequence<Pair<IReferenceLinkReference, INodeReference>>
    fun getAllReferenceTargets(): IAsyncSequence<Pair<IReferenceLinkReference, IAsyncNode>>

    fun getDescendants(includeSelf: Boolean): IAsyncSequence<IAsyncNode>
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

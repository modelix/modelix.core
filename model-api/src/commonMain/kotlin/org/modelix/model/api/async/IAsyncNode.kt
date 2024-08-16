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
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.streams.IOptionalMonoStream
import org.modelix.streams.IStream

interface IAsyncNode {
    fun asStream(): IMonoStream<IAsyncNode>
    fun asRegularNode(): INode
    fun getConcept(): IMonoStream<IConcept>
    fun getConceptRef(): IMonoStream<ConceptReference>
    fun getRoleInParent(): IMonoStream<IChildLinkReference>
    fun getParent(): IOptionalMonoStream<IAsyncNode>
    fun getPropertyValue(role: IPropertyReference): IOptionalMonoStream<String>
    fun getAllChildren(): IStream<IAsyncNode>
    fun getChildren(role: IChildLinkReference): IStream<IAsyncNode>
    fun getReferenceTarget(role: IReferenceLinkReference): IOptionalMonoStream<IAsyncNode>
    fun getReferenceTargetRef(role: IReferenceLinkReference): IOptionalMonoStream<INodeReference>
    fun getAllReferenceTargetRefs(): IStream<Pair<IReferenceLinkReference, INodeReference>>
    fun getAllReferenceTargets(): IStream<Pair<IReferenceLinkReference, IAsyncNode>>

    fun getDescendants(includeSelf: Boolean): IStream<IAsyncNode>
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

fun INode.asStream(): IMonoStream<IAsyncNode> = asAsyncNode().asStream()

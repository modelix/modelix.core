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
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.kotlin.utils.IOptionalMonoFlow
import org.modelix.kotlin.utils.flatMapConcatConcurrent
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference

interface IAsyncNode {
    fun asRegularNode(): INode
    fun getConcept(): IMonoFlow<IConcept>
    fun getConceptRef(): IMonoFlow<ConceptReference>
    fun getRoleInParent(): IOptionalMonoFlow<IChildLinkReference>
    fun getParent(): IOptionalMonoFlow<IAsyncNode>
    fun getPropertyValue(role: IPropertyReference): IOptionalMonoFlow<String>
    fun getAllChildren(): Flow<IAsyncNode>
    fun getChildren(role: IChildLinkReference): Flow<IAsyncNode>
    fun getReferenceTarget(role: IReferenceLinkReference): IOptionalMonoFlow<IAsyncNode>
    fun getReferenceTargetRef(role: IReferenceLinkReference): IOptionalMonoFlow<INodeReference>
    fun getAllReferenceTargetRefs(): Flow<Pair<IReferenceLinkReference, INodeReference>>
    fun getAllReferenceTargets(): Flow<Pair<IReferenceLinkReference, IAsyncNode>>

    fun getDescendants(includeSelf: Boolean = false): Flow<IAsyncNode> {
        return if (includeSelf) {
            flowOf(flowOf(this), getDescendants()).flattenConcat()
        } else {
            getAllChildren().flatMapConcatConcurrent { it.getDescendants(true) }
        }
    }
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

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

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.kotlin.utils.IOptionalMonoFlow
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference

interface IAsyncTree {
    suspend fun visitChanges(oldVersion: IAsyncTree, visitor: IAsyncTreeChangeVisitor)
    fun containsNode(nodeId: Long): IMonoFlow<Boolean>
    fun getProperty(nodeId: Long, role: IPropertyReference): IOptionalMonoFlow<String>
    fun getChildren(parentId: Long, role: IChildLinkReference): Flow<Long>
    fun getConceptReference(nodeId: Long): IMonoFlow<ConceptReference>
    fun getParent(nodeId: Long): IOptionalMonoFlow<Long>
    fun getRole(nodeId: Long): IMonoFlow<IChildLinkReference>
    fun getAllReferenceTargetRefs(sourceId: Long): Flow<Pair<IReferenceLinkReference, INodeReference>>
    fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): IOptionalMonoFlow<INodeReference>
    fun getReferenceRoles(sourceId: Long): Flow<String>
    fun getPropertyRoles(sourceId: Long): Flow<String>
    fun getChildRoles(sourceId: Long): Flow<String?>
    fun getAllChildren(parentId: Long): Flow<Long>
}

interface IFlowBasedTree {
    suspend fun visitChanges(oldVersion: IAsyncTree, visitor: IAsyncTreeChangeVisitor)
    fun containsNode(nodeId: Long): IMonoFlow<Boolean>
    fun getProperty(nodeId: Long, role: IPropertyReference): IMonoFlow<String?>
    fun getChildren(parentId: Long, role: IChildLinkReference): Flow<Long>
    fun getConceptReference(nodeId: Long): IMonoFlow<ConceptReference>
    fun getParent(nodeId: Long): IOptionalMonoFlow<Long>
    fun getRole(nodeId: Long): IMonoFlow<IChildLinkReference>
    fun getAllReferenceTargetRefs(sourceId: Long): Flow<Pair<IReferenceLinkReference, INodeReference>>
    fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): IMonoFlow<INodeReference?>
    fun getReferenceRoles(sourceId: Long): Flow<String>
    fun getPropertyRoles(sourceId: Long): Flow<String>
    fun getChildRoles(sourceId: Long): Flow<String?>
    fun getAllChildren(parentId: Long): Flow<Long>
}

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

package org.modelix.model.async

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.ITree

interface IAsyncTree {
    fun visitChanges(oldVersion: IAsyncTree, visitor: IAsyncTreeChangeVisitor): IAsyncValue<Unit>
    fun containsNode(nodeId: Long): IAsyncValue<Boolean>
    fun getProperty(nodeId: Long, role: IPropertyReference): IAsyncValue<String?>
    fun getChildren(parentId: Long, role: IChildLinkReference): IAsyncValue<List<Long>>
    fun getConceptReference(nodeId: Long): IAsyncValue<ConceptReference?>
    fun getParent(nodeId: Long): IAsyncValue<Long>
    fun getRole(nodeId: Long): IAsyncValue<String?>
    fun getAllReferenceTargetRefs(sourceId: Long): IAsyncValue<List<Pair<IReferenceLinkReference, INodeReference>>>
    fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): IAsyncValue<INodeReference?>
    fun getReferenceRoles(sourceId: Long): IAsyncValue<List<String>>
    fun getPropertyRoles(sourceId: Long): IAsyncValue<List<String>>
    fun getChildRoles(sourceId: Long): IAsyncValue<List<String?>>
    fun getAllChildren(parentId: Long): IAsyncValue<List<Long>>
}

fun IAsyncTree.getRootNode(): IAsyncNode = AsyncNode(ITree.ROOT_ID, this)

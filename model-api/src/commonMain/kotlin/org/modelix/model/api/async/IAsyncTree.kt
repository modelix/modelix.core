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
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.streams.IOptionalMonoStream
import org.modelix.streams.IStream

interface IAsyncTree {
    fun asStream(): IMonoStream<IAsyncTree>
    fun visitChanges(oldVersion: IAsyncTree, visitor: IAsyncTreeChangeVisitor): IMonoStream<Unit>
    fun containsNode(nodeId: Long): IMonoStream<Boolean>
    fun getProperty(nodeId: Long, role: IPropertyReference): IOptionalMonoStream<String>
    fun getChildren(parentId: Long, role: IChildLinkReference): IStream<Long>
    fun getConceptReference(nodeId: Long): IMonoStream<ConceptReference>
    fun getParent(nodeId: Long): IOptionalMonoStream<Long>
    fun getRole(nodeId: Long): IMonoStream<IChildLinkReference>
    fun getAllReferenceTargetRefs(sourceId: Long): IStream<Pair<IReferenceLinkReference, INodeReference>>
    fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): IMonoStream<INodeReference?>
    fun getReferenceRoles(sourceId: Long): IStream<String>
    fun getPropertyRoles(sourceId: Long): IStream<String>
    fun getChildRoles(sourceId: Long): IStream<String?>
    fun getAllChildren(parentId: Long): IStream<Long>
}

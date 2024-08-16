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

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.single.Single
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference

interface IAsyncTree {
    fun asStream(): Single<IAsyncTree>
    fun visitChanges(oldVersion: IAsyncTree, visitor: IAsyncTreeChangeVisitor): Maybe<Unit>
    fun containsNode(nodeId: Long): Single<Boolean>
    fun getProperty(nodeId: Long, role: IPropertyReference): Maybe<String>
    fun getChildren(parentId: Long, role: IChildLinkReference): Observable<Long>
    fun getConceptReference(nodeId: Long): Single<ConceptReference>
    fun getParent(nodeId: Long): Maybe<Long>
    fun getRole(nodeId: Long): Single<IChildLinkReference>
    fun getAllReferenceTargetRefs(sourceId: Long): Observable<Pair<IReferenceLinkReference, INodeReference>>
    fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): Maybe<INodeReference>
    fun getReferenceRoles(sourceId: Long): Observable<String>
    fun getPropertyRoles(sourceId: Long): Observable<String>
    fun getChildRoles(sourceId: Long): Observable<String?>
    fun getAllChildren(parentId: Long): Observable<Long>
}

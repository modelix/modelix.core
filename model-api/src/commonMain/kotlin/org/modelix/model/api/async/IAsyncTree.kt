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
import com.badoo.reaktive.maybe.flatMapObservable
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.concatWith
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.single.Single
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.ITree

interface IAsyncTree {
    fun asSynchronousTree(): ITree
    fun getChanges(oldVersion: IAsyncTree, changesOnly: Boolean): Observable<TreeChangeEvent>

    fun getConceptReference(nodeId: Long): Single<ConceptReference>

    fun containsNode(nodeId: Long): Single<Boolean>

    fun getParent(nodeId: Long): Maybe<Long>
    fun getRole(nodeId: Long): Single<IChildLinkReference>

    fun getChildren(parentId: Long, role: IChildLinkReference): Observable<Long>
    fun getChildRoles(sourceId: Long): Observable<IChildLinkReference>
    fun getAllChildren(parentId: Long): Observable<Long>

    fun getPropertyValue(nodeId: Long, role: IPropertyReference): Maybe<String>

    fun getPropertyRoles(sourceId: Long): Observable<IPropertyReference>
    fun getAllPropertyValues(sourceId: Long): Observable<Pair<IPropertyReference, String>>

    fun getAllReferenceTargetRefs(sourceId: Long): Observable<Pair<IReferenceLinkReference, INodeReference>>
    fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): Maybe<INodeReference>
    fun getReferenceRoles(sourceId: Long): Observable<IReferenceLinkReference>
}

interface IAsyncMutableTree : IAsyncTree {
    fun deleteNodes(nodeIds: LongArray): Single<IAsyncMutableTree>
    fun moveChild(newParentId: Long, newRole: IChildLinkReference, newIndex: Int, childId: Long): Single<IAsyncMutableTree>
    fun setConcept(nodeId: Long, concept: ConceptReference): Single<IAsyncMutableTree>

    fun setPropertyValue(nodeId: Long, role: IPropertyReference, value: String?): Single<IAsyncMutableTree>

    fun addNewChildren(parentId: Long, role: IChildLinkReference, index: Int, newIds: LongArray, concepts: Array<ConceptReference>): Single<IAsyncMutableTree>

    fun setReferenceTarget(sourceId: Long, role: IReferenceLinkReference, target: INodeReference?): Single<IAsyncMutableTree>
    fun setReferenceTarget(sourceId: Long, role: IReferenceLinkReference, targetId: Long): Single<IAsyncMutableTree>
}

fun IAsyncTree.getAncestors(nodeId: Long, includeSelf: Boolean): Observable<Long> {
    return if (includeSelf) {
        observableOf(nodeId).concatWith(getAncestors(nodeId, false))
    } else {
        getParent(nodeId).flatMapObservable { getAncestors(it, true) }
    }
}

fun IAsyncTree.getDescendants(nodeId: Long, includeSelf: Boolean): Observable<Long> {
    return if (includeSelf) getDescendantsAndSelf(nodeId) else getDescendants(nodeId)
}

fun IAsyncTree.getDescendants(nodeId: Long): Observable<Long> {
    return getAllChildren(nodeId).flatMap { getDescendantsAndSelf(it) }
}

fun IAsyncTree.getDescendantsAndSelf(nodeId: Long): Observable<Long> {
    return observableOf(nodeId).concatWith(getDescendants(nodeId))
}

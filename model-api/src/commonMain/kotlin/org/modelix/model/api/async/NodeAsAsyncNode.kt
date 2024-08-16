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
import com.badoo.reaktive.maybe.maybeOf
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.singleOf
import com.badoo.reaktive.single.toSingle
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.asProperty
import org.modelix.model.api.getDescendants
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.api.toReference

class NodeAsAsyncNode(val node: INode) : IAsyncNode {

    private fun <T : Any> T?.asOptionalMono(): Maybe<T> = if (this != null) maybeOf(this) else maybeOfEmpty()
    private fun <T> T.asMono(): Single<T> = singleOf(this)

    override fun asRegularNode(): INode = node

    override fun getConcept(): Single<IConcept> {
        return (node.concept ?: NullConcept).asMono()
    }

    override fun getConceptRef(): Single<ConceptReference> {
        return ((node.getConceptReference() ?: NullConcept.getReference()) as ConceptReference).asMono()
    }

    override fun getParent(): Maybe<IAsyncNode> {
        return node.parent?.asAsyncNode().asOptionalMono()
    }

    override fun getRoleInParent(): Single<IChildLinkReference> {
        return node.getContainmentLink().toReference().toSingle()
    }

    override fun getPropertyValue(role: IPropertyReference): Maybe<String> {
        return node.getPropertyValue(role.asProperty()).asOptionalMono()
    }

    override fun getAllChildren(): Observable<IAsyncNode> {
        return node.allChildren.map { it.asAsyncNode() }.asObservable()
    }

    override fun getChildren(role: IChildLinkReference): Observable<IAsyncNode> {
        return node.getChildren(role.toLegacy()).map { it.asAsyncNode() }.asObservable()
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): Maybe<IAsyncNode> {
        return node.getReferenceTarget(role.toLegacy())?.asAsyncNode().asOptionalMono()
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): Maybe<INodeReference> {
        return node.getReferenceTargetRef(role.toLegacy()).asOptionalMono()
    }

    override fun getAllReferenceTargetRefs(): Observable<Pair<IReferenceLinkReference, INodeReference>> {
        return node.getAllReferenceTargetRefs().map { it.first.toReference() to it.second }.asObservable()
    }

    override fun getAllReferenceTargets(): Observable<Pair<IReferenceLinkReference, IAsyncNode>> {
        return node.getAllReferenceTargets().map { it.first.toReference() to it.second.asAsyncNode() }.asObservable()
    }

    override fun getDescendants(includeSelf: Boolean): Observable<IAsyncNode> {
        return node.getDescendants(includeSelf).map { it.asAsyncNode() }.asIterable().asObservable()
    }
}

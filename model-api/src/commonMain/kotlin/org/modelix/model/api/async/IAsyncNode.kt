package org.modelix.model.api.async

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.single.Single
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference

interface IAsyncNode {
    fun asRegularNode(): INode

    fun getConcept(): Single<IConcept>
    fun getConceptRef(): Single<ConceptReference>

    fun getRoleInParent(): Single<IChildLinkReference>
    fun getParent(): Maybe<IAsyncNode>

    fun getPropertyValue(role: IPropertyReference): Maybe<String>
    fun getAllPropertyValues(): Observable<Pair<IPropertyReference, String>>

    fun getAllChildren(): Observable<IAsyncNode>
    fun getChildren(role: IChildLinkReference): Observable<IAsyncNode>

    fun getReferenceTarget(role: IReferenceLinkReference): Maybe<IAsyncNode>
    fun getReferenceTargetRef(role: IReferenceLinkReference): Maybe<INodeReference>
    fun getAllReferenceTargetRefs(): Observable<Pair<IReferenceLinkReference, INodeReference>>
    fun getAllReferenceTargets(): Observable<Pair<IReferenceLinkReference, IAsyncNode>>

    fun getDescendants(includeSelf: Boolean): Observable<IAsyncNode>
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

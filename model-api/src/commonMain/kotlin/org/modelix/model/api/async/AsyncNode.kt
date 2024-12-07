package org.modelix.model.api.async

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.maybe.mapNotNull
import com.badoo.reaktive.maybe.notNull
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.notNull
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.map
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.resolve
import org.modelix.model.api.resolveInCurrentContext

class AsyncNode(
    private val regularNode: INode,
    private val nodeId: Long,
    private val tree: () -> IAsyncTree,
    private val createNodeAdapter: (Long) -> IAsyncNode,
) : IAsyncNode {

    override fun asRegularNode(): INode = regularNode

    private fun Long.asNode(): IAsyncNode = createNodeAdapter(this)

    override fun getParent(): Maybe<IAsyncNode> {
        return tree().getParent(nodeId).map { it.asNode() }
    }

    override fun getConcept(): Single<IConcept> {
        return tree().getConceptReference(nodeId).map { it.resolve() }
    }

    override fun getConceptRef(): Single<ConceptReference> {
        return tree().getConceptReference(nodeId)
    }

    override fun getRoleInParent(): Single<IChildLinkReference> {
        return tree().getRole(nodeId)
    }

    override fun getPropertyValue(role: IPropertyReference): Maybe<String> {
        return tree().getPropertyValue(nodeId, role)
    }

    override fun getAllPropertyValues(): Observable<Pair<IPropertyReference, String>> {
        return tree().getAllPropertyValues(nodeId)
    }

    override fun getAllChildren(): Observable<IAsyncNode> {
        return tree().getAllChildren(nodeId).map { it.asNode() }
    }

    override fun getChildren(role: IChildLinkReference): Observable<IAsyncNode> {
        return tree().getChildren(nodeId, role).map { it.asNode() }
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): Maybe<IAsyncNode> {
        return getReferenceTargetRef(role).mapNotNull {
            INodeResolutionScope.runWithAdditionalScope(regularNode.getArea()) {
                it.resolveInCurrentContext()?.asAsyncNode()
            }
        }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): Maybe<INodeReference> {
        return tree().getReferenceTarget(nodeId, role).notNull()
    }

    override fun getAllReferenceTargetRefs(): Observable<Pair<IReferenceLinkReference, INodeReference>> {
        return tree().getAllReferenceTargetRefs(nodeId)
    }

    override fun getAllReferenceTargets(): Observable<Pair<IReferenceLinkReference, IAsyncNode>> {
        return tree().getAllReferenceTargetRefs(nodeId).map {
            it.first to (it.second.resolveInCurrentContext() ?: return@map null).asAsyncNode()
        }.notNull()
    }

    override fun getDescendants(includeSelf: Boolean): Observable<IAsyncNode> {
        return if (includeSelf) {
            observableOf(observableOf(this), getDescendants(false)).flatMap { it }
        } else {
            getAllChildren().flatMap { it.getDescendants(true) }
        }
    }
}

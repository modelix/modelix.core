package org.modelix.modelql.client

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.resolve
import org.modelix.model.api.serialize
import org.modelix.model.area.IArea
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IQuery
import org.modelix.modelql.core.IZip2Output
import org.modelix.modelql.core.filterNotNull
import org.modelix.modelql.core.map
import org.modelix.modelql.core.orNull
import org.modelix.modelql.core.toList
import org.modelix.modelql.core.zip
import org.modelix.modelql.untyped.ISupportsModelQL
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.asMono
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.conceptReference
import org.modelix.modelql.untyped.nodeReference
import org.modelix.modelql.untyped.parent
import org.modelix.modelql.untyped.property
import org.modelix.modelql.untyped.reference
import org.modelix.modelql.untyped.resolve
import org.modelix.modelql.untyped.roleInParent

abstract class ModelQLNode(val client: ModelQLClient) : INode, ISupportsModelQL {

    override fun <R> buildQuery(body: (IMonoStep<INode>) -> IMonoStep<R>): IQuery<R> {
        return client.buildQuery { root ->
            body(replaceQueryRoot(root))
        }
    }

    fun <R> blockingQuery(body: (IMonoStep<INode>) -> IMonoStep<R>): R {
        val client = client
        return client.blockingQuery { root ->
            body(replaceQueryRoot(root))
        }
    }

    protected open fun replaceQueryRoot(root: IMonoStep<INode>): IMonoStep<INode> {
        return root
    }

    override fun getArea(): IArea {
        return ModelQLArea(client)
    }

    override val isValid: Boolean
        get() = true

    override val roleInParent: String?
        get() = blockingQuery { it.roleInParent() }

    override val parent: INode?
        get() = blockingQuery { it.parent().nodeRefAndConcept().orNull() }?.toNode()

    override fun getChildren(role: String?): Iterable<INode> {
        return blockingQuery { it.children(role).nodeRefAndConcept().toList() }.map { it.toNode() }
    }

    override val allChildren: Iterable<INode>
        get() = blockingQuery { it.allChildren().nodeRefAndConcept().toList() }.map { it.toNode() }

    override fun moveChild(role: String?, index: Int, child: INode) {
        throw UnsupportedOperationException("readonly")
    }

    override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
        throw UnsupportedOperationException("readonly")
    }

    override fun removeChild(child: INode) {
        throw UnsupportedOperationException("readonly")
    }

    override fun getReferenceTarget(role: String): INode? {
        return blockingQuery { it.reference(role).filterNotNull().nodeRefAndConcept().orNull() }?.toNode()
    }

    override fun getReferenceTargetRef(role: String): INodeReference? {
        return blockingQuery { it.reference(role).nodeReference().orNull() }
    }

    override fun setReferenceTarget(role: String, target: INode?) {
        throw UnsupportedOperationException("readonly")
    }

    override fun getPropertyValue(role: String): String? {
        return blockingQuery { it.property(role) }
    }

    override fun setPropertyValue(role: String, value: String?) {
        throw UnsupportedOperationException("readonly")
    }

    override fun getPropertyRoles(): List<String> {
        TODO("Not yet implemented")
    }

    override fun getReferenceRoles(): List<String> {
        TODO("Not yet implemented")
    }

    private fun IZip2Output<*, INodeReference, ConceptReference?>.toNode(): INode {
        return ModelQLNodeWithKnownConcept(client, first.toSerializedRef(), second)
    }

    private fun IMonoStep<INode>.nodeRefAndConcept(): IMonoStep<IZip2Output<Any?, INodeReference, ConceptReference?>> {
        return nodeReference().zip(conceptReference())
    }

    private fun IFluxStep<INode>.nodeRefAndConcept(): IFluxStep<IZip2Output<Any?, INodeReference, ConceptReference?>> {
        return map { it.nodeRefAndConcept() }
    }
}

/**
 * Performance optimization. The concept is immutable and required for creating a typed node.
 */
class ModelQLNodeWithKnownConcept(
    client: ModelQLClient,
    override val reference: SerializedNodeReference,
    private val conceptReference: ConceptReference?
) : ModelQLNode(client) {
    override fun replaceQueryRoot(root: IMonoStep<INode>): IMonoStep<INode> {
        return reference.asMono().resolve()
    }

    override fun getConceptReference(): IConceptReference? {
        return conceptReference
    }

    override val concept: IConcept?
        get() = getConceptReference()?.resolve()
}

abstract class ModelQLNodeWithConceptCache(client: ModelQLClient) : ModelQLNode(client) {
    private val conceptRef: ConceptReference? by lazy { blockingQuery { it.conceptReference() } }

    override val concept: IConcept?
        get() = conceptRef?.resolve()

    override fun getConceptReference(): IConceptReference? {
        return conceptRef
    }
}

class ModelQLNodeWithConceptQuery(
    client: ModelQLClient,
    override val reference: SerializedNodeReference
) : ModelQLNodeWithConceptCache(client) {

    override fun replaceQueryRoot(root: IMonoStep<INode>): IMonoStep<INode> {
        return reference.asMono().resolve()
    }
}

class ModelQLRootNode(client: ModelQLClient) : ModelQLNodeWithConceptCache(client) {
    override val reference: INodeReference
        get() = ModelQLRootNodeReference()
}

class ModelQLRootNodeReference : INodeReference {
    override fun resolveNode(area: IArea?): INode? {
        if (area is ModelQLArea) return ModelQLRootNode(area.client)
        return area?.resolveNode(this)
    }
}

internal fun INodeReference.toSerializedRef() = when (this) {
    is SerializedNodeReference -> this
    else -> SerializedNodeReference(this.serialize())
}

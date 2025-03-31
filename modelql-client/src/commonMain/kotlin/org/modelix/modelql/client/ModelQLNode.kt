package org.modelix.modelql.client

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.async.INodeWithAsyncSupport
import org.modelix.model.api.async.NodeAsAsyncNode
import org.modelix.model.api.key
import org.modelix.model.api.resolve
import org.modelix.model.api.resolveChildLinkOrFallback
import org.modelix.model.api.resolvePropertyOrFallback
import org.modelix.model.api.resolveReferenceLinkOrFallback
import org.modelix.model.area.IArea
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IFluxUnboundQuery
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IMonoUnboundQuery
import org.modelix.modelql.core.IQueryExecutor
import org.modelix.modelql.core.IUnboundQuery
import org.modelix.modelql.core.IZip2Output
import org.modelix.modelql.core.StepStream
import org.modelix.modelql.core.asMono
import org.modelix.modelql.core.asStepOutput
import org.modelix.modelql.core.filterNotNull
import org.modelix.modelql.core.first
import org.modelix.modelql.core.flatMap
import org.modelix.modelql.core.map
import org.modelix.modelql.core.mapIfNotNull
import org.modelix.modelql.core.orNull
import org.modelix.modelql.core.toList
import org.modelix.modelql.core.zip
import org.modelix.modelql.untyped.ISupportsModelQL
import org.modelix.modelql.untyped.addNewChild
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.asMono
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.conceptReference
import org.modelix.modelql.untyped.moveChild
import org.modelix.modelql.untyped.nodeReference
import org.modelix.modelql.untyped.parent
import org.modelix.modelql.untyped.property
import org.modelix.modelql.untyped.reference
import org.modelix.modelql.untyped.remove
import org.modelix.modelql.untyped.resolve
import org.modelix.modelql.untyped.roleInParent
import org.modelix.modelql.untyped.setProperty
import org.modelix.modelql.untyped.setReference
import org.modelix.streams.BlockingStreamExecutor
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor

class ModelQLNodeAsAsyncNode(node: ModelQLNode) : NodeAsAsyncNode(node) {
    override fun getStreamExecutor(): IStreamExecutor = BlockingStreamExecutor
}

abstract class ModelQLNode(val client: ModelQLClient) :
    INode, ISupportsModelQL, IQueryExecutor<INode>, INodeWithAsyncSupport {
    override fun usesRoleIds(): Boolean = true

    override fun createQueryExecutor(): IQueryExecutor<INode> {
        return this
    }

    override fun getAsyncNode(): IAsyncNode {
        return ModelQLNodeAsAsyncNode(this)
    }

    override fun <Out> createStream(query: IUnboundQuery<INode, *, Out>): StepStream<Out> {
        return IStream.singleFromCoroutine {
            val result = when (query) {
                is IMonoUnboundQuery<*, *> -> {
                    val castedQuery = query as IMonoUnboundQuery<INode, Out>
                    val queryOnNode = IUnboundQuery.buildMono { replaceQueryRoot(it).map(castedQuery) }
                    listOf(client.runQuery(queryOnNode).asStepOutput(null))
                }

                is IFluxUnboundQuery<*, *> -> {
                    val castedQuery = query as IFluxUnboundQuery<INode, Out>
                    val queryOnNode = IUnboundQuery.buildFlux { replaceQueryRoot(it).flatMap(castedQuery) }
                    client.runQuery(queryOnNode)
                }

                else -> throw UnsupportedOperationException("Unknown query type: $query")
            }
            result
        }.flatMapIterable { it }
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
        get() = blockingQuery { it.parent().orNull() }

    override fun getChildren(role: IChildLink): Iterable<INode> {
        return blockingQuery { it.children(role.key()).toList() }
    }

    override fun getChildren(role: String?): Iterable<INode> {
        return getChildren(resolveChildLinkOrFallback(role))
    }

    override val allChildren: Iterable<INode>
        get() = blockingQuery { it.allChildren().toList() }

    override fun moveChild(role: IChildLink, index: Int, child: INode) {
        blockingQuery { it.moveChild(role, index, child.reference.asMono().resolve()) }
    }

    override fun moveChild(role: String?, index: Int, child: INode) {
        moveChild(resolveChildLinkOrFallback(role), index, child)
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode {
        return blockingQuery { it.addNewChild(role, index, concept as ConceptReference?).first() }
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode {
        return addNewChild(role, index, concept?.getReference())
    }

    override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
        return addNewChild(resolveChildLinkOrFallback(role), index, concept)
    }

    override fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode {
        return addNewChild(resolveChildLinkOrFallback(role), index, concept)
    }

    override fun removeChild(child: INode) {
        blockingQuery { child.reference.asMono().resolve().remove() }
    }

    override fun getReferenceTarget(role: String): INode? {
        return blockingQuery { it.reference(role).filterNotNull().nodeRefAndConcept().orNull() }?.toNode()
    }

    override fun getReferenceTargetRef(role: String): INodeReference? {
        return blockingQuery { it.reference(role).nodeReference().orNull() }
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        setReferenceTarget(link, target?.reference)
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        blockingQuery { it.setReference(role, target.asMono().mapIfNotNull { it.resolve() }) }
    }

    override fun setReferenceTarget(role: String, target: INode?) {
        setReferenceTarget(resolveReferenceLinkOrFallback(role), target)
    }

    override fun getPropertyValue(role: String): String? {
        return blockingQuery { it.property(role) }
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        blockingQuery { it.setProperty(property, value.asMono()) }
    }

    override fun setPropertyValue(role: String, value: String?) {
        setPropertyValue(resolvePropertyOrFallback(role), value)
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
        return map { it.nodeReference().zip(it.conceptReference()) }
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
    private val conceptReference: ConceptReference?,
) : ModelQLNode(client) {
    override fun replaceQueryRoot(root: IMonoStep<INode>): IMonoStep<INode> {
        return reference.asMono().resolve()
    }

    override fun getConceptReference(): ConceptReference? {
        return conceptReference
    }

    override val concept: IConcept?
        get() = getConceptReference()?.resolve()
}

abstract class ModelQLNodeWithConceptCache(client: ModelQLClient) : ModelQLNode(client) {
    private val conceptRef: ConceptReference? by lazy { blockingQuery { it.conceptReference() } }

    override val concept: IConcept?
        get() = conceptRef?.resolve()

    override fun getConceptReference(): ConceptReference? {
        return conceptRef
    }
}

class ModelQLNodeWithConceptQuery(
    client: ModelQLClient,
    override val reference: SerializedNodeReference,
) : ModelQLNodeWithConceptCache(client) {

    override fun replaceQueryRoot(root: IMonoStep<INode>): IMonoStep<INode> {
        return reference.asMono().resolve()
    }
}

class ModelQLRootNode(client: ModelQLClient) : ModelQLNodeWithConceptCache(client) {
    override val reference: INodeReference
        get() = ModelQLRootNodeReference()
}

class ModelQLRootNodeReference : INodeReference() {
    override fun resolveNode(area: IArea?): INode? {
        if (area is ModelQLArea) return ModelQLRootNode(area.client)
        return area?.resolveNode(this)
    }
    override fun serialize(): String = TODO("Not yet implemented")
}

internal fun INodeReference.toSerializedRef() = when (this) {
    is SerializedNodeReference -> this
    else -> SerializedNodeReference(this.serialize())
}

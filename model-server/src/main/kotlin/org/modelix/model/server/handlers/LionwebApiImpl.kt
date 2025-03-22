package org.modelix.model.server.handlers

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.modelix.authorization.getUserName
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.TreePointer
import org.modelix.model.api.WritableNodeAsLegacyNode
import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.api.resolve
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.runWrite
import org.modelix.model.persistent.SerializationUtil
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.runReadIO
import org.modelix.model.server.store.runWriteIO
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.NodeAssociationToModelServer
import org.modelix.streams.IStream
import org.modelix.streams.ifEmpty
import org.modelix.streams.zip

class LionwebApiImpl(val repoManager: IRepositoriesManager) : LionwebApi() {

    override suspend fun RoutingContext.bulkDelete(
        partition: String,
        nodes: List<String>,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.bulkRetrieve(
        partition: String,
        nodes: List<String>,
        depthLimit: Int?,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.bulkStore(
        partition: String,
        lionwebSerializationChunk: LionwebSerializationChunk,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.createPartitions(repository: String, lionwebSerializationChunk: LionwebSerializationChunk) {
        val partitions = (lionwebSerializationChunk.nodes ?: emptyList()).filter { it.parent == null }
        val nodesById = (lionwebSerializationChunk.nodes ?: emptyList()).associateBy { it.id }
        val branch = RepositoryId(repository).getBranchReference()

        @OptIn(RequiresTransaction::class)
        val baseVersion = runRead { repoManager.getVersion(branch) }

        if (baseVersion == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val idGenerator = repoManager.getStoreManager().idGenerator
        val treePointer = TreePointer(baseVersion.getTree(), idGenerator)
        val rootNode = treePointer.getRootNode().asAsyncNode()
        treePointer.runWrite {
            val existingPartitions = baseVersion.graph.getStreamExecutor().query {
                rootNode.getAllChildren()
                    .flatMap { node -> node.lionwebId().map { it to node } }
                    .toMap({ it.first }, { it.second })
            }

            for (partition in partitions) {
                val existing = existingPartitions[partition.id]
                val targetNode = if (existing == null) {
                    rootNode.asWritableNode()
                        .addNewChild(NullChildLinkReference, -1, partition.classifier.toConceptReference())
                } else {
                    // cache warmup
                    baseVersion.graph.getStreamExecutor()
                        .iterate<IAsyncNode>({ existing.getDescendants(true).take(30_000) }) {}

                    existing.asWritableNode()
                }
                ModelSynchronizer(
                    sourceRoot = LionwebDataAsNode(partition, null, null, nodesById),
                    targetRoot = targetNode,
                    nodeAssociation = NodeAssociationToModelServer(treePointer),
                ).synchronize()
            }
        }

        call.respond(
            LionwebResponse(
                success = true,
                messages = listOf(
                    LionwebResponseMessage(
                        kind = "RepoVersion",
                        message = lionwebSerializationChunk.serializationFormatVersion,
                    ),
                ),
            ),
        )
    }

    override suspend fun RoutingContext.deletePartitions(lionwebSerializationChunk: LionwebSerializationChunk) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.getIds(count: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.listPartitions(repository: String) {
        @OptIn(RequiresTransaction::class)
        val version = runRead { repoManager.getVersion(RepositoryId(repository).getBranchReference()) }
        if (version == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val tree = TreePointer(version.getTree())
        val rootNode = tree.getRootNode().asAsyncNode()

        val nodesData = version.graph.getStreamExecutor().querySuspending {
            rootNode.getAllChildren().flatMap { toLionwebNode(it) }.map { it.copy(parent = null) }.toList()
        }
        val responseData = ListPartitions200Response(
            success = true,
            messages = emptyList(),
            chunk = LionwebSerializationChunk(
                languages = emptyList(),
                nodes = nodesData,
            ),
        )

        call.respond(responseData)
    }

    private fun toLionwebNode(node: IAsyncNode): IStream.One<LionwebNodeStructure> {
        val id = node.lionwebId()
        val concept = node.getConceptRef()
        val allProperties = node.getAllPropertyValues().toList()
        val childIdsWithRoles = node.getAllChildren().flatMap { child ->
            child.getRoleInParent().zipWith(child.lionwebId()) { role, id ->
                role to id
            }
        }.toList().map { it.groupBy { it.first } }
        val referenceTargetsWithResolveInfo = node.getAllReferenceTargets().flatMap { (role, target) ->
            target.lionwebId().zipWith(node.getPropertyValue(role.toResolveInfoRole()).orNull()) { lionwebId, resolveInfo ->
                ReferenceWithResolveInfo(role, lionwebId, resolveInfo)
            }
        }.toList()
        val parentId = node.getParent().flatMapZeroOrOne { it.lionwebId() }.orNull()

        return IStream.zip(
            id,
            concept,
            allProperties,
            childIdsWithRoles,
            referenceTargetsWithResolveInfo,
            parentId,
        ) { id, concept, allProperties, childIdsWithRoles, referenceTargetsWithResolveInfo, parentId ->
            LionwebNodeStructure(
                id = id,
                classifier = concept.toLionWeb(),
                properties = allProperties.map {
                    LionwebNodeStructurePropertiesInner(
                        property = it.first.toLionWeb(),
                        value = it.second,
                    )
                },
                containments = childIdsWithRoles.filterNot { it.key.matches(annotationsRole) }.map {
                    LionwebNodeStructureContainmentsInner(
                        containment = it.key.toLionWeb(),
                        children = it.value.map { it.second },
                    )
                },
                references = referenceTargetsWithResolveInfo.groupBy { it.role }.map {
                    LionwebNodeStructureReferencesInner(
                        reference = it.key.toLionWeb(),
                        targets = it.value.map {
                            LionwebNodeStructureReferencesInnerTargetsInner(
                                resolveInfo = it.resolveInfo,
                                reference = it.targetId,
                            )
                        },
                    )
                },
                annotations = childIdsWithRoles.filter { it.key.matches(annotationsRole) }
                    .flatMap { it.value }.map { it.second },
                parent = parentId,
            )
        }
    }

    override suspend fun RoutingContext.listRepositories() {
        @OptIn(RequiresTransaction::class)
        call.respond(
            runRead {
                ListRepositories200Response(
                    success = true,
                    messages = emptyList(),
                    repositories = repoManager.getRepositories().map { repo ->
                        LionwebRepositoryConfiguration(
                            name = repo.id,
                            lionwebVersion = "2024.1",
                            history = true,
                        )
                    },
                )
            },
        )
    }

    override suspend fun RoutingContext.createRepository(
        repository: String,
        history: Boolean?,
        lionWebVersion: String?,
    ) {
        val user = call.getUserName()
        @OptIn(RequiresTransaction::class)
        runWrite {
            repoManager.createRepository(
                RepositoryId(repository),
                userName = user,
            )
        }
        call.respond(LionwebResponse(success = true, messages = emptyList()))
    }

    private suspend fun <R> runRead(body: () -> R): R {
        return repoManager.getTransactionManager().runReadIO(body)
    }

    private suspend fun <R> runWrite(body: () -> R): R {
        return repoManager.getTransactionManager().runWriteIO(body)
    }
}

private val annotationsRole = IChildLinkReference.fromIdAndName("lionweb:annotations", "lionweb-annotations")

private class ReferenceWithResolveInfo(
    val role: IReferenceLinkReference,
    val targetId: String,
    val resolveInfo: String?,
)

private fun IAsyncNode.lionwebId(): IStream.One<String> {
    return getPropertyValue(IPropertyReference.fromId("lionweb:id"))
        .ifEmpty { asRegularNode().asReadableNode().getNodeReference().serialize() }
}

private fun ConceptReference.toLionWeb(): LionwebMetaPointer {
    return createLionwebMetaPointer(getUID())
}

private fun LionwebMetaPointer.serialize(): String {
    return "lionweb:" +
        SerializationUtil.escape(language) +
        ":" +
        SerializationUtil.escape(key) +
        ":" +
        SerializationUtil.escape(version)
}

private fun LionwebMetaPointer.toConceptReference(): ConceptReference {
    return ConceptReference(serialize())
}

private fun LionwebMetaPointer?.toChildLinkReference(): IChildLinkReference {
    return if (this == null) NullChildLinkReference else IChildLinkReference.fromId(serialize())
}

private fun LionwebMetaPointer.toReferenceLinkReference(): IReferenceLinkReference {
    return IReferenceLinkReference.fromId(serialize())
}

private fun LionwebMetaPointer.toPropertyReference(): IPropertyReference {
    return IPropertyReference.fromId(serialize())
}

private fun IRoleReference.toLionWeb(): LionwebMetaPointer {
    return createLionwebMetaPointer(getIdOrName())
}

private fun LionwebNodeStructureContainmentsInner.modelixLink(): IChildLinkReference {
    return containment?.toChildLinkReference() ?: NullChildLinkReference
}

private fun createLionwebMetaPointer(id: String): LionwebMetaPointer {
    val parts = id.split(':')
    if (parts.size == 4 && parts[0] == "lionweb") {
        return LionwebMetaPointer(
            key = SerializationUtil.unescape(parts[1]) ?: "",
            language = SerializationUtil.unescape(parts[2]),
            version = SerializationUtil.unescape(parts[3]),
        )
    } else {
        return LionwebMetaPointer(
            key = id,
            language = null,
            version = null,
        )
    }
}

private fun IReferenceLinkReference.toResolveInfoRole(): IPropertyReference {
    return IPropertyReference.fromId("resolve-info:" + getIdOrName())
}

class LionwebDataAsNode(
    val data: LionwebNodeStructure,
    val containmentPointer: LionwebMetaPointer?,
    val parent: LionwebDataAsNode?,
    val allNodes: Map<String, LionwebNodeStructure>,
) : IWritableNode {
    private val index: Map<String, LionwebDataAsNode>? = if (parent != null) {
        null
    } else {
        asLegacyNode().getDescendants(true)
            .map { it.asWritableNode() as LionwebDataAsNode }
            .associateBy { it.data.id }
    }

    private fun getIndex(): Map<String, LionwebDataAsNode> = (parent?.getIndex() ?: index)!!

    private fun tryResolveNode(ref: INodeReference): IWritableNode? {
        return getIndex()[ref.serialize()]
    }

    override fun asLegacyNode(): INode {
        return WritableNodeAsLegacyNode(this)
    }

    override fun getModel(): IMutableModel {
        TODO("Not yet implemented")
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun getNodeReference(): INodeReference {
        return NodeReference(checkNotNull(data.id) { "No ID specified" })
    }

    override fun getConcept(): IConcept {
        return data.classifier.toConceptReference().resolve()
    }

    override fun getConceptReference(): ConceptReference {
        return data.classifier.toConceptReference()
    }

    override fun getParent(): IWritableNode? {
        return parent
    }

    override fun getContainmentLink(): IChildLinkReference {
        return containmentPointer.toChildLinkReference()
    }

    override fun getAllChildren(): List<IWritableNode> {
        return (data.containments ?: emptyList()).flatMap { containment ->
            (containment.children ?: emptyList()).map {
                LionwebDataAsNode(allNodes[it]!!, containment.containment, this, allNodes)
            }
        }
    }

    override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
        return (data.containments ?: emptyList()).filter { it.containment.toChildLinkReference().matches(role) }
            .flatMap { containment ->
                (containment.children ?: emptyList()).map {
                    LionwebDataAsNode(allNodes[it]!!, containment.containment, this, allNodes)
                }
            }
    }

    override fun getPropertyValue(property: IPropertyReference): String? {
        return data.properties?.find { it.property.toPropertyReference().matches(property) }?.value
    }

    override fun getPropertyLinks(): List<IPropertyReference> {
        return data.properties?.map { it.property.toPropertyReference() } ?: emptyList()
    }

    override fun getAllProperties(): List<Pair<IPropertyReference, String>> {
        return data.properties?.map { it.property.toPropertyReference() to it.value } ?: emptyList()
    }

    override fun getReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        return getReferenceTargetRef(role)?.let { tryResolveNode(it) }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
        return data.references
            ?.find { it.reference.toReferenceLinkReference().matches(role) }
            ?.targets
            ?.firstOrNull()
            ?.reference
            ?.let { NodeReference(it) }
    }

    override fun getReferenceLinks(): List<IReferenceLinkReference> {
        return data.references?.map { it.reference.toReferenceLinkReference() } ?: emptyList()
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
        return getAllReferenceTargetRefs().mapNotNull { it.first to (tryResolveNode(it.second) ?: return@mapNotNull null) }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
        return (data.references ?: emptyList()).mapNotNull {
            it.reference.toReferenceLinkReference() to NodeReference(it.targets?.firstOrNull()?.reference ?: return@mapNotNull null)
        }
    }

    override fun changeConcept(newConcept: ConceptReference): IWritableNode {
        throw UnsupportedOperationException("Immutable")
    }

    override fun setPropertyValue(property: IPropertyReference, value: String?) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun moveChild(
        role: IChildLinkReference,
        index: Int,
        child: IWritableNode,
    ) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun removeChild(child: IWritableNode) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun addNewChild(
        role: IChildLinkReference,
        index: Int,
        concept: ConceptReference,
    ): IWritableNode {
        throw UnsupportedOperationException("Immutable")
    }

    override fun addNewChildren(
        role: IChildLinkReference,
        index: Int,
        concepts: List<ConceptReference>,
    ): List<IWritableNode> {
        throw UnsupportedOperationException("Immutable")
    }

    override fun setReferenceTarget(
        role: IReferenceLinkReference,
        target: IWritableNode?,
    ) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun setReferenceTargetRef(
        role: IReferenceLinkReference,
        target: INodeReference?,
    ) {
        throw UnsupportedOperationException("Immutable")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LionwebDataAsNode) return false
        return other.data.id == data.id && parent == other.parent
    }

    override fun hashCode(): Int {
        return data.id.hashCode() + parent.hashCode()
    }
}

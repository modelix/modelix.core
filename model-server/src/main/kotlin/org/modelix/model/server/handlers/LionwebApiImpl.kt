package org.modelix.model.server.handlers

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.TreePointer
import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.api.getRootNode
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.SerializationUtil
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.runReadIO
import org.modelix.model.server.store.runWriteIO
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

    override suspend fun RoutingContext.createPartitions(lionwebSerializationChunk: LionwebSerializationChunk) {
        TODO("Not yet implemented")
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
            rootNode.getDescendants(true).flatMap { toLionwebNode(it) }.toList()
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
        @OptIn(RequiresTransaction::class)
        runWrite {
            repoManager.createRepository(
                RepositoryId(repository),
                userName = "lionweb@modelix.org",
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

    private fun IAsyncNode.lionwebId(): IStream.One<String> {
        return getPropertyValue(IPropertyReference.fromId("lionweb:id"))
            .ifEmpty { asRegularNode().asReadableNode().getNodeReference().serialize() }
    }

    private fun ConceptReference.toLionWeb(): LionwebMetaPointer {
        return createLionwebMetaPointer(getUID())
    }

    private fun IRoleReference.toLionWeb(): LionwebMetaPointer {
        return createLionwebMetaPointer(getIdOrName())
    }

    private fun createLionwebMetaPointer(id: String): LionwebMetaPointer {
        val parts = id.split(':')
        if (parts.size == 3 && parts[0] == "lionweb") {
            return LionwebMetaPointer(
                key = SerializationUtil.unescape(parts[1]) ?: "",
                language = SerializationUtil.unescape(parts[2]) ?: "",
                version = null,
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

    private val annotationsRole = IChildLinkReference.fromIdAndName("lionweb:annotations", "lionweb-annotations")
}

private class ReferenceWithResolveInfo(
    val role: IReferenceLinkReference,
    val targetId: String,
    val resolveInfo: String?,
)

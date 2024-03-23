/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server.handlers

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.origin
import io.ktor.server.request.acceptItems
import io.ktor.server.request.receive
import io.ktor.server.request.receiveStream
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.util.cio.use
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext
import org.modelix.api.public.Paths
import org.modelix.authorization.checkPermission
import org.modelix.authorization.getUserName
import org.modelix.authorization.hasPermission
import org.modelix.authorization.requiresLogin
import org.modelix.model.InMemoryModels
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.area.getArea
import org.modelix.model.client2.checkObjectHashes
import org.modelix.model.client2.getAllObjects
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.api.v2.VersionDeltaStream
import org.modelix.model.server.api.v2.VersionDeltaStreamV2
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.withGlobalRepositoryInCoroutine
import org.modelix.modelql.server.ModelQLServer
import org.slf4j.LoggerFactory

/**
 * Implements the endpoints used by the 'model-client', but compared to KeyValueLikeModelServer also understands what
 * client sends. This allows more validations and more responsibilities on the server side.
 */
class ModelReplicationServer(
    private val repositoriesManager: IRepositoriesManager,
    private val modelClient: LocalModelClient,
    private val inMemoryModels: InMemoryModels,
) {
    constructor(repositoriesManager: RepositoriesManager) :
        this(repositoriesManager, repositoriesManager.client, InMemoryModels())

    constructor(modelClient: LocalModelClient) : this(RepositoriesManager(modelClient), modelClient, InMemoryModels())
    constructor(storeClient: IStoreClient) : this(LocalModelClient(storeClient))

    companion object {
        private val LOG = LoggerFactory.getLogger(ModelReplicationServer::class.java)
    }

    private val storeClient: IStoreClient get() = modelClient.store

    fun init(application: Application) {
        application.apply {
            routing {
                requiresLogin {
                    installHandlers()
                }
            }
        }
    }

    private fun ApplicationCall.repositoryId() = RepositoryId(checkNotNull(parameters["repository"]) { "Parameter 'repository' not available" })
    private fun PipelineContext<Unit, ApplicationCall>.repositoryId() = call.repositoryId()
    private suspend fun <R> PipelineContext<Unit, ApplicationCall>.runWithRepository(body: suspend () -> R): R {
        return repositoriesManager.runWithRepository(repositoryId(), body)
    }

    private fun Route.installHandlers() {
        post<Paths.postGenerateClientId> {
            // checkPermission("model-server", "generate-client-id")
            call.respondText(storeClient.generateId("clientId").toString())
        }
        get<Paths.getServerId> {
            // Currently, the server ID is initialized in KeyValueLikeModelServer eagerly on startup.
            // Should KeyValueLikeModelServer be removed or change,
            // RepositoriesManager#maybeInitAndGetSeverId will initialize the server ID lazily on the first request.
            //
            // Functionally, it does not matter if the server ID is created eagerly or lazily,
            // as long as the same server ID is returned from the same server.
            val serverId = repositoriesManager.maybeInitAndGetSeverId()
            call.respondText(serverId)
        }
        get<Paths.getUserId> {
            call.respondText(call.getUserName() ?: call.request.origin.remoteHost)
        }
        get<Paths.getRepositories> {
            call.respondText(
                repositoriesManager.getRepositories()
                    .filter { call.hasPermission("repository", it.id, "list") }
                    .joinToString("\n") { it.id },
            )
        }

        get<Paths.getRepositoryBranches> {
            call.respondText(
                repositoriesManager
                    .getBranchNames(repositoryId())
                    .filter { call.hasPermission("repository", repositoryId().id, "branch", it, "list") }
                    .joinToString("\n"),
            )
        }

        get<Paths.getRepositoryBranch> {
            fun ApplicationCall.branchRef() = repositoryId().getBranchReference(parameter("branch"))
            fun PipelineContext<Unit, ApplicationCall>.branchRef() = call.branchRef()

            val baseVersionHash = call.request.queryParameters["lastKnown"]
            val branch = branchRef()

            checkPermission("repository", branch.repositoryId.id, "branch", branch.branchName, "pull")

            runWithRepository {
                val versionHash = repositoriesManager.getVersionHash(branch)
                    ?: throw BranchNotFoundException(branch)
                call.respondDelta(versionHash, baseVersionHash)
            }
        }

        delete<Paths.deleteRepositoryBranch> {
            val repositoryName = call.parameters["repository"] ?: throw BadRequestException(
                "Request lacks repository name", "missing-repository-name",
            )
            val repositoryId = try {
                RepositoryId(repositoryName)
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("Invalid repository name", "invalid-request", cause = e)
            }
            val branch = call.parameters["branch"] ?: throw BadRequestException(
                "Request lacks branch name", "missing-branch-name",
            )

            checkPermission("repository", repositoryId.id, "branch", branch, "delete")

            if (!repositoriesManager.getBranchNames(repositoryId).contains(branch)) {
                throw BranchNotFoundException(branch, repositoryId.id)
            }

            repositoriesManager.removeBranches(repositoryId, setOf(branch))

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.getRepositoryBranchHash> {
            fun ApplicationCall.branchRef() = repositoryId().getBranchReference(parameter("branch"))
            fun PipelineContext<Unit, ApplicationCall>.branchRef() = call.branchRef()
            val branch = branchRef()
            checkPermission("repository", branch.repositoryId.id, "branch", branch.branchName, "pull")
            runWithRepository {
                val versionHash = repositoriesManager.getVersionHash(branch)
                    ?: throw BranchNotFoundException(branch)
                call.respondText(versionHash)
            }
        }

        post<Paths.initializeRepository> {
            runWithRepository {
                checkPermission("repository", repositoryId().id, "create")

                val useRoleIds = call.request.queryParameters["useRoleIds"] != "false"
                val legacyGlobalStorage = call.request.queryParameters["legacyGlobalStorage"] == "true"
                val initialVersion = repositoriesManager.createRepository(repositoryId(), call.getUserName(), useRoleIds, legacyGlobalStorage)
                call.respondDelta(initialVersion.getContentHash(), null)
            }
        }

        post<Paths.deleteRepository> {
            val repositoryId = repositoryId()

            checkPermission("repository", repositoryId.id, "delete")

            runWithRepository {
                val foundAndDeleted = repositoriesManager.removeRepository(repositoryId)
                if (foundAndDeleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        post<Paths.postRepositoryBranch> {
            fun ApplicationCall.branchRef() = repositoryId().getBranchReference(parameter("branch"))
            fun PipelineContext<Unit, ApplicationCall>.branchRef() = call.branchRef()

            val branch = branchRef()
            checkPermission("repository", branch.repositoryId.id, "branch", branch.branchName, "push")

            runWithRepository {
                val deltaFromClient = call.receive<VersionDelta>()
                deltaFromClient.checkObjectHashes()
                storeClient.putAll(deltaFromClient.getAllObjects())
                val mergedHash = repositoriesManager.mergeChanges(branchRef(), deltaFromClient.versionHash)
                call.respondDelta(mergedHash, deltaFromClient.versionHash)
            }
        }

        get<Paths.pollRepositoryBranch> {
            fun ApplicationCall.branchRef() = repositoryId().getBranchReference(parameter("branch"))
            fun PipelineContext<Unit, ApplicationCall>.branchRef() = call.branchRef()

            val branch = branchRef()
            checkPermission("repository", branch.repositoryId.id, "branch", branch.branchName, "pull")

            runWithRepository {
                val lastKnownVersionHash = call.request.queryParameters["lastKnown"]
                val newVersionHash = repositoriesManager.pollVersionHash(branchRef(), lastKnownVersionHash)
                call.respondDelta(newVersionHash, lastKnownVersionHash)
            }
        }
        get<Paths.pollRepositoryBranchHash> {
            fun ApplicationCall.branchRef() = repositoryId().getBranchReference(parameter("branch"))
            fun PipelineContext<Unit, ApplicationCall>.branchRef() = call.branchRef()

            val branch = branchRef()
            checkPermission("repository", branch.repositoryId.id, "branch", branch.branchName, "pull")

            runWithRepository {
                val lastKnownVersionHash = call.request.queryParameters["lastKnown"]
                val newVersionHash = repositoriesManager.pollVersionHash(branchRef(), lastKnownVersionHash)
                call.respondText(newVersionHash)
            }
        }

        get<Paths.getRepositoryVersionHash> {
            checkPermission("repository", repositoryId().id, "objects", "read")

            // TODO permission check on the repository ID is not sufficient, because the client could
            //      provide any repository ID to access a version inside a different repository.
            //      A check if the version belongs to the repository is required.
            val baseVersionHash = call.request.queryParameters["lastKnown"]
            val versionHash = parameter("versionHash")
            runWithRepository {
                if (repositoriesManager.getVersion(repositoryId(), versionHash) == null) {
                    throw VersionNotFoundException(versionHash)
                    return@runWithRepository
                }
                call.respondDelta(versionHash, baseVersionHash)
            }
        }

        post<Paths.postRepositoryBranchQuery> { parameters ->
            val branchRef = RepositoryId(parameters.repository).getBranchReference(parameters.branch)
            checkPermission("repository", branchRef.repositoryId.id, "branch", branchRef.branchName, "query")
            runWithRepository {
                val version = repositoriesManager.getVersion(branchRef)
                LOG.trace("Running query on {} @ {}", branchRef, version)
                val initialTree = version!!.getTree()
                val branch = OTBranch(
                    PBranch(initialTree, modelClient.idGenerator),
                    modelClient.idGenerator,
                    modelClient.storeCache,
                )

                ModelQLServer.handleCall(call, { writeAccess ->
                    if (writeAccess) {
                        branch.getRootNode() to branch.getArea()
                    } else {
                        val model = inMemoryModels.getModel(initialTree).await()
                        model.getNode(ITree.ROOT_ID) to model.getArea()
                    }
                }, {
                    // writing the new version has to happen before call.respond is invoked, otherwise subsequent queries
                    // from the same client may still be executed on the old version.
                    val (ops, newTree) = branch.getPendingChanges()
                    if (newTree != initialTree) {
                        val newVersion = CLVersion.createRegularVersion(
                            id = modelClient.idGenerator.generate(),
                            author = getUserName(),
                            tree = newTree as CLTree,
                            baseVersion = version,
                            operations = ops.map { it.getOriginalOp() }.toTypedArray(),
                        )
                        repositoriesManager.mergeChanges(branchRef, newVersion.getContentHash())
                    }
                })
            }
        }

        post<Paths.postRepositoryVersionHashQuery> {
            checkPermission("repository", parameter("repository"), "objects", "read")
            runWithRepository {
                val versionHash = parameter("versionHash")
                val version = CLVersion.loadFromHash(versionHash, modelClient.storeCache)
                val initialTree = version.getTree()
                val branch = TreePointer(initialTree)
                ModelQLServer.handleCall(call, branch.getRootNode(), branch.getArea())
            }
        }

        put<Paths.putRepositoryObjects> {
            checkPermission("repository", parameter("repository"), "objects", "add")
            runWithRepository {
                val writtenEntries = withContext(Dispatchers.IO) {
                    val entries = call.receiveStream().bufferedReader().use { reader ->
                        reader.lineSequence().windowed(2, 2).map {
                            val key = it[0]
                            val value = it[1]

                            require(HashUtil.isSha256(key)) {
                                "This API cannot be used to store other entries than serialized objects." +
                                    " The key is expected to be a SHA256 hash over the value: $key -> $value"
                            }
                            val expectedKey = HashUtil.sha256(value)
                            require(expectedKey == key) { "Hash mismatch. Expected $expectedKey, but $key was provided. Value: $value" }

                            key to value
                        }.toMap()
                    }

                    storeClient.putAll(entries, true)

                    entries.size
                }
                call.respondText("$writtenEntries objects received")
            }
        }

        get<Paths.getVersionHash> {
            checkPermission("legacy-global-objects", "read")
            storeClient.withGlobalRepositoryInCoroutine {
                val baseVersionHash = call.request.queryParameters["lastKnown"]
                val versionHash = parameter("versionHash")
                if (storeClient[versionHash] == null) {
                    throw VersionNotFoundException(versionHash)
                }
                call.respondDelta(versionHash, baseVersionHash)
            }
        }
    }

    private suspend fun ApplicationCall.respondDelta(versionHash: String, baseVersionHash: String?) {
        val expectedTypes = request.acceptItems().map { ContentType.parse(it.value) }
        return if (expectedTypes.any { it.match(VersionDeltaStreamV2.CONTENT_TYPE) }) {
            respondDeltaAsObjectStreamV2(versionHash, baseVersionHash)
        } else if (expectedTypes.any { it.match(VersionDeltaStream.CONTENT_TYPE) }) {
            respondDeltaAsObjectStreamV1(versionHash, baseVersionHash, false)
        } else if (expectedTypes.any { it.match(ContentType.Application.Json) }) {
            respondDeltaAsJson(versionHash, baseVersionHash)
        } else {
            respondDeltaAsObjectStreamV1(versionHash, baseVersionHash, true)
        }
    }

    private suspend fun ApplicationCall.respondDeltaAsJson(versionHash: String, baseVersionHash: String?) {
        val delta = VersionDelta(
            versionHash,
            baseVersionHash,
            objectsMap = repositoriesManager.computeDelta(versionHash, baseVersionHash).asMap(),
        )
        respond(delta)
    }

    private suspend fun ApplicationCall.respondDeltaAsObjectStreamV1(
        versionHash: String,
        baseVersionHash: String?,
        plainText: Boolean,
    ) {
        // Call `computeDelta` before starting to respond.
        // It could already throw an exception, and in that case we do not want a successful response status.
        val objectData = repositoriesManager.computeDelta(versionHash, baseVersionHash)
        val contentType = if (plainText) ContentType.Text.Plain else VersionDeltaStream.CONTENT_TYPE
        respondBytesWriter(contentType) {
            this.useClosingWithoutCause {
                objectData.asFlow()
                    .flatten()
                    .withSeparator("\n")
                    .onEmpty { emit(versionHash) }
                    .withIndex()
                    .collect {
                        if (it.index == 0) check(it.value == versionHash) { "First object should be the version" }
                        writeStringUtf8(it.value)
                    }
            }
        }
    }

    private suspend fun ApplicationCall.respondDeltaAsObjectStreamV2(versionHash: String, baseVersionHash: String?) {
        val objectData = repositoriesManager.computeDelta(versionHash, baseVersionHash)
        respondBytesWriter(VersionDeltaStreamV2.CONTENT_TYPE) {
            this.useClosingWithoutCause {
                VersionDeltaStreamV2.encodeVersionDeltaStreamV2(this, versionHash, objectData.asFlow())
            }
        }
    }
}

/**
 * Same as [[ByteWriteChannel.use]] but closing without a cause in case of an exception.
 *
 * Calling [[ByteWriteChannel.close]] with a cause results in not closing the connection properly.
 * See ModelReplicationServerTest.`server closes connection when failing to compute delta after starting to respond`
 * This will only be fixed in Ktor 3.
 * See https://youtrack.jetbrains.com/issue/KTOR-4862/Ktor-hangs-if-exception-occurs-during-write-response-body
 */
private inline fun ByteWriteChannel.useClosingWithoutCause(block: ByteWriteChannel.() -> Unit) {
    try {
        block()
    } finally {
        close()
    }
}

private fun <T> Flow<Pair<T, T>>.flatten() = flow<T> {
    collect {
        emit(it.first)
        emit(it.second)
    }
}

private fun Flow<String>.withSeparator(separator: String) = flow {
    var first = true
    collect {
        if (first) {
            first = false
        } else {
            emit(separator)
        }
        emit(it)
    }
}

private fun PipelineContext<Unit, ApplicationCall>.parameter(name: String): String {
    return call.parameter(name)
}

private fun ApplicationCall.parameter(name: String): String {
    return requireNotNull(parameters[name]) { "Unknown parameter '$name'" }
}

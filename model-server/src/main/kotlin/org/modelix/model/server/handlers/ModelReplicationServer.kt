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
import io.ktor.server.request.acceptItems
import io.ktor.server.request.receive
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
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
import org.modelix.api.v2.DefaultApi
import org.modelix.authorization.getUserName
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
@Suppress("TooManyFunctions") // result of the API spec
class ModelReplicationServer(
    private val repositoriesManager: IRepositoriesManager,
    private val modelClient: LocalModelClient,
    private val inMemoryModels: InMemoryModels,
) : DefaultApi() {
    constructor(repositoriesManager: RepositoriesManager) :
        this(repositoriesManager, repositoriesManager.client, InMemoryModels())

    constructor(modelClient: LocalModelClient) : this(RepositoriesManager(modelClient), modelClient, InMemoryModels())
    constructor(storeClient: IStoreClient) : this(LocalModelClient(storeClient))

    companion object {
        private val LOG = LoggerFactory.getLogger(ModelReplicationServer::class.java)
    }

    private val storeClient: IStoreClient get() = modelClient.store

    fun init(application: Application) {
        application.routing {
            route("/v2") {
                installRoutes(this)
            }
        }
    }

    private fun repositoryId(paramValue: String?) = RepositoryId(checkNotNull(paramValue) { "Parameter 'repository' not available" })
    private suspend fun <R> runWithRepository(repository: String, body: suspend () -> R): R {
        return repositoriesManager.runWithRepository(repositoryId(repository), body)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositories() {
        call.respondText(repositoriesManager.getRepositories().joinToString("\n") { it.id })
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositoryBranches(repository: String) {
        call.respondText(repositoriesManager.getBranchNames(repositoryId(repository)).joinToString("\n"))
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositoryBranch(
        repository: String,
        branch: String,
        lastKnown: String?,
    ) {
        runWithRepository(repository) {
            val branchRef = repositoryId(repository).getBranchReference(branch)
            val versionHash = repositoriesManager.getVersionHash(branchRef) ?: throw BranchNotFoundException(branchRef)
            call.respondDelta(versionHash, lastKnown)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.deleteRepositoryBranch(
        repository: String,
        branch: String,
    ) {
        val repositoryId = try {
            RepositoryId(repository)
        } catch (e: IllegalArgumentException) {
            throw BadRequestException("Invalid repository name", "invalid-request", cause = e)
        }

        if (!repositoriesManager.getBranchNames(repositoryId).contains(branch)) {
            throw BranchNotFoundException(branch, repositoryId.id)
        }

        repositoriesManager.removeBranches(repositoryId, setOf(branch))

        call.respond(HttpStatusCode.NoContent)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositoryBranchHash(
        repository: String,
        branch: String,
    ) {
        runWithRepository(repository) {
            val branchRef = repositoryId(repository).getBranchReference(branch)
            val versionHash = repositoriesManager.getVersionHash(branchRef) ?: throw BranchNotFoundException(branchRef)
            call.respondText(versionHash)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.initializeRepository(
        repository: String,
        useRoleIds: Boolean?,
        legacyGlobalStorage: Boolean?,
    ) {
        runWithRepository(repository) {
            val initialVersion = repositoriesManager.createRepository(
                repositoryId(repository),
                call.getUserName(),
                useRoleIds ?: true,
                legacyGlobalStorage ?: false,
            )
            call.respondDelta(initialVersion.getContentHash(), null)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.deleteRepository(repository: String) {
        runWithRepository(repository) {
            val foundAndDeleted = repositoriesManager.removeRepository(repositoryId(repository))
            if (foundAndDeleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.postRepositoryBranch(
        repository: String,
        branch: String,
    ) {
        runWithRepository(repository) {
            val branchRef = repositoryId(repository).getBranchReference(branch)
            val deltaFromClient = call.receive<VersionDelta>()
            deltaFromClient.checkObjectHashes()
            storeClient.putAll(deltaFromClient.getAllObjects())
            val mergedHash = repositoriesManager.mergeChanges(branchRef, deltaFromClient.versionHash)
            call.respondDelta(mergedHash, deltaFromClient.versionHash)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.pollRepositoryBranch(
        repository: String,
        branch: String,
        lastKnown: String?,
    ) {
        runWithRepository(repository) {
            val branchRef = repositoryId(repository).getBranchReference(branch)
            val newVersionHash = repositoriesManager.pollVersionHash(branchRef, lastKnown)
            call.respondDelta(newVersionHash, lastKnown)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.pollRepositoryBranchHash(
        repository: String,
        branch: String,
        lastKnown: String?,
        legacyGlobalStorage: Boolean?,
    ) {
        runWithRepository(repository) {
            val branchRef = repositoryId(repository).getBranchReference(branch)
            val newVersionHash = repositoriesManager.pollVersionHash(branchRef, lastKnown)
            call.respondText(newVersionHash)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositoryVersionHash(
        versionHash: String,
        repository: String,
        lastKnown: String?,
    ) {
        // TODO permission check on the repository ID is not sufficient, because the client could
        //      provide any repository ID to access a version inside a different repository.
        //      A check if the version belongs to the repository is required.
        runWithRepository(repository) {
            if (repositoriesManager.getVersion(repositoryId(repository), versionHash) == null) {
                throw VersionNotFoundException(versionHash)
            }
            call.respondDelta(versionHash, lastKnown)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.postRepositoryBranchQuery(
        repository: String,
        branch: String,
    ) {
        val branchRef = repositoryId(repository).getBranchReference(branch)
        runWithRepository(repository) {
            val version = repositoriesManager.getVersion(branchRef)
            LOG.trace("Running query on {} @ {}", branchRef, version)
            val initialTree = version!!.getTree()
            val otBranch = OTBranch(
                PBranch(initialTree, modelClient.idGenerator),
                modelClient.idGenerator,
                modelClient.storeCache,
            )

            ModelQLServer.handleCall(call, { writeAccess ->
                if (writeAccess) {
                    otBranch.getRootNode() to otBranch.getArea()
                } else {
                    val model = inMemoryModels.getModel(initialTree).await()
                    model.getNode(ITree.ROOT_ID) to model.getArea()
                }
            }, {
                // writing the new version has to happen before call.respond is invoked, otherwise subsequent queries
                // from the same client may still be executed on the old version.
                val (ops, newTree) = otBranch.getPendingChanges()
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

    override suspend fun PipelineContext<Unit, ApplicationCall>.postRepositoryVersionHashQuery(
        versionHash: String,
        repository: String,
    ) {
        runWithRepository(repository) {
            val version = CLVersion.loadFromHash(versionHash, modelClient.storeCache)
            val initialTree = version.getTree()
            val branch = TreePointer(initialTree)
            ModelQLServer.handleCall(call, branch.getRootNode(), branch.getArea())
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.putRepositoryObjects(repository: String) {
        runWithRepository(repository) {
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

    @Deprecated("deprecated flag is set in the OpenAPI specification")
    override suspend fun PipelineContext<Unit, ApplicationCall>.getVersionHash(
        versionHash: String,
        lastKnown: String?,
    ) {
        // TODO versions should be stored inside a repository with permission checks.
        //      Knowing a version hash should not give you access to the content.
        //      This handler was already moved to the 'repositories' route. Removing it here would be a breaking
        //      change, but should be done in some future version.
        storeClient.withGlobalRepositoryInCoroutine {
            if (storeClient[versionHash] == null) {
                throw VersionNotFoundException(versionHash)
            }
            call.respondDelta(versionHash, lastKnown)
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

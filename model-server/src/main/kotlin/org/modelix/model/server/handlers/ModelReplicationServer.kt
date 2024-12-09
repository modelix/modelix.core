package org.modelix.model.server.handlers

import com.google.common.cache.CacheBuilder
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.acceptItems
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.routing
import io.ktor.util.cio.use
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext
import org.modelix.authorization.checkPermission
import org.modelix.authorization.getUserName
import org.modelix.authorization.hasPermission
import org.modelix.authorization.requiresLogin
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.TreeAsBranch
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.api.runSynchronized
import org.modelix.model.area.getArea
import org.modelix.model.client2.checkObjectHashes
import org.modelix.model.client2.getAllObjects
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.ModelServerPermissionSchema
import org.modelix.model.server.api.v2.ImmutableObjectsStream
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.api.v2.VersionDeltaStream
import org.modelix.model.server.api.v2.VersionDeltaStreamV2
import org.modelix.model.server.store.StoreManager
import org.modelix.modelql.core.IMemoizationPersistence
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.MonoUnboundQuery
import org.modelix.modelql.core.QueryEvaluationContext
import org.modelix.modelql.core.QueryGraphDescriptor
import org.modelix.modelql.core.upcast
import org.modelix.modelql.server.ModelQLServer
import org.modelix.streams.exactlyOne
import org.modelix.streams.getSynchronous
import org.slf4j.LoggerFactory

/**
 * Implements the endpoints used by the 'model-client', but compared to KeyValueLikeModelServer also understands what
 * client sends. This allows more validations and more responsibilities on the server side.
 */
@Suppress("TooManyFunctions") // result of the API spec
class ModelReplicationServer(
    private val repositoriesManager: IRepositoriesManager,
) : V2Api() {

    companion object {
        private val LOG = LoggerFactory.getLogger(ModelReplicationServer::class.java)
    }

    private val stores: StoreManager get() = repositoriesManager.getStoreManager()
    private val indexPersistence: IMemoizationPersistence = InMemoryMemoizationPersistence()

    fun init(application: Application) {
        application.routing {
            requiresLogin {
                installRoutes(this)
            }
        }
    }

    private fun repositoryId(paramValue: String?) =
        RepositoryId(checkNotNull(paramValue) { "Parameter 'repository' not available" })

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositories() {
        call.respondText(
            repositoriesManager.getRepositories()
                .filter { call.hasPermission(ModelServerPermissionSchema.repository(it).list) }
                .joinToString("\n") { it.id },
        )
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositoryBranches(repository: String) {
        call.respondText(
            repositoriesManager
                .getBranchNames(repositoryId(repository))
                .filter { call.hasPermission(ModelServerPermissionSchema.repository(repository).branch(it).list) }
                .joinToString("\n"),
        )
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositoryBranchDelta(
        repository: String,
        branch: String,
        lastKnown: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)
        val versionHash = repositoriesManager.getVersionHash(branchRef) ?: throw BranchNotFoundException(branchRef)
        call.respondDelta(RepositoryId(repository), versionHash, lastKnown)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositoryBranchV1(
        repository: String,
        branch: String,
        lastKnown: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)
        val versionHash = repositoriesManager.getVersionHash(branchRef) ?: throw BranchNotFoundException(branchRef)
        call.respond(BranchV1(branch, versionHash))
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.deleteRepositoryBranch(
        repository: String,
        branch: String,
    ) {
        val repositoryId = try {
            RepositoryId(repository)
        } catch (e: IllegalArgumentException) {
            throw InvalidRepositoryIdException(repository, e)
        }

        checkPermission(ModelServerPermissionSchema.repository(repositoryId).branch(branch).delete)

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
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)
        val versionHash = repositoriesManager.getVersionHash(branchRef) ?: throw BranchNotFoundException(branchRef)
        call.respondText(versionHash)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.initializeRepository(
        repository: String,
        useRoleIds: Boolean?,
        legacyGlobalStorage: Boolean?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).create)
        val initialVersion = repositoriesManager.createRepository(
            repositoryId(repository),
            call.getUserName(),
            useRoleIds ?: true,
            legacyGlobalStorage ?: false,
        )
        call.respondDelta(RepositoryId(repository), initialVersion.getContentHash(), null)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.deleteRepository(repository: String) {
        checkPermission(ModelServerPermissionSchema.repository(repository).delete)

        val foundAndDeleted = repositoriesManager.removeRepository(repositoryId(repository))
        if (foundAndDeleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.postRepositoryBranch(
        repository: String,
        branch: String,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).push)
        val branchRef = repositoryId(repository).getBranchReference(branch)
        val deltaFromClient = call.receive<VersionDelta>()
        deltaFromClient.checkObjectHashes()
        repositoriesManager.getStoreClient(RepositoryId(repository)).putAll(deltaFromClient.getAllObjects())
        val mergedHash = repositoriesManager.mergeChanges(branchRef, deltaFromClient.versionHash)
        call.respondDelta(RepositoryId(repository), mergedHash, deltaFromClient.versionHash)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.pollRepositoryBranch(
        repository: String,
        branch: String,
        lastKnown: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)
        val newVersionHash = repositoriesManager.pollVersionHash(branchRef, lastKnown)
        call.respondDelta(RepositoryId(repository), newVersionHash, lastKnown)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.postRepositoryObjectsGetAll(repository: String) {
        checkPermission(ModelServerPermissionSchema.repository(repository).objects.read)
        val channel = call.receiveChannel()
        val keys = hashSetOf<String>()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            keys.add(line)
        }

        val objects = withContext(Dispatchers.IO) {
            repositoriesManager.getStoreClient(RepositoryId(repository)).getAll(keys)
        }

        for (entry in objects) {
            if (entry.value == null) { throw ObjectValueNotFoundException(entry.key) }
        }
        @Suppress("UNCHECKED_CAST")
        objects as Map<String, String>
        call.respondTextWriter(contentType = ImmutableObjectsStream.CONTENT_TYPE) {
            ImmutableObjectsStream.encode(this, objects)
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.pollRepositoryBranchHash(
        repository: String,
        branch: String,
        lastKnown: String?,
        legacyGlobalStorage: Boolean?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)
        val newVersionHash = repositoriesManager.pollVersionHash(branchRef, lastKnown)
        call.respondText(newVersionHash)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getRepositoryVersionHash(
        versionHash: String,
        repository: String,
        lastKnown: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).objects.read)
        if (repositoriesManager.getVersion(repositoryId(repository), versionHash) == null) {
            throw VersionNotFoundException(versionHash)
        }
        call.respondDelta(RepositoryId(repository), versionHash, lastKnown)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.postRepositoryBranchQuery(
        repository: String,
        branchName: String,
    ) {
        val branchRef = repositoryId(repository).getBranchReference(branchName)
        checkPermission(ModelServerPermissionSchema.branch(branchRef).query)
        val version = repositoriesManager.getVersion(branchRef) ?: throw BranchNotFoundException(branchRef)
        LOG.trace("Running query on {} @ {}", branchRef, version)
        val initialTree = version.getTree()

        IMemoizationPersistence.CONTEXT_INSTANCE.runInCoroutine(indexPersistence) {
            lateinit var branch: IBranch
            ModelQLServer.handleCall(call, { writeAccess ->
                branch = if (writeAccess) {
                    OTBranch(
                        PBranch(initialTree, stores.idGenerator),
                        stores.idGenerator,
                        repositoriesManager.getLegacyObjectStore(RepositoryId(repository)),
                    )
                } else {
                    TreeAsBranch(initialTree)
                }
                branch.getRootNode() to branch.getArea()
            }, {
                // writing the new version has to happen before call.respond is invoked, otherwise subsequent queries
                // from the same client may still be executed on the old version.
                (branch as? OTBranch)?.let { otBranch ->
                    val (ops, newTree) = otBranch.getPendingChanges()
                    if (newTree != initialTree) {
                        val newVersion = CLVersion.createRegularVersion(
                            id = stores.idGenerator.generate(),
                            author = getUserName(),
                            tree = newTree,
                            baseVersion = version,
                            operations = ops.map { it.getOriginalOp() }.toTypedArray(),
                        )
                        repositoriesManager.mergeChanges(branchRef, newVersion.getContentHash())
                    }
                }
            })
        }
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.postRepositoryVersionHashQuery(
        versionHash: String,
        repository: String,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).objects.read)
        val version = CLVersion.loadFromHash(versionHash, repositoriesManager.getLegacyObjectStore(RepositoryId(repository)))
        val initialTree = version.getTree()
        val branch = TreePointer(initialTree)
        ModelQLServer.handleCall(call, branch.getRootNode(), branch.getArea())
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.putRepositoryObjects(repository: String) {
        checkPermission(ModelServerPermissionSchema.repository(parameter("repository")).objects.add)

        val channel = call.receiveChannel()
        // Hash map can be used. Server does not expect entries in any order.
        val entries = hashMapOf<String, String>()

        while (true) {
            val key = channel.readUTF8Line() ?: break

            if (!HashUtil.isSha256(key)) {
                throw InvalidObjectKeyException(key)
            }

            val value = channel.readUTF8Line() ?: throw ObjectKeyWithoutObjectValueException(key)

            val expectedKey = HashUtil.sha256(value)
            if (expectedKey != key) { throw MismatchingObjectKeyAndValueException(key, expectedKey, value) }
            entries[key] = value
        }

        withContext(Dispatchers.IO) {
            repositoriesManager.getStoreClient(RepositoryId(repository)).putAll(entries, true)
        }
        call.respondText("${entries.size} objects received")
    }

    @Deprecated("deprecated flag is set in the OpenAPI specification")
    override suspend fun PipelineContext<Unit, ApplicationCall>.getVersionHash(
        versionHash: String,
        lastKnown: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.legacyGlobalObjects.read)
        if (stores.getGlobalStoreClient()[versionHash] == null) {
            throw VersionNotFoundException(versionHash)
        }
        call.respondDelta(null, versionHash, lastKnown)
    }

    private suspend fun ApplicationCall.respondDelta(repositoryId: RepositoryId?, versionHash: String, baseVersionHash: String?) {
        val expectedTypes = request.acceptItems().map { ContentType.parse(it.value) }
        return if (expectedTypes.any { it.match(VersionDeltaStreamV2.CONTENT_TYPE) }) {
            respondDeltaAsObjectStreamV2(repositoryId, versionHash, baseVersionHash)
        } else if (expectedTypes.any { it.match(VersionDeltaStream.CONTENT_TYPE) }) {
            respondDeltaAsObjectStreamV1(repositoryId, versionHash, baseVersionHash, false)
        } else if (expectedTypes.any { it.match(ContentType.Application.Json) }) {
            respondDeltaAsJson(repositoryId, versionHash, baseVersionHash)
        } else {
            respondDeltaAsObjectStreamV1(repositoryId, versionHash, baseVersionHash, true)
        }
    }

    private suspend fun ApplicationCall.respondDeltaAsJson(repositoryId: RepositoryId?, versionHash: String, baseVersionHash: String?) {
        val delta = VersionDelta(
            versionHash,
            baseVersionHash,
            objectsMap = repositoriesManager.computeDelta(repositoryId, versionHash, baseVersionHash).asMap(),
        )
        respond(delta)
    }

    private suspend fun ApplicationCall.respondDeltaAsObjectStreamV1(
        repositoryId: RepositoryId?,
        versionHash: String,
        baseVersionHash: String?,
        plainText: Boolean,
    ) {
        // Call `computeDelta` before starting to respond.
        // It could already throw an exception, and in that case we do not want a successful response status.
        val objectData = repositoriesManager.computeDelta(repositoryId, versionHash, baseVersionHash)
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

    private suspend fun ApplicationCall.respondDeltaAsObjectStreamV2(repositoryId: RepositoryId?, versionHash: String, baseVersionHash: String?) {
        val objectData = repositoriesManager.computeDelta(repositoryId, versionHash, baseVersionHash)
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

@Suppress("UNCHECKED_CAST")
private fun <K, V> Map<K, V?>.checkValuesNotNull(lazyMessage: (K) -> Any): Map<K, V> = apply {
    for (entry in this) {
        checkNotNull(entry.value) { lazyMessage(entry.key) }
    }
} as Map<K, V>

private fun PipelineContext<Unit, ApplicationCall>.parameter(name: String): String {
    return call.parameter(name)
}

private fun ApplicationCall.parameter(name: String): String {
    return requireNotNull(parameters[name]) { "Unknown parameter '$name'" }
}

class InMemoryMemoizationPersistence : IMemoizationPersistence {

    private val cache = CacheBuilder.newBuilder().softValues().build<IndexCacheKey, IStepOutput<*>>()

    /**
     * Used for deduplication of instances to safe memory.
     */
    private val descriptorInstances = CacheBuilder.newBuilder().maximumSize(100).build<QueryGraphDescriptor, QueryGraphDescriptor>()

    override fun <In, Out> getMemoizer(query: MonoUnboundQuery<In, Out>): IMemoizationPersistence.Memoizer<In, Out> {
        return MemoizerImpl(query, query.createDescriptor().normalize().deduplicate())
    }

    private inner class MemoizerImpl<In, Out>(val query: MonoUnboundQuery<In, Out>, val normalizedQueryDescriptor: QueryGraphDescriptor) : IMemoizationPersistence.Memoizer<In, Out> {
        override fun memoize(input: IStepOutput<In>): IStepOutput<Out> {
            runSynchronized(cache) {
                return cache.get(IndexCacheKey(normalizedQueryDescriptor, input)) {
                    query.asStream(QueryEvaluationContext.EMPTY, input).exactlyOne().getSynchronous()
                }.upcast()
            }
        }
    }

    private fun QueryGraphDescriptor.deduplicate() = descriptorInstances.get(this) { this }

    private data class IndexCacheKey(val query: QueryGraphDescriptor, val input: Any?)

    private class IndexData<K, V>(val map: Map<K, List<IStepOutput<V>>>)

    private fun <K, V> IndexData<*, *>.upcast() = this as IndexData<K, V>
}

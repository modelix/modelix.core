package org.modelix.model.server.handlers

import com.google.common.cache.CacheBuilder
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.acceptItems
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.routing
import io.ktor.util.cio.use
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.modelix.authorization.checkPermission
import org.modelix.authorization.getUserName
import org.modelix.authorization.hasPermission
import org.modelix.authorization.requiresLogin
import org.modelix.datastructures.history.EquidistantIntervalsSpec
import org.modelix.datastructures.history.HistoryIndexNode
import org.modelix.datastructures.history.HistoryQueries
import org.modelix.datastructures.history.PaginationParameters
import org.modelix.datastructures.history.SplitPointsIntervalSpec
import org.modelix.datastructures.history.withTimeRangeFilter
import org.modelix.datastructures.objects.Object
import org.modelix.kotlin.utils.urlEncode
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.TreeId
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
import org.modelix.model.server.api.BranchInfo
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.model.server.api.v2.ImmutableObjectsStream
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.api.v2.VersionDeltaStream
import org.modelix.model.server.api.v2.VersionDeltaStreamV2
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.StoreManager
import org.modelix.model.server.store.runReadIO
import org.modelix.model.server.store.runWriteIO
import org.modelix.modelql.core.IMemoizationPersistence
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.MonoUnboundQuery
import org.modelix.modelql.core.QueryEvaluationContext
import org.modelix.modelql.core.QueryGraphDescriptor
import org.modelix.modelql.core.upcast
import org.modelix.modelql.server.ModelQLServer
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.ifEmpty
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
    private val indexPersistence = CacheBuilder.newBuilder().softValues().build<RepositoryId, InMemoryMemoizationPersistence>()

    fun init(application: Application) {
        application.routing {
            requiresLogin {
                installRoutes(this)
            }
        }
    }

    private fun repositoryId(paramValue: String?) =
        RepositoryId(checkNotNull(paramValue) { "Parameter 'repository' not available" })

    override suspend fun RoutingContext.getRepositories() {
        call.respondText(
            @OptIn(RequiresTransaction::class)
            runRead { repositoriesManager.getRepositories() }
                .filter { call.hasPermission(ModelServerPermissionSchema.repository(it).list) }
                .joinToString("\n") { it.id },
        )
    }

    override suspend fun RoutingContext.getRepositoryBranches(repository: String) {
        @OptIn(RequiresTransaction::class)
        val branchNames = runRead {
            repositoriesManager.getBranchNames(repositoryId(repository))
                .filter { call.hasPermission(ModelServerPermissionSchema.repository(repository).branch(it).list) }
        }
        if (acceptsJson()) {
            call.respond<List<BranchInfo>>(
                @OptIn(RequiresTransaction::class)
                runRead {
                    branchNames.mapNotNull { branchName ->
                        val branchRef = repositoryId(repository).getBranchReference(branchName)
                        val versionHash = repositoriesManager.getVersionHash(branchRef)
                        versionHash?.let { BranchInfo(branchName, it) }
                    }
                },
            )
        } else {
            call.respondText(branchNames.joinToString("\n"))
        }
    }

    private fun RoutingContext.acceptsJson(): Boolean =
        call.request.acceptItems().any { ContentType.parse(it.value).match(ContentType.Application.Json) }

    override suspend fun RoutingContext.getRepositoryBranchDelta(
        repository: String,
        branch: String,
        lastKnown: String?,
        filter: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)

        @OptIn(RequiresTransaction::class)
        val versionHash = runRead {
            repositoriesManager.getVersionHash(branchRef) ?: throw BranchNotFoundException(branchRef)
        }
        call.respondDelta(RepositoryId(repository), versionHash, parseFilter(filter, lastKnown))
    }

    private fun parseFilter(filterAsJson: String?, lastKnown: String?): ObjectDeltaFilter {
        return (filterAsJson?.let { ObjectDeltaFilter.fromJson(it) } ?: ObjectDeltaFilter()).let {
            if (lastKnown == null) it else it.copy(knownVersions = it.knownVersions + lastKnown)
        }
    }

    override suspend fun RoutingContext.getRepositoryBranchV1(
        repository: String,
        branch: String,
        lastKnown: String?,
        filter: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)

        @OptIn(RequiresTransaction::class)
        val versionHash = runRead {
            repositoriesManager.getVersionHash(branchRef) ?: throw BranchNotFoundException(branchRef)
        }
        call.respond(BranchV1(branch, versionHash))
    }

    override suspend fun RoutingContext.deleteRepositoryBranch(
        repository: String,
        branch: String,
    ) {
        val repositoryId = try {
            RepositoryId(repository)
        } catch (e: IllegalArgumentException) {
            throw InvalidRepositoryIdException(repository, e)
        }

        checkPermission(ModelServerPermissionSchema.repository(repositoryId).branch(branch).delete)

        @OptIn(RequiresTransaction::class)
        runWrite {
            if (!repositoriesManager.getBranchNames(repositoryId).contains(branch)) {
                throw BranchNotFoundException(branch, repositoryId.id)
            }

            repositoriesManager.removeBranches(repositoryId, setOf(branch))
        }

        call.respond(HttpStatusCode.NoContent)
    }

    override suspend fun RoutingContext.getRepositoryBranchHash(
        repository: String,
        branch: String,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)

        @OptIn(RequiresTransaction::class)
        val versionHash = runRead {
            repositoriesManager.getVersionHash(branchRef) ?: throw BranchNotFoundException(branchRef)
        }
        call.respondText(versionHash)
    }

    private suspend fun RoutingContext.getHistoryIndexInternal(repository: String, versionHash: String): Object<HistoryIndexNode> {
        checkPermission(ModelServerPermissionSchema.repository(repository).objects.read)
        val version = repositoriesManager.getVersion(repositoryId(repository), versionHash)
        if (version == null) {
            throw VersionNotFoundException(versionHash)
        }

        @OptIn(RequiresTransaction::class)
        val index = runWrite {
            (repositoriesManager as RepositoriesManager).getOrCreateHistoryIndex(repositoryId(repository), version)
        }
        return index
    }

    private fun parseTimeRange(minTime: String?, maxTime: String?): ClosedRange<Instant>? {
        if (minTime == null && maxTime == null) return null
        return Instant.fromEpochSeconds(minTime?.toLong() ?: 0L)..Instant.fromEpochSeconds(maxTime?.toLong() ?: Long.MAX_VALUE)
    }

    override suspend fun RoutingContext.getHistoryIndex(
        repository: String,
        versionHash: String,
    ) {
        val index = getHistoryIndexInternal(repository, versionHash)
        call.respondText(index.getHashString() + "\n" + index.data.serialize())
    }

    override suspend fun RoutingContext.getHistoryAsFixedIntervals(
        repository: String,
        versionHash: String,
        duration: Int,
        minTime: String?,
        maxTime: String?,
        skip: Int?,
        limit: Int?,
    ) {
        val index = getHistoryIndexInternal(repository, versionHash)
        val intervalsSpec =
            EquidistantIntervalsSpec(duration.seconds).withTimeRangeFilter(parseTimeRange(minTime, maxTime))
        val intervals = HistoryQueries { index }.intervals(
            intervalsSpec,
            PaginationParameters(skip ?: 0, limit ?: 200),
        )
        call.respond(intervals)
    }

    override suspend fun RoutingContext.getHistoryAsProvidedIntervals(
        repository: String,
        versionHash: String,
        requestBody: List<String>,
    ) {
        val index = getHistoryIndexInternal(repository, versionHash)
        val intervals = HistoryQueries { index }.intervals(
            SplitPointsIntervalSpec(requestBody.map { Instant.fromEpochSeconds(it.toLong()) }),
            PaginationParameters.ALL,
        )
        call.respond(intervals)
    }

    override suspend fun RoutingContext.getHistoryAsSessions(
        repository: String,
        versionHash: String,
        minTime: String?,
        maxTime: String?,
        delay: Int?,
        skip: Int?,
        limit: Int?,
    ) {
        val index = getHistoryIndexInternal(repository, versionHash)
        val intervals = HistoryQueries { index }.sessions(
            parseTimeRange(minTime, maxTime),
            delay?.seconds ?: 5.minutes,
            PaginationParameters(skip ?: 0, limit ?: 200),
        )
        call.respond(intervals)
    }

    override suspend fun RoutingContext.getHistoryEntries(
        repository: String,
        versionHash: String,
        minTime: String?,
        maxTime: String?,
        skip: Int?,
        limit: Int?,
    ) {
        val index = getHistoryIndexInternal(repository, versionHash)
        val entries = HistoryQueries { index }.range(
            parseTimeRange(minTime, maxTime),
            PaginationParameters(skip ?: 0, limit ?: 200),
        )
        call.respond(entries)
    }

    override suspend fun RoutingContext.initializeRepository(
        repositoryName: String,
        useRoleIds: Boolean?,
        legacyGlobalStorage: Boolean?,
    ) {
        val config = if (call.request.contentType() == ContentType.Application.Json) {
            call.receive<RepositoryConfig>().copy(
                // fix possible mismatch
                repositoryId = repositoryName,
                repositoryName = repositoryName,
                alternativeNames = emptySet(),
            )
        } else {
            // Legacy configuration for old clients.
            // New clients with support for new data structures will send the desired config as JSON.
            RepositoryConfig(
                legacyNameBasedRoles = useRoleIds ?: true,
                legacyGlobalStorage = legacyGlobalStorage ?: false,
                nodeIdType = RepositoryConfig.NodeIdType.INT64,
                primaryTreeType = RepositoryConfig.TreeType.HASH_ARRAY_MAPPED_TRIE,
                modelId = TreeId.random().id,
                repositoryId = repositoryName,
                repositoryName = repositoryName,
            )
        }

        val repositoryId = RepositoryId(config.repositoryId)
        checkPermission(ModelServerPermissionSchema.repository(repositoryId).create)

        @OptIn(RequiresTransaction::class)
        val initialVersion = runWrite {
            repositoriesManager.createRepository(
                config,
                call.getUserName(),
            )
        }
        call.respondDelta(repositoryId, initialVersion.getContentHash(), ObjectDeltaFilter())
    }

    override suspend fun RoutingContext.changeRepositoryConfig(repository: String) {
        checkPermission(ModelServerPermissionSchema.repository(repository).write)
        val newConfig: RepositoryConfig = call.receive()
        val updatedConfig =
            @OptIn(RequiresTransaction::class)
            runWrite {
                repositoriesManager.migrateRepository(newConfig, call.getUserName())
                repositoriesManager.getConfig(RepositoryId(repository))
            }
        call.respond(updatedConfig)
    }

    override suspend fun RoutingContext.getRepositoryConfig(repository: String) {
        checkPermission(ModelServerPermissionSchema.repository(repository).read)
        val config =
            @OptIn(RequiresTransaction::class)
            runRead {
                repositoriesManager.getConfig(RepositoryId(repository))
            }
        call.respond(config)
    }

    override suspend fun RoutingContext.deleteRepository(repository: String) {
        checkPermission(ModelServerPermissionSchema.repository(repository).delete)

        @OptIn(RequiresTransaction::class)
        val foundAndDeleted = runWrite {
            repositoriesManager.removeRepository(repositoryId(repository))
        }
        if (foundAndDeleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    override suspend fun RoutingContext.redirectToFrontend(
        repository: String,
        branch: String,
    ) {
        val urlTemplate = System.getenv("MODELIX_FRONTEND_URL") ?: "../../../../../history/{repository}/{branch}/"
        val url = urlTemplate.replace("{repository}", repository.urlEncode()).replace("{branch}", branch.urlEncode())
        call.respondRedirect(permanent = false, url = url)
    }

    override suspend fun RoutingContext.postRepositoryBranch(
        repository: String,
        branch: String,
        force: Boolean?,
        failIfExists: Boolean?,
    ) {
        val force = force == true
        val failIfExists = failIfExists == true
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).run { if (force) forcePush else push })

        val branchRef = repositoryId(repository).getBranchReference(branch)
        val deltaFromClient = call.receive<VersionDelta>()
        deltaFromClient.checkObjectHashes()
        val objectsFromClient = deltaFromClient.getAllObjects()
        withContext(Dispatchers.IO) {
            @OptIn(RequiresTransaction::class) // no transactions required for immutable store
            repositoriesManager.getStoreClient(RepositoryId(repository), true).putAll(objectsFromClient)
        }
        suspend fun <R : Any> writeToBranch(writeAction: () -> R, onSuccess: suspend (R) -> Unit) {
            val result =
                @OptIn(RequiresTransaction::class)
                runWrite {
                    if (failIfExists && repositoriesManager.getVersionHash(branchRef) != null) {
                        null
                    } else {
                        writeAction()
                    }
                }
            if (result == null) {
                call.respond(
                    HttpStatusCode.Conflict,
                    "Branch $branch in repository $repository already exists",
                )
            } else {
                onSuccess(result)
            }
        }

        if (force) {
            @OptIn(RequiresTransaction::class)
            writeToBranch({
                repositoriesManager.forcePush(branchRef, deltaFromClient.versionHash)
            }, {
                call.respondDelta(RepositoryId(repository), deltaFromClient.versionHash, ObjectDeltaFilter(deltaFromClient.versionHash))
            })
        } else {
            // Run a merge outside a transaction to keep the transaction for the actual merge smaller.
            // If there are no concurrent pushes on the same branch, then all the work is done here.
            val preMergedVersion = repositoriesManager.mergeChangesWithoutPush(branchRef, deltaFromClient.versionHash)

            @OptIn(RequiresTransaction::class)
            writeToBranch({
                repositoriesManager.mergeChanges(branchRef, preMergedVersion)
            }, { mergedHash ->
                call.respondDelta(RepositoryId(repository), mergedHash, ObjectDeltaFilter(deltaFromClient.versionHash))
            })
        }
    }

    override suspend fun RoutingContext.pollRepositoryBranch(
        repository: String,
        branch: String,
        lastKnown: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)
        val branchRef = repositoryId(repository).getBranchReference(branch)
        val newVersionHash = repositoriesManager.pollVersionHash(branchRef, lastKnown)
        call.respondDelta(RepositoryId(repository), newVersionHash, ObjectDeltaFilter(lastKnown))
    }

    override suspend fun RoutingContext.postRepositoryObjectsGetAll(repository: String) {
        checkPermission(ModelServerPermissionSchema.repository(repository).objects.read)
        val channel = call.receiveChannel()
        val keys = hashSetOf<String>()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            keys.add(line)
        }

        val objects = withContext(Dispatchers.IO) {
            @OptIn(RequiresTransaction::class) // no transactions required for immutable store
            repositoriesManager.getStoreClient(RepositoryId(repository), true).getAll(keys)
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

    override suspend fun RoutingContext.pollRepositoryBranchHash(
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

    override suspend fun RoutingContext.getRepositoryVersionHash(
        versionHash: String,
        repository: String,
        lastKnown: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).objects.read)
        if (repositoriesManager.getVersion(repositoryId(repository), versionHash) == null) {
            throw VersionNotFoundException(versionHash)
        }
        call.respondDelta(
            RepositoryId(repository),
            versionHash,
            ObjectDeltaFilter(
                knownVersions = setOfNotNull(lastKnown),
                includeHistory = false,
                includeOperations = false,
            ),
        )
    }

    override suspend fun RoutingContext.postRepositoryBranchQuery(
        repository: String,
        branchName: String,
    ) {
        val branchRef = repositoryId(repository).getBranchReference(branchName)
        checkPermission(ModelServerPermissionSchema.branch(branchRef).query)
        @OptIn(RequiresTransaction::class)
        val version = runRead { repositoriesManager.getVersion(branchRef) ?: throw BranchNotFoundException(branchRef) }
        LOG.trace("Running query on {} @ {}", branchRef, version)
        val initialTree = version.getTree()

        val persistence = indexPersistence.get(branchRef.repositoryId) {
            InMemoryMemoizationPersistence(stores.getAsyncStore(branchRef.repositoryId).getStreamExecutor())
        }
        IMemoizationPersistence.CONTEXT_INSTANCE.runInCoroutine(persistence) {
            lateinit var branch: IBranch
            ModelQLServer.handleCall(call, { writeAccess ->
                branch = if (writeAccess) {
                    OTBranch(
                        PBranch(initialTree, stores.idGenerator),
                        stores.idGenerator,
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
                        @OptIn(RequiresTransaction::class)
                        runWrite {
                            repositoriesManager.mergeChanges(branchRef, newVersion.getContentHash())
                        }
                    }
                }
            })
        }
    }

    override suspend fun RoutingContext.postRepositoryVersionHashQuery(
        versionHash: String,
        repository: String,
    ) {
        checkPermission(ModelServerPermissionSchema.repository(repository).objects.read)
        val version = CLVersion.loadFromHash(versionHash, repositoriesManager.getLegacyObjectStore(RepositoryId(repository)))
        val initialTree = version.getTree()
        val branch = TreePointer(initialTree)
        ModelQLServer.handleCall(call, branch.getRootNode(), branch.getArea())
    }

    override suspend fun RoutingContext.putRepositoryObjects(repository: String) {
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
            @OptIn(RequiresTransaction::class) // no transactions required for immutable store
            repositoriesManager.getStoreClient(RepositoryId(repository), true).putAll(entries, true)
        }
        call.respondText("${entries.size} objects received")
    }

    @Deprecated("deprecated flag is set in the OpenAPI specification")
    override suspend fun RoutingContext.getVersionHash(
        versionHash: String,
        lastKnown: String?,
    ) {
        checkPermission(ModelServerPermissionSchema.legacyGlobalObjects.read)
        @OptIn(RequiresTransaction::class)
        if (runRead { stores.getGlobalStoreClient()[versionHash] } == null) {
            throw VersionNotFoundException(versionHash)
        }
        call.respondDelta(null, versionHash, ObjectDeltaFilter(lastKnown))
    }

    private suspend fun ApplicationCall.respondDelta(repositoryId: RepositoryId?, versionHash: String, filter: ObjectDeltaFilter) {
        val expectedTypes = request.acceptItems().map { ContentType.parse(it.value) }
        return if (expectedTypes.any { it.match(VersionDeltaStreamV2.CONTENT_TYPE) }) {
            respondDeltaAsObjectStreamV2(repositoryId, versionHash, filter)
        } else if (expectedTypes.any { it.match(VersionDeltaStream.CONTENT_TYPE) }) {
            respondDeltaAsObjectStreamV1(repositoryId, versionHash, filter, false)
        } else if (expectedTypes.any { it.match(ContentType.Application.Json) }) {
            respondDeltaAsJson(repositoryId, versionHash, filter)
        } else {
            respondDeltaAsObjectStreamV1(repositoryId, versionHash, filter, true)
        }
    }

    private suspend fun ApplicationCall.respondDeltaAsJson(repositoryId: RepositoryId?, versionHash: String, filter: ObjectDeltaFilter) {
        val delta = VersionDelta(
            versionHash,
            filter.knownVersions.firstOrNull(),
            objectsMap = repositoriesManager.computeDelta(repositoryId, versionHash, filter).asMap(),
        )
        respond(delta)
    }

    private suspend fun ApplicationCall.respondDeltaAsObjectStreamV1(
        repositoryId: RepositoryId?,
        versionHash: String,
        filter: ObjectDeltaFilter,
        plainText: Boolean,
    ) {
        // Call `computeDelta` before starting to respond.
        // It could already throw an exception, and in that case we do not want a successful response status.
        val objectData = repositoriesManager.computeDelta(repositoryId, versionHash, filter)
        val contentType = if (plainText) ContentType.Text.Plain else VersionDeltaStream.CONTENT_TYPE
        respondBytesWriter(contentType) {
            this.useClosingWithoutCause {
                objectData.asStream().mapMany {
                    it.flatMapIterable { it.toList() }
                        .withSeparator("\n")
                        .ifEmpty(versionHash)
                        .withIndex()
                }.iterateSuspending {
                    if (it.index == 0) check(it.value == versionHash) { "First object should be the version" }
                    writeStringUtf8(it.value)
                }
            }
        }
    }

    private suspend fun ApplicationCall.respondDeltaAsObjectStreamV2(repositoryId: RepositoryId?, versionHash: String, filter: ObjectDeltaFilter) {
        val objectData = repositoriesManager.computeDelta(repositoryId, versionHash, filter)
        respondBytesWriter(VersionDeltaStreamV2.CONTENT_TYPE) {
            this.useClosingWithoutCause {
                VersionDeltaStreamV2.encodeVersionDeltaStreamV2(this, versionHash, objectData.asStream())
            }
        }
    }

    private suspend fun <R> runRead(body: () -> R): R {
        return repositoriesManager.getTransactionManager().runReadIO(body)
    }

    private suspend fun <R> runWrite(body: () -> R): R {
        return repositoriesManager.getTransactionManager().runWriteIO(body)
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

private fun IStream.Many<String>.withSeparator(separator: String) = flatMapIterable { listOf(separator, it) }.skip(1)

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

private fun RoutingContext.parameter(name: String): String {
    return call.parameter(name)
}

private fun ApplicationCall.parameter(name: String): String {
    return requireNotNull(parameters[name]) { "Unknown parameter '$name'" }
}

class InMemoryMemoizationPersistence(private val streamExecutor: IStreamExecutor) : IMemoizationPersistence {

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
                    streamExecutor.query { query.asStream(QueryEvaluationContext.EMPTY, input).exactlyOne() }
                }.upcast()
            }
        }
    }

    private fun QueryGraphDescriptor.deduplicate() = descriptorInstances.get(this) { this }

    private data class IndexCacheKey(val query: QueryGraphDescriptor, val input: Any?)

    private class IndexData<K, V>(val map: Map<K, List<IStepOutput<V>>>)

    private fun <K, V> IndexData<*, *>.upcast() = this as IndexData<K, V>
}

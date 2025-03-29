package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.buildUrl
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.kotlin.utils.DeprecationInfo
import org.modelix.kotlin.utils.WeakValueMap
import org.modelix.kotlin.utils.getOrPut
import org.modelix.kotlin.utils.runSynchronized
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.TreeId
import org.modelix.model.api.IBranch
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INode
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.LazyLoadingObjectGraph
import org.modelix.model.async.getAsyncStore
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.fullDiff
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.IAuthRequestHandler
import org.modelix.model.oauth.ModelixAuthClient
import org.modelix.model.oauth.OAuthConfig
import org.modelix.model.oauth.OAuthConfigBuilder
import org.modelix.model.oauth.TokenProvider
import org.modelix.model.oauth.TokenProviderAuthConfig
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.model.server.api.v2.ImmutableObjectsStream
import org.modelix.model.server.api.v2.ObjectHashAndSerializedObject
import org.modelix.model.server.api.v2.SerializedObject
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.api.v2.VersionDeltaStream
import org.modelix.model.server.api.v2.VersionDeltaStreamV2
import org.modelix.model.server.api.v2.asStream
import org.modelix.model.server.api.v2.toMap
import org.modelix.modelql.client.ModelQLClient
import org.modelix.modelql.core.IMonoStep
import org.modelix.streams.IExecutableStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VersionNotFoundException(val versionHash: String) : Exception("Version $versionHash not found")

class ModelClientV2(
    private val httpClient: HttpClient,
    val baseUrl: String,
    private var clientProvidedUserId: String?,
) : IModelClientV2, IModelClientV2Internal, Closable {
    private var clientId: Int = 0
    private var idGenerator: IIdGenerator = IdGeneratorDummy()
    private var serverProvidedUserId: String? = null

    private val objectGraphs = WeakValueMap<RepositoryId, ModelClientGraph>()
    private val repositoryConfigurations = HashMap<String, RepositoryConfig>()

    suspend fun init() {
        updateClientId()
        updateUserId()
    }

    private fun getObjectGraph(repository: RepositoryId?): ModelClientGraph {
        return runSynchronized(objectGraphs) {
            val repositoryId = repository ?: RepositoryId("")
            objectGraphs.getOrPut(repositoryId) {
                ModelClientGraph(this, repositoryId)
            }
        }
    }

    override fun getStore(repository: RepositoryId): IAsyncObjectStore {
        return ModelClientAsStore(this, repository)
    }

    private fun getRepository(graph: IObjectGraph): RepositoryId? {
        return when (graph) {
            is LazyLoadingObjectGraph -> {
                val store = graph.getAsyncStore()
                when (store) {
                    is ModelClientAsStore -> store.repositoryId
                    else -> error("Unknown store type: $graph")
                }
            }
            is ModelClientGraph -> graph.repositoryId
            else -> error("Unknown graph type: $graph")
        }
    }

    override suspend fun getServerId(): String {
        return httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegments("server-id")
            }
        }.bodyAsText()
    }

    private suspend fun updateClientId() {
        this.clientId = httpClient.post {
            url {
                takeFrom(baseUrl)
                appendPathSegments("generate-client-id")
            }
        }.bodyAsText().toInt()
        this.idGenerator = IdGenerator.getInstance(clientId)
    }

    suspend fun updateUserId() {
        serverProvidedUserId = httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegments("user-id")
            }
        }.bodyAsText()
    }

    /**
     * Version of [setClientProvidedUserId] with typo.
     */
    @Deprecated("Use setClientProvidedUserId, without a typo.", ReplaceWith("setClientProvidedUserId(userId)"))
    fun setClientProvideUserId(userId: String?) {
        clientProvidedUserId = userId
    }

    /**
     * Set or remove the client provided user ID.
     *
     * When the used ID is removed by passing null, [[getUserId]] might return the [[serverProvidedUserId]].
     *
     * @param userId A new user ID, or null to remove the old one.
     */
    fun setClientProvidedUserId(userId: String?) {
        setClientProvideUserId(userId)
    }

    override fun getClientId(): Int = clientId

    override fun getIdGenerator(): IIdGenerator = idGenerator

    override fun getUserId(): String? = clientProvidedUserId ?: serverProvidedUserId

    override suspend fun initRepository(repository: RepositoryId, useRoleIds: Boolean): IVersion {
        return initRepository(
            RepositoryConfig(
                legacyNameBasedRoles = !useRoleIds,
                legacyGlobalStorage = false,
                nodeIdType = RepositoryConfig.NodeIdType.STRING,
                primaryTreeType = RepositoryConfig.TreeType.PATRICIA_TRIE,
                modelId = TreeId.random().id,
                repositoryId = RepositoryId.random().id,
                repositoryName = repository.id,
                alternativeNames = emptySet(),
            ),
        )
    }

    override suspend fun initRepositoryWithLegacyStorage(repository: RepositoryId): IVersion {
        return initRepository(
            RepositoryConfig(
                legacyNameBasedRoles = false,
                legacyGlobalStorage = true,
                nodeIdType = RepositoryConfig.NodeIdType.STRING,
                primaryTreeType = RepositoryConfig.TreeType.PATRICIA_TRIE,
                modelId = TreeId.random().id,
                repositoryId = RepositoryId.random().id,
                repositoryName = repository.id,
                alternativeNames = emptySet(),
            ),
        )
    }

    override suspend fun initRepository(config: RepositoryConfig): IVersion {
        val repositoryId = RepositoryId(config.repositoryId)
        return httpClient.preparePost {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", repositoryId.id, "init")
            }
            useVersionStreamFormat()
        }.execute { response ->
            createVersion(repositoryId, null, response.readVersionDelta()).also {
                runSynchronized(repositoryConfigurations) {
                    for (alias in config.getAliases()) {
                        repositoryConfigurations[alias] = config
                    }
                }
            }
        }
    }

    override suspend fun getRepositoryConfig(alias: String): RepositoryConfig? {
        runSynchronized(repositoryConfigurations) {
            repositoryConfigurations[alias]?.let { return it }
        }
        return requestRepositoryConfig(alias)?.also { config ->
            runSynchronized(repositoryConfigurations) {
                repositoryConfigurations[alias] = config
            }
        }
    }

    private suspend fun requestRepositoryConfig(repositoryAlias: String): RepositoryConfig? {
        val response = httpClient.get {
            expectSuccess = false
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", repositoryAlias, "config")
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body<RepositoryConfig>()
    }

    override suspend fun listRepositories(): List<RepositoryId> {
        return httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegments("repositories")
            }
        }.bodyAsText().lines().filter { it.isNotEmpty() }.map { RepositoryId(it) }
    }

    override suspend fun deleteRepository(repository: RepositoryId): Boolean {
        try {
            return httpClient.post {
                url {
                    takeFrom(baseUrl)
                    appendPathSegmentsEncodingSlash("repositories", repository.id, "delete")
                }
            }.status == HttpStatusCode.NoContent
        } catch (ex: Exception) {
            LOG.error(ex) { ex.message }
            return false
        }
    }

    override suspend fun listBranches(repository: RepositoryId): List<BranchReference> {
        return httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", repository.id, "branches")
            }
        }.bodyAsText().lines().filter { it.isNotEmpty() }.map { repository.getBranchReference(it) }
    }

    override suspend fun deleteBranch(branch: BranchReference): Boolean {
        try {
            return httpClient.delete {
                url {
                    takeFrom(baseUrl)
                    appendPathSegmentsEncodingSlash(
                        "repositories",
                        branch.repositoryId.id,
                        "branches",
                        branch.branchName,
                    )
                }
            }.status == HttpStatusCode.NoContent
        } catch (ex: Exception) {
            LOG.error(ex) { ex.message }
            return false
        }
    }

    @Deprecated("repository ID is required for permission checks")
    @DeprecationInfo("3.7.0", "May be removed with the next major release. Also remove the endpoint from the model-server.")
    override suspend fun loadVersion(versionHash: String, baseVersion: IVersion?): IVersion {
        checkCreatedByThisClient(baseVersion, null)
        val repositoryIdFromBaseVersion = (baseVersion as? CLVersion)?.let { getRepository(it.graph) }
        if (repositoryIdFromBaseVersion != null) {
            return doLoadVersion(repositoryIdFromBaseVersion, versionHash, baseVersion)
        } else {
            // try finding the version in any repository
            for (repositoryId in listRepositories() + null) {
                try {
                    return doLoadVersion(repositoryId, versionHash, baseVersion)
                } catch (ex: ClientRequestException) {
                    when (ex.response.status) {
                        HttpStatusCode.NotFound -> {}
                        HttpStatusCode.Unauthorized -> {}
                        HttpStatusCode.Forbidden -> {}
                        else -> throw ex
                    }
                }
            }
            throw VersionNotFoundException(versionHash)
        }
    }

    override suspend fun loadVersion(
        repositoryId: RepositoryId,
        versionHash: String,
        baseVersion: IVersion?,
    ): IVersion {
        return doLoadVersion(repositoryId, versionHash, baseVersion)
    }

    override suspend fun lazyLoadVersion(
        repositoryId: RepositoryId,
        versionHash: String,
    ): IVersion {
        val graph = getObjectGraph(repositoryId)
        return graph.getStreamExecutor().querySuspending {
            graph.fromHashString(versionHash, CPVersion).resolve()
        }.let { CLVersion(it) }
    }

    override suspend fun lazyLoadVersion(branch: BranchReference): IVersion {
        return lazyLoadVersion(branch.repositoryId, pullHash(branch))
    }

    private suspend fun doLoadVersion(
        repositoryId: RepositoryId?,
        versionHash: String,
        baseVersion: IVersion?,
    ): IVersion {
        checkCreatedByThisClient(baseVersion, repositoryId)
        return httpClient.prepareGet {
            url {
                takeFrom(baseUrl)
                if (repositoryId == null) {
                    appendPathSegments("versions", versionHash)
                } else {
                    appendPathSegments("repositories", repositoryId.id, "versions", versionHash)
                }
                if (baseVersion != null) {
                    parameters["lastKnown"] = (baseVersion as CLVersion).getContentHash()
                }
            }
            useVersionStreamFormat()
        }.execute { response ->
            createVersion(repositoryId ?: RepositoryId(""), baseVersion as CLVersion?, response.readVersionDelta())
        }
    }

    override suspend fun getObjects(repository: RepositoryId, keys: Sequence<org.modelix.model.server.api.v2.ObjectHash>): Map<org.modelix.model.server.api.v2.ObjectHash, SerializedObject> {
        LOG.debug { "${clientId.toString(16)}.getObjects($repository)" }
        return httpClient.preparePost {
            url {
                takeFrom(baseUrl)
                appendPathSegments("repositories", repository.id, "objects", "getAll")
            }
            setBody(keys.joinToString("\n"))
        }.execute { response ->
            ImmutableObjectsStream.decode(response.bodyAsChannel())
        }
    }

    override suspend fun push(branch: BranchReference, version: IVersion, baseVersion: IVersion?): IVersion {
        LOG.debug { "${clientId.toString(16)}.push($branch, $version, $baseVersion)" }
        require(version is CLVersion)
        val delta = if (version.getContentHash() == baseVersion?.getContentHash()) {
            VersionDelta(version.getContentHash(), null)
        } else {
            require(baseVersion is CLVersion?)
            checkCreatedByThisClient(version, branch.repositoryId)
            checkCreatedByThisClient(baseVersion, branch.repositoryId)
            val objects = version.graph.getStreamExecutor().queryManyLater {
                version.fullDiff(baseVersion).map { it.getHashString() to it.data.serialize() }
            }
            // large HTTP requests and large Json objects don't scale well
            val lastChunk = pushObjects(branch.repositoryId, objects, returnLastChunk = true)
            VersionDelta(version.getContentHash(), null, objectsMap = lastChunk.toMap())
        }

        return httpClient.preparePost {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName)
            }
            useVersionStreamFormat()
            contentType(ContentType.Application.Json)
            setBody(delta)
        }.execute { response ->
            createVersion(branch.repositoryId, version, response.readVersionDelta())
        }
    }

    override suspend fun pushObjects(repository: RepositoryId, objects: Sequence<ObjectHashAndSerializedObject>) {
        pushObjects(
            repository,
            IExecutableStream.many(objects),
            false,
        )
    }

    /**
     * If the last chunk is smaller than #minBodySize, then the remaining objects are returned for inlining in the
     * main request.
     */
    private suspend fun pushObjects(
        repository: RepositoryId,
        objects: IExecutableStream.Many<ObjectHashAndSerializedObject>,
        returnLastChunk: Boolean,
    ): List<ObjectHashAndSerializedObject> {
        LOG.debug { "${clientId.toString(16)}.pushObjects($repository)" }
        val maxBodySize = 2 * 1024 * 1024
        val chunkContent = StringBuilder()
        val chunkEntries = ArrayList<ObjectHashAndSerializedObject>()

        suspend fun sendChunk() {
            httpClient.put {
                url {
                    takeFrom(baseUrl)
                    appendPathSegmentsEncodingSlash("repositories", repository.id, "objects")
                }
                contentType(ContentType.Text.Plain)
                setBody(chunkContent.toString())
            }
            chunkContent.clear()
            chunkEntries.clear()
        }

        objects.iterateSuspending { entry ->
            val entrySize = (if (chunkContent.isEmpty()) 0 else 1) + entry.first.length + 1 + entry.second.length
            if (chunkContent.length + entrySize > maxBodySize) {
                sendChunk()
            }
            if (chunkContent.isNotEmpty()) chunkContent.append('\n')
            chunkContent.append(entry.first).append('\n').append(entry.second)
            chunkEntries.add(entry)
        }
        if (chunkContent.isNotEmpty() && !returnLastChunk) {
            sendChunk()
        }
        return chunkEntries
    }

    override suspend fun pull(branch: BranchReference, lastKnownVersion: IVersion?, filter: ObjectDeltaFilter): IVersion {
        require(lastKnownVersion is CLVersion?)
        checkCreatedByThisClient(lastKnownVersion, branch.repositoryId)
        return httpClient.prepareGet {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName)
                if (lastKnownVersion != null) {
                    parameters["lastKnown"] = lastKnownVersion.getContentHash()
                }
                if (filter != ObjectDeltaFilter()) {
                    parameters["filter"] = filter.toJson()
                }
            }
            useVersionStreamFormat()
        }.execute { response ->
            val receivedVersion = createVersion(branch.repositoryId, lastKnownVersion, response.readVersionDelta())
            LOG.debug { "${clientId.toString(16)}.pull($branch, $lastKnownVersion) -> $receivedVersion" }
            receivedVersion
        }
    }

    override suspend fun pullIfExists(branch: BranchReference): IVersion? {
        return httpClient.prepareGet {
            expectSuccess = false
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName)
            }
            useVersionStreamFormat()
        }.execute { response ->
            val receivedVersion = when (response.status) {
                HttpStatusCode.NotFound -> null
                HttpStatusCode.OK -> createVersion(branch.repositoryId, null, response.readVersionDelta())
                else -> throw ResponseException(response, response.bodyAsText())
            }
            LOG.debug { "${clientId.toString(16)}.pullIfExists($branch) -> $receivedVersion" }
            receivedVersion
        }
    }

    override suspend fun pullHash(branch: BranchReference): String {
        val response = httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName, "hash")
            }
        }
        val receivedHash: String = response.body()
        return receivedHash
    }

    override suspend fun pollHash(branch: BranchReference, lastKnownVersion: IVersion?): String {
        checkCreatedByThisClient(lastKnownVersion, branch.repositoryId)
        return pollHash(branch, lastKnownVersion?.getContentHash())
    }

    override suspend fun pollHash(branch: BranchReference, lastKnownHash: String?): String {
        val response = httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName, "pollHash")
                if (lastKnownHash != null) {
                    parameters["lastKnown"] = lastKnownHash
                }
            }
        }
        val receivedHash: String = response.body()
        return receivedHash
    }

    override suspend fun poll(branch: BranchReference, lastKnownVersion: IVersion?): IVersion {
        require(lastKnownVersion is CLVersion?)
        checkCreatedByThisClient(lastKnownVersion, branch.repositoryId)
        LOG.debug { "${clientId.toString(16)}.poll($branch, $lastKnownVersion)" }
        return httpClient.prepareGet {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName, "poll")
                if (lastKnownVersion != null) {
                    parameters["lastKnown"] = lastKnownVersion.getContentHash()
                }
            }
            useVersionStreamFormat()
        }.execute { response ->
            val receivedVersion = createVersion(
                branch.repositoryId,
                lastKnownVersion,
                response.readVersionDelta(),
            )
            LOG.debug { "${clientId.toString(16)}.poll($branch, $lastKnownVersion) -> $receivedVersion" }
            receivedVersion
        }
    }

    override suspend fun <R> query(branch: BranchReference, body: (IMonoStep<INode>) -> IMonoStep<R>): R {
        val url = URLBuilder().apply {
            takeFrom(baseUrl)
            appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName, "query")
        }
        return ModelQLClient.builder().httpClient(httpClient).url(url.buildString()).build().query(body)
    }

    override suspend fun <R> query(repositoryId: RepositoryId, versionHash: String, body: (IMonoStep<INode>) -> IMonoStep<R>): R {
        val url = URLBuilder().apply {
            takeFrom(baseUrl)
            appendPathSegmentsEncodingSlash("repositories", repositoryId.id, "versions", versionHash, "query")
        }
        return ModelQLClient.builder().httpClient(httpClient).url(url.buildString()).build().query(body)
    }

    override fun close() {
        httpClient.close()
    }

    private suspend fun createVersion(repositoryAlias: RepositoryId, baseVersion: CLVersion?, delta: VersionDeltaStream): CLVersion {
        val config = getRepositoryConfig(repositoryAlias.id)
        val repositoryId = config?.let { RepositoryId(it.repositoryId) } ?: repositoryAlias
        val graph = getObjectGraph(repositoryId)
        return if (baseVersion != null && delta.versionHash == baseVersion.getContentHash()) {
            CLVersion(graph.withUnloadedHistory(baseVersion.obj))
        } else {
            val receivedObjects = delta.getObjectsAsFlow()
                .map { ObjectHash(it.first) to it.second }.toMap()
            val graph = getObjectGraph(repositoryId)
            CLVersion(graph.loadVersion(ObjectHash(delta.versionHash), receivedObjects))
        }
    }

    private fun checkCreatedByThisClient(version: IVersion?, repositoryId: RepositoryId?) {
//        if (version == null) return
//        version as CLVersion
//        require(storeForRepository.values.contains(version.asyncStore)) {
//            "Version was not created by this client. Use IModelClientV2.getStore. [version=$version]"
//        }
//        if (repositoryId != null) {
//            require(version.asyncStore == getStore(repositoryId)) {
//                "Version belongs to a different repository: $version"
//            }
//        }
    }

    companion object {
        private val LOG = KotlinLogging.logger {}
        fun builder(): ModelClientV2Builder = ModelClientV2PlatformSpecificBuilder()
    }
}

abstract class ModelClientV2Builder {
    protected var httpClient: HttpClient? = null
    protected var baseUrl: String = "https://localhost/model/v2"
    protected var authConfig: IAuthConfig? = null
    protected var userId: String? = null
    protected var connectTimeout: Duration = 1.seconds
    protected var requestTimeout: Duration = 30.seconds

    // 0 and 1 mean "disable retries"
    protected var retries: UInt = 3U

    fun build(): ModelClientV2 {
        return ModelClientV2(
            httpClient?.config { configureHttpClient(this) } ?: createHttpClient(),
            baseUrl,
            userId,
        )
    }

    fun url(url: String): ModelClientV2Builder {
        baseUrl = normalizeUrl(url)
        return this
    }

    fun client(httpClient: HttpClient): ModelClientV2Builder {
        this.httpClient = httpClient
        return this
    }

    fun authToken(provider: TokenProvider) = also {
        authConfig = TokenProviderAuthConfig(provider)
    }

    fun authConfig(config: IAuthConfig) = also {
        this.authConfig = config
    }

    fun authRequestBrowser(browser: ((url: String) -> Unit)?) = oauth {
        authRequestHandler(
            if (browser == null) {
                null
            } else {
                object : IAuthRequestHandler {
                    override fun browse(url: String) {
                        browser(url)
                    }
                }
            },
        )
    }

    fun enableOAuth() = oauth { }

    fun oauth(body: OAuthConfigBuilder.() -> Unit) = also {
        authConfig = OAuthConfigBuilder(authConfig as? OAuthConfig ?: legacyOAuthConfig()).apply(body).build()
    }

    /**
     * Handles the case when the model-server runs inside a kubernetes cluster with keycloak but doesn't provide the
     * OAuth endpoints in the 401 response headers.
     */
    private fun legacyOAuthConfig(): OAuthConfig? {
        // When the model server is reachable at https://example.org/model/,
        // Keycloak is expected to be reachable under https://example.org/realms/
        // See https://github.com/modelix/modelix.kubernetes/blob/60f7db6533c3fb82209b1a6abb6836923f585672/proxy/nginx.conf#L14
        // and https://github.com/modelix/modelix.kubernetes/blob/60f7db6533c3fb82209b1a6abb6836923f585672/proxy/nginx.conf#L41

        val normalizedModelUrl = buildUrl {
            takeFrom(baseUrl)
            if (pathSegments.lastOrNull() == "") pathSegments = pathSegments.dropLast(1)
            if (pathSegments.lastOrNull() == "v2") pathSegments = pathSegments.dropLast(1)
        }
        if (normalizedModelUrl.segments.lastOrNull() != "model") return null

        val oidcUrl = buildUrl {
            takeFrom(normalizedModelUrl)
            if (pathSegments.lastOrNull() == "model") pathSegments = pathSegments.dropLast(1)
            appendPathSegments("realms", "modelix", "protocol", "openid-connect")
        }
        val authUrl = buildUrl {
            takeFrom(oidcUrl)
            appendPathSegments("auth")
        }
        val tokenUrl = buildUrl {
            takeFrom(oidcUrl)
            appendPathSegments("token")
        }
        return OAuthConfig(
            clientId = "external-mps",
            scopes = setOf("email"),
            authorizationUrl = authUrl.toString(),
            tokenUrl = tokenUrl.toString(),
        )
    }

    fun userId(userId: String?): ModelClientV2Builder {
        this.userId = userId
        return this
    }

    fun connectTimeout(timeout: Duration): ModelClientV2Builder {
        this.connectTimeout = timeout
        return this
    }

    fun requestTimeout(timeout: Duration): ModelClientV2Builder {
        this.requestTimeout = timeout
        return this
    }

    /**
     * Configures the number of retries to be performed in case a request has failed.
     *
     * @param num 0 to disable the retry mechanism
     */
    fun retries(num: UInt): ModelClientV2Builder {
        this.retries = num
        return this
    }

    protected open fun configureHttpClient(config: HttpClientConfig<*>) {
        config.apply {
            expectSuccess = true
            followRedirects = false
            install(ContentNegotiation) {
                json()
            }
            install(HttpTimeout) {
                connectTimeoutMillis = connectTimeout.inWholeMilliseconds
                requestTimeoutMillis = requestTimeout.inWholeMilliseconds
            }
            if (retries > 1U) {
                install(HttpRequestRetry) {
                    retryOnExceptionOrServerErrors(maxRetries = retries.toInt())
                    exponentialDelay()
                    modifyRequest {
                        try {
                            //                    connectionStatus = ConnectionStatus.SERVER_ERROR
                            this.response?.call?.client?.coroutineContext?.let { CoroutineScope(it) }?.launch {
                                response?.let { println(it.bodyAsText()) }
                            }
                        } catch (e: Exception) {
                            LOG.debug(e) { "" }
                        }
                    }
                }
            }
            authConfig?.let { ModelixAuthClient.installAuth(this, it) }
        }
    }

    protected abstract fun createHttpClient(): HttpClient

    companion object {
        private val LOG = KotlinLogging.logger {}

        fun normalizeUrl(url: String): String {
            return buildUrl {
                takeFrom(url)
                if (pathSegments.lastOrNull() == "") pathSegments = pathSegments.dropLast(1)
                if (pathSegments.lastOrNull() != "v2") appendPathSegments("v2")
            }.toString()
        }
    }
}

expect class ModelClientV2PlatformSpecificBuilder() : ModelClientV2Builder {
    override fun createHttpClient(): HttpClient
}

fun VersionDelta.checkObjectHashes() {
    HashUtil.checkObjectHashes(objectsMap)
}
private fun URLBuilder.appendPathSegmentsEncodingSlash(vararg components: String): URLBuilder {
    return appendPathSegments(components.toList(), true)
}

fun VersionDelta.getAllObjects(): Map<String, String> = objectsMap + objects.associateBy { HashUtil.sha256(it) }

suspend fun HttpResponse.readVersionDelta(): VersionDeltaStream {
    val parsedContentType = contentType()
    return if (parsedContentType?.match(VersionDeltaStreamV2.CONTENT_TYPE) == true) {
        return readVersionDeltaStreamV2()
    } else if (parsedContentType?.match(VersionDeltaStream.CONTENT_TYPE) == true) {
        return readVersionDeltaStreamV1()
    } else {
        body<VersionDelta>().asStream()
    }
}

private suspend fun HttpResponse.readVersionDeltaStreamV1(): VersionDeltaStream {
    val content = bodyAsChannel()
    val versionHash = checkNotNull(content.readUTF8Line()) { "No objects received" }
    val versionObject = content.readUTF8Line()
    return if (versionObject == null) {
        VersionDeltaStream(versionHash, emptyFlow())
    } else {
        VersionDeltaStream(
            versionHash,
            flow {
                emit(versionHash to versionObject)
                while (true) {
                    val key = content.readUTF8Line() ?: break
                    val value = checkNotNull(content.readUTF8Line()) { "Object missing for hash $key" }
                    emit(key to value)
                }
            },
        )
    }
}

private suspend fun HttpResponse.readVersionDeltaStreamV2(): VersionDeltaStream {
    val content = bodyAsChannel()
    val decodeVersionDeltaStreamV2 = VersionDeltaStreamV2.decodeVersionDeltaStreamV2(content)
    return VersionDeltaStream(decodeVersionDeltaStreamV2.versionHash, decodeVersionDeltaStreamV2.hashesWithDeltaObject)
}

fun HttpRequestBuilder.useVersionStreamFormat() {
    headers[HttpHeaders.Accept] = VersionDeltaStreamV2.CONTENT_TYPE.toString()
    // Add CONTENT_TYPE_VERSION_DELTA_V1 so that newer clients cant talk with older servers.
    headers.append(HttpHeaders.Accept, VersionDeltaStream.CONTENT_TYPE.toString())
}

/**
 * Performs a write transaction on the root node of the given branch.
 *
 * [IModelClientV2.runWriteOnBranch] can be used access to the underlying branch is needed.
 */
suspend fun <T> IModelClientV2.runWrite(branchRef: BranchReference, body: (INode) -> T): T {
    return runWriteOnBranch(branchRef) {
        body(it.getRootNode())
    }
}

/**
 * Performs a write transaction on the root node of the given branch.
 *
 * [IModelClientV2.runWrite] can be used if access to the underlying branch is not needed.
 */
suspend fun <T> IModelClientV2.runWriteOnBranch(branchRef: BranchReference, body: (IBranch) -> T): T {
    val client = this
    val baseVersion = client.pullIfExists(branchRef)
        ?: branchRef.repositoryId.getBranchReference()
            .takeIf { it != branchRef }
            ?.let { client.pullIfExists(it) } // master branch
        ?: client.initRepository(branchRef.repositoryId)

    var result: T? = null
    val newVersion = baseVersion.runWrite(this) {
        result = body(it)
    }
    if (newVersion != null) {
        client.push(branchRef, newVersion, baseVersion)
    }
    return result as T
}

fun IVersion.runWrite(client: IModelClientV2, body: (IBranch) -> Unit): IVersion? {
    return runWrite(client.getIdGenerator(), client.getUserId(), body)
}

fun IVersion.runWrite(idGenerator: IIdGenerator, author: String?, body: (IBranch) -> Unit): IVersion? {
    val baseVersion = this
    val branch = OTBranch(TreePointer(baseVersion.getTree(), idGenerator), idGenerator)
    branch.computeWrite {
        body(branch)
    }
    val (ops, newTree) = branch.getPendingChanges()
    if (ops.isEmpty()) {
        return null
    }
    return CLVersion.createRegularVersion(
        id = idGenerator.generate(),
        author = author,
        tree = newTree as CLTree,
        baseVersion = baseVersion as CLVersion?,
        operations = ops.map { it.getOriginalOp() }.toTypedArray(),
    )
}

private fun String.ensureSuffix(suffix: String) = if (endsWith(suffix)) this else this + suffix

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.modelix.model.IVersion
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INode
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.computeDelta
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.HashUtil
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.modelql.client.ModelQLClient
import org.modelix.modelql.core.IMonoStep
import kotlin.time.Duration.Companion.seconds

class ModelClientV2(
    private val httpClient: HttpClient,
    val baseUrl: String,
) : IModelClientV2, Closable {
    private var clientId: Int = 0
    private var idGenerator: IIdGenerator = IdGeneratorDummy()
    private var userId: String? = null
    private val kvStore = MapBasedStore()
    val store = ObjectStoreCache(kvStore) // TODO the store will accumulate garbage

    suspend fun init() {
        updateClientId()
        updateUserId()
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
        userId = httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegments("user-id")
            }
        }.bodyAsText()
    }

    override fun getClientId(): Int = clientId

    override fun getIdGenerator(): IIdGenerator = idGenerator

    override fun getUserId(): String? = userId

    override suspend fun initRepository(repository: RepositoryId): IVersion {
        val response = httpClient.post {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", repository.id, "init")
            }
        }
        val delta = response.body<VersionDelta>()
        return createVersion(null, delta)
    }

    override suspend fun listRepositories(): List<RepositoryId> {
        return httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegments("repositories")
            }
        }.bodyAsText().lines().map { RepositoryId(it) }
    }

    override suspend fun listBranches(repository: RepositoryId): List<BranchReference> {
        return httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", repository.id, "branches")
            }
        }.bodyAsText().lines().map { repository.getBranchReference(it) }
    }

    @Deprecated("repository ID is required for permission checks")
    override suspend fun loadVersion(versionHash: String, baseVersion: IVersion?): IVersion {
        val response = httpClient.post {
            url {
                takeFrom(baseUrl)
                appendPathSegments("versions", versionHash)
                if (baseVersion != null) {
                    parameters["lastKnown"] = (baseVersion as CLVersion).getContentHash()
                }
            }
        }
        val delta = Json.decodeFromString<VersionDelta>(response.bodyAsText())
        return createVersion(baseVersion as CLVersion?, delta)
    }

    override suspend fun loadVersion(
        repositoryId: RepositoryId,
        versionHash: String,
        baseVersion: IVersion?,
    ): IVersion {
        val response = httpClient.post {
            url {
                takeFrom(baseUrl)
                appendPathSegments("repositories", repositoryId.id, "versions", versionHash)
                if (baseVersion != null) {
                    parameters["lastKnown"] = (baseVersion as CLVersion).getContentHash()
                }
            }
        }
        val delta = Json.decodeFromString<VersionDelta>(response.bodyAsText())
        return createVersion(baseVersion as CLVersion?, delta)
    }

    override suspend fun push(branch: BranchReference, version: IVersion, baseVersion: IVersion?): IVersion {
        LOG.debug { "${clientId.toString(16)}.push($branch, $version, $baseVersion)" }
        require(version is CLVersion)
        require(baseVersion is CLVersion?)
        version.write()
        val objects = version.computeDelta(baseVersion)
        val response = httpClient.post {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName)
            }
            contentType(ContentType.Application.Json)
            val body = VersionDelta(version.getContentHash(), null, objectsMap = objects)
            body.checkObjectHashes()
            setBody(body)
        }
        val mergedVersionDelta = response.body<VersionDelta>()
        return createVersion(version, mergedVersionDelta)
    }

    override suspend fun pull(branch: BranchReference, lastKnownVersion: IVersion?): IVersion {
        require(lastKnownVersion is CLVersion?)
        val response = httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName)
                if (lastKnownVersion != null) {
                    parameters["lastKnown"] = lastKnownVersion.hash
                }
            }
        }
        val receivedVersion = createVersion(lastKnownVersion, response.body())
        LOG.debug { "${clientId.toString(16)}.pull($branch, $lastKnownVersion) -> $receivedVersion" }
        return receivedVersion
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
        val response = httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName, "pollHash")
                if (lastKnownVersion != null) {
                    parameters["lastKnown"] = lastKnownVersion.getContentHash()
                }
            }
        }
        val receivedHash: String = response.body()
        return receivedHash
    }

    override suspend fun poll(branch: BranchReference, lastKnownVersion: IVersion?): IVersion {
        require(lastKnownVersion is CLVersion?)
        LOG.debug { "${clientId.toString(16)}.poll($branch, $lastKnownVersion)" }
        val response = httpClient.get {
            url {
                takeFrom(baseUrl)
                appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName, "poll")
                if (lastKnownVersion != null) {
                    parameters["lastKnown"] = lastKnownVersion.hash
                }
            }
        }
        val receivedVersion = createVersion(lastKnownVersion, response.body())
        LOG.debug { "${clientId.toString(16)}.poll($branch, $lastKnownVersion) -> $receivedVersion" }
        return receivedVersion
    }

    override suspend fun <R> query(branch: BranchReference, body: (IMonoStep<INode>) -> IMonoStep<R>): R {
        val url = URLBuilder().apply {
            takeFrom(baseUrl)
            appendPathSegmentsEncodingSlash("repositories", branch.repositoryId.id, "branches", branch.branchName, "query")
        }
        return ModelQLClient.builder().httpClient(httpClient).url(url.buildString()).build().query(body)
    }

    override suspend fun <R> query(repository: RepositoryId, versionHash: String, body: (IMonoStep<INode>) -> IMonoStep<R>): R {
        val url = URLBuilder().apply {
            takeFrom(baseUrl)
            appendPathSegmentsEncodingSlash("repositories", repository.id, "versions", versionHash, "query")
        }
        return ModelQLClient.builder().httpClient(httpClient).url(url.buildString()).build().query(body)
    }

    override fun close() {
        httpClient.close()
    }

    private fun createVersion(baseVersion: CLVersion?, delta: VersionDelta): CLVersion {
        return if (baseVersion == null) {
            CLVersion(
                delta.versionHash,
                store.also { it.keyValueStore.putAll(delta.getAllObjects()) },
            )
        } else if (delta.versionHash == baseVersion.hash) {
            baseVersion
        } else {
            require(baseVersion.store == store) { "baseVersion was not created by this client" }
            store.keyValueStore.putAll(delta.getAllObjects())
            CLVersion(
                delta.versionHash,
                baseVersion.store,
            )
        }
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
        fun builder(): ModelClientV2Builder = ModelClientV2PlatformSpecificBuilder()
    }
}

abstract class ModelClientV2Builder {
    protected var httpClient: HttpClient? = null
    protected var baseUrl: String = "https://localhost/model/v2"
    protected var authTokenProvider: (() -> String?)? = null

    fun build(): ModelClientV2 {
        return ModelClientV2(
            httpClient?.config { configureHttpClient(this) } ?: createHttpClient(),
            baseUrl,
        )
    }

    fun url(url: String): ModelClientV2Builder {
        baseUrl = url
        return this
    }

    fun client(httpClient: HttpClient): ModelClientV2Builder {
        this.httpClient = httpClient
        return this
    }

    fun authToken(provider: () -> String?): ModelClientV2Builder {
        authTokenProvider = provider
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
                connectTimeoutMillis = 1.seconds.inWholeMilliseconds
                requestTimeoutMillis = 30.seconds.inWholeMilliseconds
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(maxRetries = 3)
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
    }

    protected abstract fun createHttpClient(): HttpClient

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
    }
}

expect class ModelClientV2PlatformSpecificBuilder() : ModelClientV2Builder

fun VersionDelta.checkObjectHashes() {
    HashUtil.checkObjectHashes(objectsMap)
}
private fun URLBuilder.appendPathSegmentsEncodingSlash(vararg components: String): URLBuilder {
    return appendPathSegments(components.toList(), true)
}

fun VersionDelta.getAllObjects(): Map<String, String> = objectsMap + objects.associateBy { HashUtil.sha256(it) }

/**
 * Performs a write transaction on the root node of the given branch.
 */
suspend fun <T> IModelClientV2.runWrite(branchRef: BranchReference, body: (INode) -> T): T {
    val client = this
    val baseVersion = client.pull(branchRef, null)
    val branch = OTBranch(TreePointer(baseVersion.getTree(), client.getIdGenerator()), client.getIdGenerator(), (client as ModelClientV2).store)
    val result = branch.computeWrite {
        body(branch.getRootNode())
    }
    val (ops, newTree) = branch.getPendingChanges()
    val newVersion = CLVersion.createRegularVersion(
        id = client.getIdGenerator().generate(),
        author = client.getUserId(),
        tree = newTree as CLTree,
        baseVersion = baseVersion as CLVersion?,
        operations = ops.map { it.getOriginalOp() }.toTypedArray(),
    )
    client.push(branchRef, newVersion, baseVersion)
    return result
}

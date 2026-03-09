@file:OptIn(DelicateCoroutinesApi::class)

package org.modelix.model.client2

import INodeJS
import INodeReferenceJS
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.datetime.toJSDate
import kotlinx.datetime.toKotlinInstant
import org.modelix.datastructures.history.PaginationParameters
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.historyAsMutationParameters
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.TreeId
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.JSNodeConverter
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.mutable.DummyIdGenerator
import org.modelix.model.mutable.INodeIdGenerator
import org.modelix.model.mutable.ModelixIdGenerator
import org.modelix.model.mutable.asMutableReadonly
import org.modelix.model.mutable.asMutableThreadSafe
import org.modelix.model.mutable.load
import org.modelix.model.mutable.withAutoTransactions
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.server.api.BranchInfo
import org.modelix.mps.multiplatform.model.MPSIdGenerator
import org.modelix.streams.getSuspending
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Date
import kotlin.js.Promise
import kotlin.time.Duration.Companion.seconds

/**
 * Extracts the binding key from a request URL path.
 *
 * Binding keys have the form `{repositoryId}/{branchId}` and are extracted from the path pattern
 * `/repositories/{repositoryId}/branches/{branchId}`.
 *
 * Returns `null` for requests that are not associated with a specific branch (e.g. `/v2/server-id`).
 */
internal fun extractBindingKey(path: String): String? {
    val match = Regex("/repositories/([^/]+)/branches/([^/]+)").find(path) ?: return null
    return "${match.groupValues[1]}/${match.groupValues[2]}"
}

/** Configuration for [PerBindingAuthPlugin]. */
internal class PerBindingAuthConfig {
    var getToken: suspend (bindingKey: String?) -> String? = { null }
}

/**
 * Custom Ktor client plugin that adds `Authorization: Bearer <token>` headers per branch binding.
 *
 * For requests that target a specific repository/branch, the token is looked up by binding key.
 * For other requests (e.g. `/v2/server-id`), the global token is used as a fallback.
 * The token provider is invoked on **every** request, ensuring tokens are always fresh without
 * relying on Ktor's internal bearer-token cache.
 */
internal val PerBindingAuthPlugin = createClientPlugin("PerBindingAuth", ::PerBindingAuthConfig) {
    onRequest { request, _ ->
        val bindingKey = extractBindingKey(request.url.encodedPath)
        val token = pluginConfig.getToken(bindingKey)
        if (token != null) {
            request.headers[HttpHeaders.Authorization] = "Bearer $token"
        }
    }
}

/**
 * A [ModelClientV2Builder] that installs [PerBindingAuthPlugin] to support per-binding tokens.
 *
 * The [tokenSelector] is called for every HTTP request. It receives the binding key
 * (`{repositoryId}/{branchId}`) extracted from the request URL, or `null` for non-binding
 * requests. The implementation should return the appropriate bearer token or `null` for
 * unauthenticated requests.
 */
private class ClientJSInternalBuilder(
    private val tokenSelector: suspend (bindingKey: String?) -> String?,
) : ModelClientV2Builder() {
    override fun createHttpClient(): HttpClient {
        return HttpClient(Js) {
            configureHttpClient(this)
        }
    }

    override fun configureHttpClient(config: HttpClientConfig<*>) {
        // Install the standard plugins (JSON, timeout, retry, compression).
        // authConfig is intentionally kept null so ModelixAuthClient is NOT installed;
        // authentication is handled exclusively by PerBindingAuthPlugin below.
        super.configureHttpClient(config)
        config.install(PerBindingAuthPlugin) {
            getToken = tokenSelector
        }
    }
}


@JsExport
fun loadModelsFromJson(json: Array<String>): INodeJS {
    val branch = loadModelsFromJsonAsBranch(json)
    return branch.rootNode
}

/**
 * Load data from JSON strings into an in-memory branch.
 *
 * Each JSON string will be added as child of the [MutableModelTreeJs.rootNode] the created [MutableModelTreeJs].
 */
@JsExport
fun loadModelsFromJsonAsBranch(json: Array<String>): MutableModelTreeJs {
    val tree = IGenericModelTree.builder()
        .graph(createObjectStoreCache(MapBasedStore()).asObjectGraph())
        .withNodeReferenceIds()
        .build()
    val mutableTree = tree.asMutableThreadSafe(ModelixIdGenerator(IdGenerator.getInstance(1), tree.getId()))
    json.forEach { ModelData.fromJson(it).load(mutableTree) }
    return MutableModelTreeJsImpl(mutableTree.withAutoTransactions())
}

/**
 * Create a client connected to the model server.
 *
 * @param url URL to the V2 endpoint of the model server.
 * e.g., http://localhost:28102/v2
 * @param bearerTokenProvider Optional global token provider used for requests that are not
 *   associated with a specific model binding (e.g. `/v2/server-id`). Per-binding tokens take
 *   precedence over this provider and can be supplied via
 *   [ReplicatedModelParameters.tokenProvider] when calling [ClientJS.startReplicatedModels].
 */
@JsExport
fun connectClient(url: String, bearerTokenProvider: (() -> Promise<String?>)? = null): Promise<ClientJS> {
    return GlobalScope.promise {
        val bindingTokenProviders = mutableMapOf<String, suspend () -> String?>()
        val globalToken: (suspend () -> String?)? = bearerTokenProvider?.let { bp -> { bp().await() } }

        val clientBuilder = ClientJSInternalBuilder { bindingKey ->
            if (bindingKey != null) {
                bindingTokenProviders[bindingKey]?.invoke() ?: globalToken?.invoke()
            } else {
                globalToken?.invoke()
            }
        }.url(url)

        val client = clientBuilder.build()
        client.init()
        return@promise ClientJSImpl(client, bindingTokenProviders)
    }
}

@JsExport
sealed class IdSchemeJS() {
    object MPS : IdSchemeJS()
    object MODELIX : IdSchemeJS()
    object READONLY : IdSchemeJS()
}

@JsExport
data class VersionInformationWithModelTree(val version: VersionInformationJS, val tree: MutableModelTreeJs)

/**
 * JS-API for [ModelClientV2].
 * Can be used to perform operations on the model server and to read and write model data.
 *
 * The full version data of an [ModelClientV2] is not exposed because most parts model API are not exposed to JS yet.
 * See https://issues.modelix.org/issue/MODELIX-962
 */
@JsExport
interface ClientJS {

    /**
     * Provide a user ID to the model client.
     * The user ID is used to set the author of newly created versions.
     * See [VersionInformationJS.author]
     *
     * The server might reject a user ID from the client
     * when a configured authorization specifies another user ID.
     *
     * @param userId provided user ID
     */
    fun setClientProvidedUserId(userId: String)

    /**
     * Create a repository
     *
     * @param repositoryId ID of repository to be created
     * @param useRoleIds whether the tree created for the initial version uses UIDs or names
     *                   to access roles see [ITree.usesRoleIds]
     */
    fun initRepository(repositoryId: String, useRoleIds: Boolean = true): Promise<Unit>

    fun loadReadonlyVersion(repositoryId: String, versionHash: String): Promise<VersionInformationWithModelTree>

    fun getHistoryRange(repositoryId: String, headVersion: String, skip: Int, limit: Int): Promise<Array<VersionInformationJS>>
    fun getHistorySessions(repositoryId: String, headVersion: String, delaySeconds: Int, skip: Int, limit: Int): Promise<Array<HistoryIntervalJS>>
    fun getHistoryForFixedIntervals(repositoryId: String, headVersion: String, intervalDurationSeconds: Int, skip: Int, limit: Int): Promise<Array<HistoryIntervalJS>>
    fun getHistoryForProvidedIntervals(repositoryId: String, headVersion: String, splitAt: Array<Date>): Promise<Array<HistoryIntervalJS>>

    fun getHistoryRangeForBranch(repositoryId: String, branchId: String, skip: Int, limit: Int): Promise<Array<VersionInformationJS>>
    fun getHistorySessionsForBranch(repositoryId: String, branchId: String, delaySeconds: Int, skip: Int, limit: Int): Promise<Array<HistoryIntervalJS>>
    fun getHistoryForFixedIntervalsForBranch(repositoryId: String, branchId: String, intervalDurationSeconds: Int, skip: Int, limit: Int): Promise<Array<HistoryIntervalJS>>
    fun getHistoryForProvidedIntervalsForBranch(repositoryId: String, branchId: String, splitAt: Array<Date>): Promise<Array<HistoryIntervalJS>>

    fun revertTo(repositoryId: String, branchId: String, targetVersionHash: String): Promise<String>

    fun diffAsMutationParameters(repositoryId: String, newVersion: String, oldVersion: String): Promise<Array<MutationParametersJS>>

    /**
     * Fetch existing branches for a given repository from the model server.
     *
     * @param repositoryId Repository ID to fetch branches from.
     */
    fun fetchBranches(repositoryId: String): Promise<Array<String>>

    /**
     * Fetch existing branches for a given repository from the model server.
     *
     * @param repositoryId Repository ID to fetch branches from.
     */
    fun fetchBranchesWithHashes(
        repositoryId: String,
    ): Promise<Array<BranchInfo>>

    /**
     * Create a new branch in the given repository on the model server.
     * The new branch will point to the given version.
     * If the branch already exists, the promise will be rejected.
     * See also [deleteBranch] to delete an existing branch.
     * @param repositoryId Repository ID to create the branch in.
     * @param branchId ID of the new branch to create.
     * @param versionHash Hash of the version the new branch should point to.
     */
    fun createBranch(repositoryId: String, branchId: String, versionHash: String): Promise<Unit>

    /**
     * Delete an existing branch from the given repository on the model server.
     * If the branch does not exist, the promise will be resolved with `false`.
     * See also [createBranch] to create a new branch.
     *
     * @param repositoryId Repository ID to delete the branch from.
     * @param branchId ID of the branch to delete.
     * @return Promise that resolves to `true` if the branch existed and was deleted, else `false`.
     */
    fun deleteBranch(
        repositoryId: String,
        branchId: String,
    ): Promise<Boolean>

    /**
     * Fetch existing repositories from the model server.
     */
    fun fetchRepositories(): Promise<Array<String>>

    /**
     * Starts a replicated model for a given branch.
     * [ReplicatedModelJS.getBranch] can then be used to access the replicated branch.
     *
     * @param repositoryId Repository ID of the branch to replicate.
     * @param branchId ID of the branch to replicate.
     */
    fun startReplicatedModel(repositoryId: String, branchId: String, idScheme: IdSchemeJS): Promise<ReplicatedModelJS>

    fun startReplicatedModels(parameters: Array<ReplicatedModelParameters>): Promise<ReplicatedModelJS>

    /**
     * Dispose the client by closing the underlying connection to the model server.
     */
    fun dispose()
}

@JsExport
class ReplicatedModelParameters(
    val repositoryId: String,
    val branchId: String,
    val idScheme: IdSchemeJS,
    /**
     * Optional bearer-token provider for this specific binding.
     *
     * When provided, this callback is invoked on **every individual HTTP request**
     * (each GET, POST, etc.) that targets the `repositoryId`/`branchId` combination —
     * not just once per [startReplicatedModels] call. This ensures a fresh token is used
     * for each request without relying on Ktor's internal bearer-token cache. It takes
     * precedence over the global token provider supplied to [connectClient].
     *
     * Pass `null` (or omit this parameter) to use the global token from [connectClient].
     */
    val tokenProvider: (() -> Promise<String?>)? = null,
)

internal class ClientJSImpl(
    private val modelClient: ModelClientV2,
    /**
     * Mutable map shared with [connectClient]'s [PerBindingAuthPlugin] token selector.
     * Entries are added here when [startReplicatedModels] is called with per-binding token
     * providers, making the plugin immediately route the right token for each branch's requests.
     */
    private val bindingTokenProviders: MutableMap<String, suspend () -> String?> = mutableMapOf(),
) : ClientJS {

    override fun setClientProvidedUserId(userId: String) {
        modelClient.setClientProvidedUserId(userId)
    }

    override fun initRepository(repositoryId: String, useRoleIds: Boolean): Promise<Unit> {
        return GlobalScope.promise {
            modelClient.initRepository(RepositoryId(repositoryId), useRoleIds)
            return@promise
        }
    }

    override fun fetchRepositories(): Promise<Array<String>> {
        return GlobalScope.promise {
            return@promise modelClient.listRepositories()
                .map { it.id }.toTypedArray()
        }
    }

    override fun fetchBranches(
        repositoryId: String,
    ): Promise<Array<String>> {
        return GlobalScope.promise {
            val repositoryIdObject = RepositoryId(repositoryId)
            return@promise modelClient.listBranches(repositoryIdObject)
                .map { it.branchName }.toTypedArray()
        }
    }

    override fun fetchBranchesWithHashes(
        repositoryId: String,
    ): Promise<Array<BranchInfo>> = GlobalScope.promise {
        return@promise modelClient.listBranchesWithHashes(RepositoryId(repositoryId)).toTypedArray()
    }

    override fun createBranch(
        repositoryId: String,
        branchId: String,
        versionHash: String,
    ): Promise<Unit> {
        return GlobalScope.promise {
            RepositoryId(repositoryId).let { repositoryId ->
                val branchReference = repositoryId.getBranchReference(branchId)

                val version = modelClient.lazyLoadVersion(repositoryId, versionHash)
                modelClient.push(branchReference, version, version, force = false, failIfExists = true)
                    ?: throw CancellationException("Branch $branchId already exists in repository $repositoryId")
            }
        }
    }

    override fun deleteBranch(
        repositoryId: String,
        branchId: String,
    ): Promise<Boolean> {
        return GlobalScope.promise {
            RepositoryId(repositoryId).let { repositoryId ->
                val branchReference = repositoryId.getBranchReference(branchId)
                return@promise modelClient.deleteBranch(branchReference)
            }
        }
    }

    override fun startReplicatedModel(
        repositoryId: String,
        branchId: String,
        idScheme: IdSchemeJS,
    ): Promise<ReplicatedModelJS> {
        return startReplicatedModels(arrayOf(ReplicatedModelParameters(repositoryId, branchId, idScheme)))
    }

    override fun startReplicatedModels(parameters: Array<ReplicatedModelParameters>): Promise<ReplicatedModelJS> {
        return GlobalScope.promise {
            val models = parameters.map { parameters ->
                // Register a per-binding token provider if one was supplied with the parameters.
                // The shared bindingTokenProviders map is read by PerBindingAuthPlugin on every
                // HTTP request, so the registration takes effect immediately.
                parameters.tokenProvider?.let { jsProvider ->
                    val key = "${parameters.repositoryId}/${parameters.branchId}"
                    bindingTokenProviders[key] = { jsProvider().await() }
                }

                val modelClient = modelClient
                val branchReference = RepositoryId(parameters.repositoryId).getBranchReference(parameters.branchId)
                val idGenerator: (TreeId) -> INodeIdGenerator<INodeReference> = when (parameters.idScheme) {
                    IdSchemeJS.READONLY -> { treeId -> DummyIdGenerator() }
                    IdSchemeJS.MODELIX -> { treeId -> ModelixIdGenerator(modelClient.getIdGenerator(), treeId) }
                    IdSchemeJS.MPS -> { treeId -> MPSIdGenerator(modelClient.getIdGenerator(), treeId) }
                }
                modelClient.getReplicatedModel(branchReference, idGenerator).also { it.start() }
            }
            ReplicatedModelJSImpl(models)
        }
    }

    override fun loadReadonlyVersion(repositoryId: String, versionHash: String): Promise<VersionInformationWithModelTree> {
        return GlobalScope.promise {
            val version = modelClient.loadVersion(RepositoryId(repositoryId), versionHash, null)
            VersionInformationWithModelTree(
                VersionInformationJS(
                    version.getAuthor(),
                    version.getTimestamp()?.toJSDate(),
                    version.getContentHash(),
                ),
                MutableModelTreeJsImpl(version.getModelTree().asMutableReadonly()),
            )
        }
    }

    override fun getHistoryRangeForBranch(repositoryId: String, branchId: String, skip: Int, limit: Int) =
        GlobalScope.promise { modelClient.pullHash(RepositoryId(repositoryId).getBranchReference(branchId)) }
            .then { getHistoryRange(repositoryId, it, skip, limit) }
            .then { it }

    override fun getHistoryRange(repositoryId: String, headVersion: String, skip: Int, limit: Int) =
        GlobalScope.promise {
            modelClient.queryHistory(
                RepositoryId(repositoryId),
                ObjectHash(headVersion),
            ).range(
                pagination = PaginationParameters(skip, limit),
            )
                .map {
                    VersionInformationJS(
                        it.author,
                        it.time.toJSDate(),
                        it.versionHash.toString(),
                    )
                }
                .toTypedArray()
        }

    override fun getHistorySessions(
        repositoryId: String,
        headVersion: String,
        delaySeconds: Int,
        skip: Int,
        limit: Int,
    ): Promise<Array<HistoryIntervalJS>> = GlobalScope.promise {
        modelClient.queryHistory(
            RepositoryId(repositoryId),
            ObjectHash(headVersion),
        ).sessions(
            delay = delaySeconds.seconds,
            pagination = PaginationParameters(skip, limit),
        ).map { it.toJS() }.toTypedArray()
    }

    override fun getHistoryForFixedIntervals(
        repositoryId: String,
        headVersion: String,
        intervalDurationSeconds: Int,
        skip: Int,
        limit: Int,
    ): Promise<Array<HistoryIntervalJS>> = GlobalScope.promise {
        modelClient.queryHistory(
            RepositoryId(repositoryId),
            ObjectHash(headVersion),
        ).intervals(
            interval = intervalDurationSeconds.seconds,
            pagination = PaginationParameters(skip, limit),
        ).map { it.toJS() }.toTypedArray()
    }

    override fun getHistoryForProvidedIntervals(
        repositoryId: String,
        headVersion: String,
        splitAt: Array<Date>,
    ): Promise<Array<HistoryIntervalJS>> = GlobalScope.promise {
        modelClient.queryHistory(
            RepositoryId(repositoryId),
            ObjectHash(headVersion),
        ).splitAt(
            splitPoints = splitAt.map { it.toKotlinInstant() },
        ).map { it.toJS() }.toTypedArray()
    }

    override fun getHistorySessionsForBranch(
        repositoryId: String,
        branchId: String,
        delaySeconds: Int,
        skip: Int,
        limit: Int,
    ): Promise<Array<HistoryIntervalJS>> =
        GlobalScope.promise { modelClient.pullHash(RepositoryId(repositoryId).getBranchReference(branchId)) }
            .then { getHistorySessions(repositoryId, it, delaySeconds, skip, limit) }
            .then { it }

    override fun getHistoryForFixedIntervalsForBranch(
        repositoryId: String,
        branchId: String,
        intervalDurationSeconds: Int,
        skip: Int,
        limit: Int,
    ): Promise<Array<HistoryIntervalJS>> =
        GlobalScope.promise { modelClient.pullHash(RepositoryId(repositoryId).getBranchReference(branchId)) }
            .then { getHistoryForFixedIntervals(repositoryId, it, intervalDurationSeconds, skip, limit) }
            .then { it }

    override fun getHistoryForProvidedIntervalsForBranch(
        repositoryId: String,
        branchId: String,
        splitAt: Array<Date>,
    ): Promise<Array<HistoryIntervalJS>> =
        GlobalScope.promise { modelClient.pullHash(RepositoryId(repositoryId).getBranchReference(branchId)) }
            .then { getHistoryForProvidedIntervals(repositoryId, it, splitAt) }
            .then { it }

    override fun revertTo(
        repositoryId: String,
        branchId: String,
        targetVersionHash: String,
    ): Promise<String> = GlobalScope.promise {
        modelClient.revertTo(
            branch = RepositoryId(repositoryId).getBranchReference(branchId),
            versionHash = ObjectHash(targetVersionHash),
        ).toString()
    }

    override fun dispose() {
        modelClient.close()
    }

    override fun diffAsMutationParameters(
        repositoryId: String,
        newVersion: String,
        oldVersion: String,
    ): Promise<Array<MutationParametersJS>> = GlobalScope.promise {
        val version = modelClient.lazyLoadVersion(RepositoryId(repositoryId), newVersion) as CLVersion
        @OptIn(DelicateModelixApi::class)
        version.historyAsMutationParameters(ObjectHash(oldVersion))
            .map { it.toJS() }
            .toList()
            .getSuspending(version.graph)
            .toTypedArray()
    }
}

typealias ChangeHandler = (ChangeJS) -> Unit

/**
 * JS-API for [MutableModelTree].
 * Can be used to read and write model data.
 *
 * The full version data of an [ModelClientV2] is not exposed because most parts model API are not exposed to JS yet.
 * See https://issues.modelix.org/issue/MODELIX-962
 */
@JsExport
interface MutableModelTreeJs {
    /**
     * Get root in the branch.
     * The root node can be used to read and write model data.
     */
    val rootNode: INodeJS

    fun getRootNodes(): Array<INodeJS>

    /**
     * Find the node for the given reference in the branch.
     * Returns `null` if the node cannot be found.
     *
     * @param reference the reference to the node.
     */
    fun resolveNode(reference: INodeReferenceJS): INodeJS?

    /**
     * Add a change handler to the branch.
     * The change handler will be called when data on the branch changes.
     */
    fun addListener(handler: ChangeHandler)

    /**
     * Remove a change handler from the branch.
     */
    fun removeListener(handler: ChangeHandler)
}

internal fun toNodeJs(rootNode: INode) = JSNodeConverter.nodeToJs(rootNode).unsafeCast<INodeJS>()

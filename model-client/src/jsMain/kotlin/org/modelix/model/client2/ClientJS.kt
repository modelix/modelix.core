@file:OptIn(DelicateCoroutinesApi::class)

package org.modelix.model.client2

import INodeJS
import INodeReferenceJS
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.datetime.toJSDate
import org.modelix.datastructures.model.IGenericModelTree
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
import org.modelix.model.mutable.VersionedModelTree
import org.modelix.model.mutable.asMutableThreadSafe
import org.modelix.model.mutable.load
import org.modelix.model.mutable.withAutoTransactions
import org.modelix.model.persistent.MapBasedStore
import org.modelix.mps.multiplatform.model.MPSIdGenerator
import kotlin.js.Promise

/**
 * Same as [loadModelsFromJsonAsBranch] but directly returns the [MutableModelTreeJs.rootNode] of the created branch.
 */
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
 */
@JsExport
fun connectClient(url: String, bearerTokenProvider: (() -> Promise<String?>)? = null): Promise<ClientJS> {
    return GlobalScope.promise {
        val clientBuilder = ModelClientV2.builder()
            .url(url)

        if (bearerTokenProvider != null) {
            clientBuilder.authToken { bearerTokenProvider().await() }
        }
        val client = clientBuilder.build()
        client.init()
        return@promise ClientJSImpl(client)
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

    /**
     * Fetch existing branches for a given repository from the model server.
     *
     * @param repositoryId Repository ID to fetch branches from.
     */
    fun fetchBranches(repositoryId: String): Promise<Array<String>>

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

    /**
     * Dispose the client by closing the underlying connection to the model server.
     */
    fun dispose()
}

internal class ClientJSImpl(private val modelClient: ModelClientV2) : ClientJS {

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

    override fun startReplicatedModel(
        repositoryId: String,
        branchId: String,
        idScheme: IdSchemeJS,
    ): Promise<ReplicatedModelJS> = startReplicatedModelWithIdGenerator(
        repositoryId,
        branchId,
        when (idScheme) {
            IdSchemeJS.READONLY -> { treeId -> DummyIdGenerator() }
            IdSchemeJS.MODELIX -> { treeId -> ModelixIdGenerator(modelClient.getIdGenerator(), treeId) }
            IdSchemeJS.MPS -> { treeId -> MPSIdGenerator(modelClient.getIdGenerator(), treeId) }
        },
    )

    private fun startReplicatedModelWithIdGenerator(repositoryId: String, branchId: String, idGenerator: (TreeId) -> INodeIdGenerator<INodeReference>): Promise<ReplicatedModelJS> {
        val modelClient = modelClient
        val branchReference = RepositoryId(repositoryId).getBranchReference(branchId)
        val model: ReplicatedModel = modelClient.getReplicatedModel(branchReference, idGenerator)
        return GlobalScope.promise {
            model.start()
            return@promise ReplicatedModelJSImpl(model)
        }
    }

    override fun loadReadonlyVersion(repositoryId: String, versionHash: String): Promise<VersionInformationWithModelTree> {
        return GlobalScope.promise {
            val version = modelClient.loadVersion(RepositoryId(repositoryId), versionHash, null)
            VersionInformationWithModelTree(
                VersionInformationJS(
                    (version as CLVersion).author,
                    version.getTimestamp()?.toJSDate(),
                    version.getContentHash(),
                ),
                MutableModelTreeJsImpl(VersionedModelTree(version).withAutoTransactions()),
            )
        }
    }

    override fun dispose() {
        modelClient.close()
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

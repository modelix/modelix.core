package org.modelix.mps.sync

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.ktor.client.HttpClient
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.neu.ITreeToSTreeTransformer
import java.net.ConnectException
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl : SyncService {

    private var log: Logger = logger<SyncServiceImpl>()

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    public var clientBindingMap: MutableMap<ModelClientV2, MutableList<BindingImpl>> = mutableMapOf()

    fun getAllClients(): List<IModelClientV2> = clientBindingMap.keys.toList()

    // todo add afterActivate to allow async refresh
    suspend fun connectToModelServer(
        httpClient: HttpClient?,
        serverURL: URL,
        jwt: String?,
    ): ModelClientV2 {
        // avoid reconnect to existing server
        val client = clientBindingMap.keys.find { it.baseUrl == serverURL.toString() }
        client?.let {
            log.info("Using already existing connection to $serverURL")
            return it
        }

        // TODO: use JWT here
        val modelClientV2: ModelClientV2 = ModelClientV2.builder()
            .also { if (httpClient != null) it.client(httpClient) }
            .url(serverURL.toString()).build()

        try {
            log.info("Connecting to $serverURL")
            modelClientV2.init()
//                modelClientV2.initRepository(RepositoryId("lolwat"+(0..10).random().toString()))
        } catch (e: ConnectException) {
            log.warn("Unable to connect: ${e.message} / ${e.cause}")
            throw e
        }
        log.info("Connection to $serverURL successful")
        clientBindingMap[modelClientV2] = mutableListOf<BindingImpl>()
        return modelClientV2
    }

    fun disconnectModelServer(client: ModelClientV2) {
        clientBindingMap[client]?.forEach { it.deactivate() }
        clientBindingMap.remove(client)
        client.close()
    }

    override suspend fun bindModel(
        modelClientV2: ModelClientV2,
        branchReference: BranchReference,
        modelName: String,
        model: INode,
        project: MPSProject,
        afterActivate: (() -> Unit)?,
    ): IBinding {
        lateinit var bindingImpl: BindingImpl

        // set up a client, a replicated model and an implementation of a binding (to MPS)
        log.info("Binding model $modelName")
        val replicatedModel: ReplicatedModel = modelClientV2.getReplicatedModel(branchReference)
        replicatedModel.start()

        // 🚧🏗️👷👷‍♂️ WARNING Construction area 🚧🚧🚧

        ITreeToSTreeTransformer(replicatedModel, project).transform(model)
        bindingImpl = BindingImpl(replicatedModel, modelName, project)
        // trigger callback after activation
        afterActivate?.invoke()

        // remember the new binding
        clientBindingMap[modelClientV2]!!.add(bindingImpl)

        return bindingImpl
    }

    fun dispose() {
        // cancel all running coroutines
        coroutineScope.cancel()
        // dispose the bindings
        clientBindingMap.values.forEach { it: MutableList<BindingImpl> -> it.forEach { it2 -> it2.deactivate() } }
        // dispose the clients
        clientBindingMap.keys.forEach { it: ModelClientV2 -> it.close() }
    }

    suspend fun removeServerConnectionAndAllBindings(serverURL: URL) {
        val foundClient = clientBindingMap.keys.find { it.baseUrl.equals(serverURL) } ?: return

        // TODO do a proper binding removal here?
        clientBindingMap[foundClient]!!.forEach { it.deactivate() }

        foundClient.close()

        clientBindingMap.remove(foundClient)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class BindingImpl(val replicatedModel: ReplicatedModel, modelName: String, mpsPproject: MPSProject) : IBinding {

    val branch: IBranch

    init {
        branch = replicatedModel.getBranch()

        // TODO use the modelName here...

        // todo: test if there is a module already
        // if (!mpsPproject.projectModels.any { it.name.equals(branch.getId()) }) {
        // todo: create model
        // }
        setupBinding()
    }

    private fun setupBinding() {
        // TODO: Re-implement features here which are currently available in the mps sync plugin

        // listen to changes on the branch in the replicatedModel (model-server side) ...
        branch.addListener(object : IBranchListener {
            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                // TODO fixme, because if we make changes to the model-server from MPS, then we have to avoid getting into a self-calling infinite loop....
                // ... and write to MPS
            }
        })

        // this includes setting up of syncing
        // * to MPS from the replicated model
        // * to the replicated model from MPS

        // replicatedModel.getBranch().getRootNode()

        // todo: check if replication and connection work and raise exception otherwise
    }

    override fun deactivate(callback: Runnable?) {
        replicatedModel.dispose()
    }

    override fun activate(callback: Runnable?) {
        TODO("Not yet implemented")
    }

    override suspend fun flush() {
        TODO("Not yet implemented")
    }
}

package org.modelix.mps.sync

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.runBlocking
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.binding.IBinding
import java.net.ConnectException
import java.net.URL

class SyncServiceImpl : SyncService {

    private var log: Logger = logger<SyncServiceImpl>()

    //    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private var clientBindingMap: MutableMap<ModelClientV2, BindingImpl> = mutableMapOf()

    override suspend fun bindRepository(
        serverURL: URL,
        branchReference: BranchReference,
        jwt: String,
        project: MPSProject,
        afterActivate: () -> Unit,
    ): IBinding {
        log.info("Connecting to $serverURL")

        lateinit var bindingImpl: BindingImpl

        // set up a client, a replicated model and an implementation of a binding (to MPS)
        // TODO: use JWT here
        val modelClientV2: ModelClientV2 = ModelClientV2.builder().url(serverURL.toString()).build()
        runBlocking {
            try {
                modelClientV2.init()

                val replicatedModel: ReplicatedModel = modelClientV2.getReplicatedModel(branchReference)
                replicatedModel.start()

                bindingImpl = BindingImpl(replicatedModel, project)

                log.info("Connection successful")
            } catch (e: ConnectException) {
                log.warn("Unable to connect: ${e.message} / ${e.cause}")
                throw e
            }
        }
        afterActivate()
        // TODO: Re-implement features here which are currently available in the mps sync plugin

        // this includes setting up of syncing
        // * to MPS from the replicated model
        // * to the replicated model from MPS

        // replicatedModel.getBranch().getRootNode()

        // todo: check if replication and connection work and raise exception otherwise
        clientBindingMap.put(modelClientV2, bindingImpl)
        return bindingImpl
    }

    fun dispose() {
        // cancel all running coroutines
//        coroutineScope.cancel()
        // dispose the bindings
        clientBindingMap.values.forEach { it: IBinding -> it.deactivate() }
        // dispose the clients
        clientBindingMap.keys.forEach { it: ModelClientV2 -> it.close() }
    }

    suspend fun unbindRepository(serverURL: URL) {
        val foundBinding = clientBindingMap.keys.find { it.baseUrl.equals(serverURL) } ?: return
        foundBinding.close()
        clientBindingMap.remove(foundBinding)
    }
}

class BindingImpl(val replicatedModel: ReplicatedModel, mpsPproject: MPSProject) : IBinding {

    val branch: IBranch

    init {
        branch = replicatedModel.getBranch()

        // todo: test if there is a module already
        // if (!mpsPproject.projectModels.any { it.name.equals(branch.getId()) }) {
        // todo: create model
        // }

        // listen to changes on the branch in the replicatedModel (model-server side) ...
        branch.addListener(object : IBranchListener {
            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                TODO("Not yet implemented")
                // ... and write to MPS
            }
        })
    }

    override fun deactivate(callback: Runnable?) {
        replicatedModel.dispose()
    }

    override fun activate(callback: Runnable?) {
        TODO("Not yet implemented")
    }
}

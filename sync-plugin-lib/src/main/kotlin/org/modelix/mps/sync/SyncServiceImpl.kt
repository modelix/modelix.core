package org.modelix.mps.sync

import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.binding.Binding
import java.net.URL

class SyncServiceImpl : SyncService {

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    override fun bindRepository(
        serverURL: URL,
        branchReference: BranchReference,
        jwt: String,
        mpsPproject: MPSProject,
        afterActivate: () -> Unit,
    ): Binding {
        // set up a client, a replicated model and an implementation of a binding (to MPS)
        val modelClientV2: ModelClientV2 = ModelClientV2.builder().url(serverURL.toString()).authToken { jwt }.build()
        val replicatedModel: ReplicatedModel = modelClientV2.getReplicatedModel(branchReference)
        val bindingImpl = BindingImpl(replicatedModel, mpsPproject)

        coroutineScope.launch {
            replicatedModel.start()
        }

        // TODO: Re-implement features here which are currently available in the mps sync plugin

        // this includes setting up of syncing
        // * to MPS from the replicated model
        // * to the replicated model from MPS

        // replicatedModel.getBranch().getRootNode()

        // todo: check if replication and connection work and raise exception otherwise
        return bindingImpl
    }

    fun dispose() {
        coroutineScope.cancel()
    }
}

class BindingImpl(replicatedModel: ReplicatedModel, mpsPproject: MPSProject) : Binding {

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

    override fun deactivate() {
//        this.replicatedModel.dispose()
    }

    override fun activate() {
        TODO("Not yet implemented")
    }
}

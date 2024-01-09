package org.modelix.mps.sync

import jetbrains.mps.project.MPSProject
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface SyncService {

    suspend fun bindModel(
        client: ModelClientV2,
        branchReference: BranchReference,
        model: INode,
        callback: (() -> Unit)?,
    ): IBinding

    suspend fun connectModelServer(serverURL: URL, jwt: String, callback: (() -> Unit)?): ModelClientV2

    fun disconnectModelServer(client: ModelClientV2, callback: (() -> Unit)?): Unit

    fun setActiveMpsProject(mpsProject: MPSProject)

    fun getModelBindings(): Set<ModelBinding>

    fun getModuleBindings(): Set<ModuleBinding>

    fun dispose()
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface IBinding {

    fun activate(callback: Runnable? = null)
    fun deactivate(callback: Runnable? = null)
}

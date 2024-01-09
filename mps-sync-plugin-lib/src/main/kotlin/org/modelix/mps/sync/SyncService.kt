package org.modelix.mps.sync

import jetbrains.mps.project.MPSProject
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.bindings.ModuleBinding

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface SyncService {

    suspend fun bindModel(
        client: ModelClientV2,
        branchReference: BranchReference,
        model: INode,
        afterActivate: (() -> Unit)?,
    ): IBinding

    fun setActiveMpsProject(mpsProject: MPSProject)

    fun getModelBindings(): Set<ModelBinding>

    fun getModuleBindings(): Set<ModuleBinding>
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface IBinding {

    fun activate(callback: Runnable? = null)
    fun deactivate(callback: Runnable? = null)
}

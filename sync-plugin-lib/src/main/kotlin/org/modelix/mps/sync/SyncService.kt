package org.modelix.mps.sync

import jetbrains.mps.project.MPSProject
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference

interface SyncService {

    suspend fun bindModel(
        client: ModelClientV2,
        branchReference: BranchReference,
        modelName: String,
        model: INode,
        targetProject: MPSProject,
        afterActivate: (() -> Unit)?,
    ): IBinding
}

interface IBinding {

    fun activate(callback: Runnable? = null)
    fun deactivate(callback: Runnable? = null)
}

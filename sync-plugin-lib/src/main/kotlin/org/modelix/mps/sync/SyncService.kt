package org.modelix.mps.sync

import jetbrains.mps.project.MPSProject
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.binding.IBinding

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

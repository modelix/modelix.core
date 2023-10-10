package org.modelix.mps.sync

import jetbrains.mps.project.MPSProject
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.binding.IBinding
import java.net.URL

interface SyncService {
    suspend fun bindModel(
        serverURL: URL,
        branchReference: BranchReference,
        modelName: String,
        jwt: String,
        targetProject: MPSProject,
        afterActivate: () -> Unit,
    ): IBinding
}

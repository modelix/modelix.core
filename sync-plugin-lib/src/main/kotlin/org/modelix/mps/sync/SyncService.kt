package org.modelix.mps.sync

import jetbrains.mps.project.MPSProject
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.binding.IBinding
import java.net.URL

interface SyncService {
    suspend fun bindRepository(
        serverURL: URL,
        branchReference: BranchReference,
        jwt: String,
        project: MPSProject,
        afterActivate: () -> Unit,
    ): IBinding
}

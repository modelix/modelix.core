package org.modelix.mps.sync3

import com.intellij.openapi.components.service
import jetbrains.mps.ide.project.ProjectHelper
import org.modelix.model.lazy.BranchReference

interface IModelSyncService {
    companion object {
        @JvmStatic
        fun getInstance(project: com.intellij.openapi.project.Project): IModelSyncService {
            return project.service<ModelSyncService>()
        }

        @JvmStatic
        fun getInstance(project: org.jetbrains.mps.openapi.project.Project): IModelSyncService {
            return getInstance(ProjectHelper.toIdeaProject(project as jetbrains.mps.project.Project))
        }
    }

    fun addServer(url: String): IServerConnection
    fun getServerConnections(): List<IServerConnection>
}

interface IServerConnection {
    fun activate()
    fun deactivate()
    fun remove()
    fun getStatus(): Status

    fun bind(branchRef: BranchReference): IBinding
    fun getBindings(): List<IBinding>

    enum class Status {
        CONNECTED,
        DISCONNECTED
    }
}

interface IBinding {
    val mpsProject: org.jetbrains.mps.openapi.project.Project
    val branchRef: BranchReference
    fun activate()
    fun deactivate()

    /**
     * Blocks until both ends are in sync.
     */
    suspend fun flush()
}

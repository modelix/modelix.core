package org.modelix.mps.sync

import com.intellij.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface SyncService {

    suspend fun bindModule(
        client: ModelClientV2,
        branchReference: BranchReference,
        module: INode,
        callback: (() -> Unit)? = null,
    ): List<IBinding>

    suspend fun connectModelServer(serverURL: URL, jwt: String, callback: (() -> Unit)? = null): ModelClientV2

    fun disconnectModelServer(client: ModelClientV2, callback: (() -> Unit)? = null)

    fun setActiveProject(project: Project)

    fun getModelBindings(): List<ModelBinding>

    fun getModuleBindings(): List<ModuleBinding>

    fun dispose()
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface IBinding {

    fun activate(callback: Runnable? = null)

    fun deactivate(removeFromServer: Boolean, callback: Runnable? = null)

    fun name(): String
}

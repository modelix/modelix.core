package org.modelix.mps.sync

import com.intellij.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import java.net.URL
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface SyncService {

    suspend fun bindModule(
        client: ModelClientV2,
        branchReference: BranchReference,
        module: INode,
        callback: (() -> Unit)? = null,
    ): Iterable<IBinding>

    suspend fun connectModelServer(serverURL: URL, jwt: String, callback: (() -> Unit)? = null): ModelClientV2

    fun disconnectModelServer(client: ModelClientV2, callback: (() -> Unit)? = null)

    fun setActiveProject(project: Project)

    fun dispose()
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface IBinding {

    fun activate(callback: Runnable? = null)

    fun deactivate(removeFromServer: Boolean, callback: Runnable? = null): CompletableFuture<Any?>

    fun name(): String
}

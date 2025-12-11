package org.modelix.mps.sync3

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import jetbrains.mps.ide.project.ProjectHelper
import kotlinx.coroutines.runBlocking
import org.modelix.model.IVersion
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.oauth.OAuthConfigBuilder
import java.io.Closeable

interface IModelSyncService {
    companion object {
        var continueOnError: Boolean? = null

        @JvmStatic
        fun getInstance(project: Project): IModelSyncService {
            return project.service<ModelSyncService>()
        }

        @JvmStatic
        fun getInstance(project: org.jetbrains.mps.openapi.project.Project): IModelSyncService {
            return getInstance(ProjectHelper.toIdeaProject(project as jetbrains.mps.project.Project))
        }
    }

    fun addServer(url: String, repositoryId: RepositoryId? = null): IServerConnection = addServer(
        ModelServerConnectionProperties(
            url = url,
            repositoryId = repositoryId,
        ),
    )
    fun addServer(properties: ModelServerConnectionProperties): IServerConnection
    fun getServerConnections(): List<IServerConnection>
    fun getUsedServerConnections(): List<IServerConnection>
    fun getBindings(): List<IBinding>
    fun updateBinding(oldBranchRef: BranchReference, newBranchRef: BranchReference, resetLocalState: Boolean = false)
}

data class ModelServerConnectionProperties(
    val url: String,
    /**
     * Is forwarded to the token endpoint.
     */
    val repositoryId: RepositoryId? = null,
    val oauthClientId: String? = null,
    val oauthClientSecret: String? = null,
)

interface IServerConnection : Closeable {
    fun setTokenProvider(tokenProvider: (suspend () -> String?))
    fun configureOAuth(body: OAuthConfigBuilder.() -> Unit)

    fun activate()
    fun deactivate()
    fun remove()
    fun getStatus(): Status
    fun getPendingAuthRequest(): String?

    override fun close() = deactivate()

    suspend fun pullVersion(branchRef: BranchReference): IVersion

    fun bind(branchRef: BranchReference): IBinding = bind(branchRef, null)
    fun bind(branchRef: BranchReference, lastSyncedVersionHash: String?): IBinding
    fun getBindings(): List<IBinding>

    fun getUrl(): String
    suspend fun getClient(): IModelClientV2

    enum class Status {
        CONNECTED,
        DISCONNECTED,
        AUTHORIZATION_REQUIRED,
    }
}

interface IBinding : Closeable {
    fun getProject(): org.jetbrains.mps.openapi.project.Project
    fun getConnection(): IServerConnection
    fun getBranchRef(): BranchReference
    fun isEnabled(): Boolean
    fun enable()
    fun disable()
    fun delete()

    override fun close() = disable()

    /**
     * Blocks until both ends are in sync.
     * @exception Throwable if the last synchronization failed
     * @return the latest version
     */
    suspend fun flush(): IVersion
    fun flushBlocking() = runBlocking { flush() }
    suspend fun flushIfEnabled(): IVersion?
    fun forceSync(push: Boolean)
    fun getCurrentVersion(): IVersion?

    fun getSyncProgress(): String?
    fun getStatus(): Status

    sealed class Status {
        object Disabled : Status()

        /**
         * Binding is enabled, but the initial synchronization hasn't started yet.
         */
        object Initializing : Status()

        /**
         * The last synchronization was successful.
         */
        data class Synced(val versionHash: String) : Status()

        /**
         * Synchronization is running.
         */
        data class Syncing(val progress: () -> String?) : Status()

        /**
         * The last synchronization failed.
         */
        data class Error(val message: String?) : Status()

        /**
         * The last synchronization failed because of a mission permission.
         */
        data class NoPermission(val user: String?) : Status()
    }
}

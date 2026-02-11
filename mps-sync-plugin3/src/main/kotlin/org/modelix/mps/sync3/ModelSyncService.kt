@file:OptIn(ExperimentalTime::class)

package org.modelix.mps.sync3

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.jdom.Element
import org.modelix.model.IVersion
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.OAuthConfigBuilder
import org.modelix.model.oauth.TokenProvider
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.ExperimentalTime

private val LOG = KotlinLogging.logger { }

@Service(Service.Level.PROJECT)
@State(name = "modelix-sync", storages = [Storage(value = "modelix.xml")])
class ModelSyncService(val project: Project) :
    IModelSyncService,
    Disposable,
    PersistentStateComponent<Element> {
    private val mpsProject: MPSProject get() = ProjectHelper.fromIdeaProject(project)!!

    private var loadedState: SyncServiceState = SyncServiceState()
    private val worker: AtomicReference<BindingWorker?> = AtomicReference(null)
    private val coroutinesScope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    override fun addServer(properties: ModelServerConnectionProperties): Connection {
        return AppLevelModelSyncService.getInstance().getOrCreateConnection(properties).let { Connection(it) }
    }

    @Synchronized
    override fun getServerConnections(): List<IServerConnection> {
        return AppLevelModelSyncService.getInstance().getConnections().map { Connection(it) }
    }

    @Synchronized
    override fun getUsedServerConnections(): List<IServerConnection> {
        return loadedState.bindings
            .filterValues { it.enabled }
            .keys
            .map { it.connectionProperties }
            .distinct()
            .map { Connection(AppLevelModelSyncService.getInstance().getOrCreateConnection(it)) }
    }

    @Synchronized
    override fun dispose() {
        worker.get()?.deactivate()
        coroutinesScope.cancel("disposed")
    }

    @Synchronized
    override fun getState(): Element? {
        return updateCurrentVersions().toXml()
        // Returning XML seems to be the most reliable way to get the state actually persisted.
        // Letting IntelliJ serialize the state sometimes fails silently.
        // Using kotlin.serialization is difficult because of version conflicts.
    }

    @Synchronized
    override fun loadState(state: Element) {
        loadState(SyncServiceState.fromXml(state))
    }

    @Synchronized
    override fun getBindings(): List<IBinding> {
        return loadedState.bindings.keys.map { Binding(it) }
    }

    @Synchronized
    override fun isReadonly(binding: IBinding): Boolean {
        binding as Binding
        return loadedState.bindings[binding.id]?.readonly == true
    }

    @Synchronized
    override fun switchBranch(oldBranchRef: BranchReference, newBranchRef: BranchReference, dropLocalChanges: Boolean) {
        updateState {
            it.bindings.none { it.key.branchRef == oldBranchRef } &&
                throw IllegalArgumentException("No binding for $oldBranchRef")

            it.copy(
                bindings = it.bindings.mapKeys { (key, _) ->
                    if (key.branchRef == oldBranchRef) {
                        key.copy(branchRef = newBranchRef)
                    } else {
                        key
                    }
                }.mapValues { (key, value) ->
                    if (dropLocalChanges && key.branchRef == newBranchRef) {
                        value.copy(versionHash = null)
                    } else {
                        value
                    }
                },
            )
        }
    }

    @Synchronized
    fun loadState(newState: SyncServiceState) {
        val oldState: SyncServiceState = this.loadedState
        val enabledBindings = newState.bindings.filter { it.value.enabled }

        val worker: BindingWorker? = worker.get()
        if (enabledBindings.isEmpty()) {
            if (worker == null) {
                // nothing to do
            } else {
                worker.deactivate()
                this.worker.set(null)
            }
        } else {
            if (worker != null) {
                if (worker.syncTargets.map { it.bindingId } != enabledBindings.keys.toList() ||
                    enabledBindings.any { (bindingId, newBindingState) ->
                        newBindingState.versionHash != oldState.bindings[bindingId]?.versionHash &&
                            newBindingState.versionHash != worker.initialVersionHash(bindingId) &&
                            newBindingState.versionHash != worker.getCurrentVersionHash(bindingId)
                    }
                ) {
                    worker.deactivate()
                    this.worker.set(null)
                }
            }
            if (enabledBindings.isNotEmpty()) {
                getOrCreateWorker(enabledBindings)?.activate()
            } else {
                this.worker.get()?.deactivate()
                this.worker.set(null)
            }
        }

        this.loadedState = newState
    }

    @Synchronized
    private fun writeState(updater: (SyncServiceState) -> SyncServiceState): SyncServiceState {
        val oldState: SyncServiceState = this.loadedState
        val updatedState = updater(oldState)
        if (updatedState != oldState) {
            this.loadedState = updatedState
        }
        return this.loadedState
    }

    @Synchronized
    private fun updateState(updater: (SyncServiceState) -> SyncServiceState): SyncServiceState {
        return updater(this.loadedState).also { loadState(it) }
    }

    @Synchronized
    private fun updateBindingState(id: BindingId, updater: (BindingState) -> BindingState) {
        updateState { oldState ->
            val oldBinding = oldState.bindings[id]
            oldState.copy(
                bindings = oldState.bindings + (id to updater(oldBinding ?: BindingState())),
            )
        }
    }

    @Synchronized
    private fun updateCurrentVersions(): SyncServiceState {
        return writeState { oldState ->
            oldState.copy(
                bindings = oldState.bindings.mapValues { (bindingId, state) ->
                    val newHash = worker.get()?.getCurrentVersionHash(bindingId)
                    state.copy(versionHash = newHash ?: state.versionHash)
                },
            )
        }
    }

    @Synchronized
    private fun getOrCreateWorker(): BindingWorker? {
        val bindings = loadedState.bindings.map { it.key to it.value }.filter { it.second.enabled }
        return if (bindings.isNotEmpty()) getOrCreateWorker(bindings) else null
    }

    @Synchronized
    private fun getOrCreateWorker(bindings: Map<BindingId, BindingState>): BindingWorker? {
        val enabledBindings = bindings.map { it.key to it.value }.filter { it.second.enabled }
        return if (enabledBindings.isEmpty()) null else getOrCreateWorker(enabledBindings)
    }

    @Synchronized
    private fun getOrCreateWorker(bindings: List<Pair<BindingId, BindingState?>>): BindingWorker {
        return worker.getOrPut {
            it ?: BindingWorker(
                coroutinesScope,
                MPSProjectAsNode(mpsProject),
                syncTargets = bindings.map { (id, state) ->
                    SyncTarget(
                        serverConnection = addServer(id.connectionProperties.copy(repositoryId = id.branchRef.repositoryId)),
                        bindingId = id,
                        initialVersionHash = state?.versionHash,
                        readonly = state?.readonly ?: false,
                        projectId = state?.projectId,
                    )
                },
                continueOnError = { IModelSyncService.continueOnError ?: true },
            )
        }
    }

    inner class Connection(val connection: AppLevelModelSyncService.ServerConnection) : IServerConnection {
        override fun setTokenProvider(tokenProvider: TokenProvider) {
            connection.setAuthorizationConfig(IAuthConfig.fromTokenProvider(tokenProvider))
        }

        override fun configureOAuth(body: OAuthConfigBuilder.() -> Unit) {
            connection.configureOAuth(body)
        }

        override fun getUrl(): String {
            return connection.properties.url
        }

        override suspend fun getClient(): IModelClientV2 {
            return connection.getClient()
        }

        override fun activate() {
            TODO("Not yet implemented")
        }

        override fun deactivate() {
            connection.disable()
        }

        override fun remove() {
            TODO("Not yet implemented")
        }

        override fun getStatus(): IServerConnection.Status {
            return if (connection.isConnected()) {
                IServerConnection.Status.CONNECTED
            } else if (connection.getPendingAuthRequest() != null) {
                IServerConnection.Status.AUTHORIZATION_REQUIRED
            } else {
                IServerConnection.Status.DISCONNECTED
            }
        }

        override fun getPendingAuthRequest(): String? {
            return connection.getPendingAuthRequest()
        }

        override suspend fun pullVersion(branchRef: BranchReference): IVersion {
            return connection.getClient().pull(branchRef, null)
        }

        override fun bind(branchRef: BranchReference, lastSyncedVersionHash: String?): IBinding {
            val id = BindingId(connection.properties, branchRef)
            updateBindingState(id) { oldBinding ->
                BindingState(
                    versionHash = lastSyncedVersionHash ?: oldBinding.versionHash,
                    enabled = true,
                )
            }
            return Binding(id)
        }

        override fun getBindings(): List<IBinding> {
            return synchronized(this@ModelSyncService) {
                loadedState.bindings.keys.map { Binding(it) }.filter { it.id.connectionProperties == connection.properties }
            }
        }

        private fun getService(): ModelSyncService = this@ModelSyncService

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Connection

            return connection == other.connection && getService() == other.getService()
        }

        override fun hashCode(): Int {
            return connection.hashCode() + 31 * getService().hashCode()
        }
    }

    inner class Binding(val id: BindingId) : IBinding {
        override fun toString(): String = id.toString()

        override fun getProject(): org.jetbrains.mps.openapi.project.Project {
            return ProjectHelper.fromIdeaProject(project)!!
        }

        override fun getConnection(): IServerConnection {
            return Connection(AppLevelModelSyncService.getInstance().getOrCreateConnection(id.connectionProperties))
        }

        override fun getBranchRef(): BranchReference {
            return id.branchRef
        }

        override fun isEnabled(): Boolean {
            return synchronized(this@ModelSyncService) { loadedState.bindings[id]?.enabled == true }
        }

        override fun enable() {
            updateBindingState(id) { it.copy(enabled = true) }
        }

        override fun disable() {
            updateBindingState(id) { it.copy(enabled = false) }
        }

        override fun delete() {
            updateState { it.copy(bindings = it.bindings - id) }
        }

        override suspend fun flush(): IVersion {
            check(isEnabled()) { "Binding is disabled" }
            return checkNotNull(flush(false)) { "Flush failed. No version was returned." }
        }

        override suspend fun flushIfEnabled(): IVersion? {
            return flush(true)
        }

        private suspend fun flush(ifEnabled: Boolean): IVersion? {
            val worker = synchronized(this@ModelSyncService) {
                if (ifEnabled && !isEnabled()) return null
                getOrCreateWorker()
            } ?: return null
            val index = worker.syncTargets.indexOfFirst { it.bindingId == id }
            return worker.flush()[index]
        }

        private fun indexInWorker(): Int {
            return (worker.get() ?: return -1).syncTargets.indexOfFirst { it.bindingId == id }
        }

        override fun forceSync(push: Boolean) {
            coroutinesScope.launch {
                worker.get()?.forceSync(push)
            }
        }

        override fun getCurrentVersion(): IVersion? {
            return worker.get()?.getCurrentVersion(id)
        }

        override fun getSyncProgress(): String? {
            return worker.get()?.getSyncProgress()
        }

        override fun getStatus(): IBinding.Status {
            return worker.get()?.getStatus()[indexInWorker()] ?: IBinding.Status.Disabled
        }

        private fun getService(): ModelSyncService = this@ModelSyncService

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Binding

            return id == other.id && getService() == other.getService()
        }

        override fun hashCode(): Int {
            return id.hashCode() + 31 * getService().hashCode()
        }
    }
}

fun CoroutineScope.launchLoop(body: suspend () -> Unit) = launchLoop(BackoffStrategy(), body)
fun CoroutineScope.launchLoop(backoffStrategy: BackoffStrategy, body: suspend () -> Unit) = launch { jobLoop(backoffStrategy, body) }

suspend fun jobLoop(body: suspend () -> Unit): Unit = jobLoop(BackoffStrategy(), body)

suspend fun jobLoop(
    backoffStrategy: BackoffStrategy,
    body: suspend () -> Unit,
) {
    while (true) {
        try {
            backoffStrategy.wait()
            body()
            backoffStrategy.success()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Throwable) {
            LOG.warn("Exception during synchronization", ex)
            backoffStrategy.failed()
        }
    }
}

private fun <T : Any> AtomicReference<T?>.getOrPut(initializer: (T?) -> T) = updateAndGet { initializer(it) } as T

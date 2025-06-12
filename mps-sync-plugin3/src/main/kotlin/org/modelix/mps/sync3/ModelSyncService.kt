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
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.OAuthConfigBuilder
import org.modelix.model.oauth.TokenProvider
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
    private val workers = LinkedHashMap<BindingId, BindingWorker>()
    private val coroutinesScope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    override fun addServer(properties: ModelServerConnectionProperties): Connection {
        return AppLevelModelSyncService.getInstance().addConnection(properties).let { Connection(it) }
    }

    @Synchronized
    override fun getServerConnections(): List<IServerConnection> {
        val enabledBindingIds = loadedState.bindings
            .filterValues { it.enabled }
            .keys
            .map { it.connectionProperties }

        return AppLevelModelSyncService.getInstance().getConnections()
            .filter { con -> enabledBindingIds.contains(con.properties)}
            .map { Connection(it) }
    }

    @Synchronized
    override fun dispose() {
        workers.values.forEach { it.deactivate() }
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
    fun loadState(newState: SyncServiceState) {
        val oldState: SyncServiceState = this.loadedState
        val allBindingIds = newState.bindings.keys + oldState.bindings.keys + workers.keys

        for (id in allBindingIds) {
            val newBindingState: BindingState? = newState.bindings[id]
            val oldBindingState: BindingState? = oldState.bindings[id]
            val worker: BindingWorker? = workers[id]
            if (newBindingState == null) {
                if (worker == null) {
                    // nothing to do
                } else {
                    worker.deactivate()
                    workers.remove(id)
                }
            } else {
                if (worker != null) {
                    if (newBindingState.versionHash != oldBindingState?.versionHash &&
                        newBindingState.versionHash != worker.initialVersionHash &&
                        newBindingState.versionHash != worker.getCurrentVersionHash()
                    ) {
                        worker.deactivate()
                        workers.remove(id)
                    }
                }
                val newWorker = getOrCreateWorker(id, newBindingState)
                if (newBindingState.enabled) {
                    newWorker.activate()
                } else {
                    newWorker.deactivate()
                }
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
                oldState.bindings.mapValues {
                    it.value.copy(
                        versionHash = workers[it.key]?.getCurrentVersionHash() ?: it.value.versionHash,
                    )
                },
            )
        }
    }

    @Synchronized
    private fun updateWorker(id: BindingId, state: BindingState) {
        val binding = getOrCreateWorker(id, state)
        if (state.enabled) {
            binding.activate()
        } else {
            binding.deactivate()
        }
    }

    @Synchronized
    private fun getOrCreateWorker(id: BindingId, state: BindingState?): BindingWorker {
        return workers.getOrPut(id) {
            BindingWorker(
                coroutinesScope,
                mpsProject,
                serverConnection = addServer(id.connectionProperties.copy(repositoryId = id.branchRef.repositoryId)),
                branchRef = id.branchRef,
                initialVersionHash = state?.versionHash,
                continueOnError = { IModelSyncService.continueOnError ?: true },
            )
        }
    }

    data class SyncServiceState(
        val bindings: Map<BindingId, BindingState> = emptyMap(),
    ) {
        fun toXml() = Element("model-sync").also {
            it.children.addAll(
                bindings.map { bindingEntry ->
                    Element("binding").also {
                        it.children.add(Element("enabled").also { it.text = bindingEntry.value.enabled.toString() })
                        it.children.add(Element("url").also { it.text = bindingEntry.key.connectionProperties.url })
                        bindingEntry.key.connectionProperties.oauthClientId?.let { oauthClientId ->
                            it.children.add(Element("oauthClientId").also { it.text = oauthClientId })
                        }
                        bindingEntry.key.connectionProperties.oauthClientSecret?.let { oauthClientSecret ->
                            it.children.add(Element("oauthClientSecret").also { it.text = oauthClientSecret })
                        }
                        it.children.add(Element("repository").also { it.text = bindingEntry.key.branchRef.repositoryId.id })
                        it.children.add(Element("branch").also { it.text = bindingEntry.key.branchRef.branchName })
                        it.children.add(Element("versionHash").also { it.text = bindingEntry.value.versionHash })
                    }
                },
            )
        }
        companion object {
            fun fromXml(element: Element): SyncServiceState {
                return SyncServiceState(
                    element.getChildren("binding").mapNotNull<Element, Pair<BindingId, BindingState>> { element ->
                        val repositoryId = RepositoryId(element.getChild("repository")?.text ?: return@mapNotNull null)
                        BindingId(
                            connectionProperties = ModelServerConnectionProperties(
                                url = element.getChild("url")?.text ?: return@mapNotNull null,
                                repositoryId = repositoryId,
                                oauthClientId = element.getChild("oauthClientId")?.text,
                                oauthClientSecret = element.getChild("oauthClientSecret")?.text,
                            ),
                            branchRef = BranchReference(
                                repositoryId,
                                element.getChild("branch")?.text ?: return@mapNotNull null,
                            ),
                        ) to BindingState(
                            versionHash = element.getChild("versionHash")?.text,
                            enabled = element.getChild("enabled")?.text.toBoolean(),
                        )
                    }.toMap(),
                )
            }
        }
    }

    data class BindingState(
        val versionHash: String? = null,
        val enabled: Boolean = false,
    )

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
            connection.disconnect()
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
                    versionHash = lastSyncedVersionHash ?: oldBinding?.versionHash,
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
            val worker = synchronized(this@ModelSyncService) {
                getOrCreateWorker(id, loadedState.bindings[id])
            }
            return worker.flush()
        }

        override suspend fun flushIfEnabled(): IVersion? {
            val worker = synchronized(this@ModelSyncService) {
                if (!isEnabled()) return null
                getOrCreateWorker(id, loadedState.bindings[id])
            }
            return worker.flush()
        }

        override fun forceSync(push: Boolean) {
            coroutinesScope.launch {
                workers[id]?.forceSync(push)
            }
        }

        override fun getCurrentVersion(): IVersion? {
            return workers[id]?.getCurrentVersion()
        }

        override fun getSyncProgress(): String? {
            return workers[id]?.getSyncProgress()
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
            break
        } catch (ex: Throwable) {
            LOG.warn("Exception during synchronization", ex)
            backoffStrategy.failed()
        }
    }
}

data class BindingId(val connectionProperties: ModelServerConnectionProperties, val branchRef: BranchReference) {
    override fun toString(): String {
        return "BindingId($connectionProperties, ${branchRef.repositoryId}, ${branchRef.branchName})"
    }
}

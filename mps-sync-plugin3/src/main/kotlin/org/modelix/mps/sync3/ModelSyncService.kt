@file:OptIn(ExperimentalTime::class)

package org.modelix.mps.sync3

import com.intellij.configurationStore.Property
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
import org.jdom.Element
import org.modelix.model.IVersion
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync3.Binding.Companion.LOG
import kotlin.time.ExperimentalTime

@Service(Service.Level.PROJECT)
@State(name = "modelix-sync", storages = [Storage(value = "modelix.xml")])
class ModelSyncService(val project: Project) :
    IModelSyncService,
    Disposable,
    PersistentStateComponent<Element> {
    private val mpsProject: MPSProject get() = ProjectHelper.fromIdeaProject(project)!!

    private val bindings = ArrayList<Binding>()
    private val coroutinesScope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    override fun addServer(url: String): IServerConnection {
        return AppLevelModelSyncService.getInstance().addConnection(url).let { Connection(it) }
    }

    @Synchronized
    override fun getServerConnections(): List<IServerConnection> {
        return AppLevelModelSyncService.getInstance().getConnections().map { Connection(it) }
    }

    @Synchronized
    override fun dispose() {
        bindings.forEach { it.deactivate() }
        coroutinesScope.cancel("disposed")
    }

    @Synchronized
    override fun getState(): Element? {
        return State(
            bindings = bindings.map {
                BindingState(
                    url = it.serverConnection.getUrl(),
                    repository = it.branchRef.repositoryId.id,
                    branch = it.branchRef.branchName,
                    versionHash = it.getCurrentVersionHash(),
                )
            },
        ).toXml()
        // Returning XML seems to be the most reliable way to get the state actually persisted.
        // Letting IntelliJ serialize the state sometimes fails silently.
        // Using kotlin.serialization is difficult because of version conflicts.
    }

    @Synchronized
    override fun loadState(state: Element) {
        val state = State.fromXml(state)
        val statesById = state.bindings.associateBy { it.getId() }
        val bindingsById = bindings.associateBy { it.getState().getId() }
        val allBindingIds = statesById.keys + bindingsById.keys

        for (id in allBindingIds) {
            val state = statesById[id]
            val binding = bindingsById[id]
            if (state == null) {
                if (binding == null) {
                    // unreachable
                } else {
                    binding.deactivate()
                    bindings.remove(binding)
                }
            } else {
                if (binding == null) {
                    loadBinding(state)
                } else {
                    if (binding.initialVersionHash != state.versionHash && binding.getCurrentVersionHash() != state.versionHash) {
                        binding.deactivate()
                        bindings.remove(binding)
                        loadBinding(state)
                    }
                }
            }
        }
    }

    private fun loadBinding(state: BindingState) {
        addServer(state.url ?: return).bind(
            branchRef = RepositoryId(state.repository ?: return).getBranchReference(state.branch),
            lastSyncedVersionHash = state.versionHash,
        )
    }

    private fun Binding.getState() = BindingState(
        url = serverConnection.getUrl(),
        repository = branchRef.repositoryId.id,
        branch = branchRef.branchName,
        versionHash = getCurrentVersionHash(),
    )

    data class State(
        @Property
        val bindings: List<BindingState> = emptyList(),
    ) {
        fun toXml() = Element("model-sync").also {
            it.children.addAll(bindings.map { it.toXml() })
        }
        companion object {
            fun fromXml(element: Element) = State(element.getChildren("binding").map { BindingState.fromXml(it) })
        }
    }

    data class BindingState(
        val url: String? = null,
        val repository: String? = null,
        val branch: String? = null,
        val versionHash: String? = null,
    ) {
        fun getId(): Any = copy(versionHash = null)
        fun toXml() = Element("binding").also {
            it.children.add(Element("url").also { it.text = url })
            it.children.add(Element("repository").also { it.text = repository })
            it.children.add(Element("branch").also { it.text = branch })
            it.children.add(Element("versionHash").also { it.text = versionHash })
        }
        companion object {
            fun fromXml(element: Element) = BindingState(
                // separate elements instead of attributes so that each value has its own line in the .xml file
                element.getChild("url")?.text,
                element.getChild("repository")?.text,
                element.getChild("branch")?.text,
                element.getChild("versionHash")?.text,
            )
        }
    }

    inner class Connection(val connection: AppLevelModelSyncService.ServerConnection) : IServerConnection {
        override fun getUrl(): String {
            return connection.url
        }

        override suspend fun getClient(): IModelClientV2 {
            return connection.getClient()
        }

        override fun activate() {
            TODO("Not yet implemented")
        }

        override fun deactivate() {
            TODO("Not yet implemented")
        }

        override fun remove() {
            TODO("Not yet implemented")
        }

        override fun getStatus(): IServerConnection.Status {
            TODO("Not yet implemented")
        }

        override suspend fun pullVersion(branchRef: BranchReference): IVersion {
            return connection.getClient().pull(branchRef, null)
        }

        override fun bind(branchRef: BranchReference, lastSyncedVersionHash: String?): IBinding {
            val binding = Binding(
                coroutinesScope = coroutinesScope,
                mpsProject = mpsProject,
                serverConnection = this,
                branchRef = branchRef,
                initialVersionHash = lastSyncedVersionHash,
            )
            bindings.add(binding)
            binding.activate()
            return binding
        }

        override fun getBindings(): List<IBinding> {
            return bindings.filter { it.serverConnection == this }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Connection

            return connection == other.connection
        }

        override fun hashCode(): Int {
            return connection.hashCode()
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

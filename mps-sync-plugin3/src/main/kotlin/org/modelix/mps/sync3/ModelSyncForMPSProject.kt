package org.modelix.mps.sync3

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.IVersion
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.sync.bulk.FullSyncFilter
import org.modelix.model.sync.bulk.InvalidatingVisitor
import org.modelix.model.sync.bulk.InvalidationTree
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.ModelSynchronizer.IIncrementalUpdateInformation
import org.modelix.model.sync.bulk.NodeAssociationFromModelServer
import org.modelix.model.sync.bulk.NodeAssociationToModelServer
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.model.sync.bulk.MPSProjectSyncMask
import org.modelix.mps.sync3.Binding.Companion.LOG
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class AppLevelModelSyncService() : Disposable {

    private val coroutinesScope = CoroutineScope(Dispatchers.IO)

    class ServerConnection(val url: String) {
        val client: IModelClientV2 = ModelClientV2.builder().url(url).build()
    }
}

@Service(Service.Level.PROJECT)
class ModelSyncService(val project: Project) : IModelSyncService, Disposable {
    private val mpsProject: MPSProject get() = ProjectHelper.fromIdeaProjectOrFail(project)

    private val client: IModelClientV2 = ModelClientV2.builder().url("").build()
    private val bindings = ArrayList<Binding>()
    private val coroutinesScope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun bind(branchRef: BranchReference): Binding {
        val binding = Binding(
            coroutinesScope = coroutinesScope,
            mpsProject = mpsProject,
            client = client,
            branchRef = branchRef,
            initialVersionHash = null
        )
        bindings.add(binding)
        binding.activate()
        return binding
    }

    @Synchronized
    override fun dispose() {
        bindings.forEach { it.deactivate() }
        coroutinesScope.cancel("disposed")
    }
}

class Binding(
    val coroutinesScope: CoroutineScope,
    override val mpsProject: org.jetbrains.mps.openapi.project.Project,
    val client: IModelClientV2,
    override val branchRef: BranchReference,
    val initialVersionHash: String?,
) : IBinding {
    companion object {
        val LOG = mu.KotlinLogging.logger {  }
    }

    private val activated = AtomicBoolean(false)
    private val lastSyncedVersion = ValueWithMutex<IVersion?>(null)
    private var syncJob: Job? = null
    private var syncToServerTask: ValidatingJob? = null
    private var invalidatingListener: MPSInvalidatingListener? = null

    private val repository: SRepository get() = mpsProject.repository

    override fun activate() {
        if (activated.getAndSet(true)) return
        syncJob = coroutinesScope.launch { syncJob() }
    }

    override fun deactivate() {
        if (!activated.getAndSet(false)) return

        syncJob?.cancel()
        syncJob = null
        syncToServerTask = null
        invalidatingListener?.stop()
        invalidatingListener = null
    }

    private suspend fun CoroutineScope.syncJob() {
        // initial sync
        lastSyncedVersion.updateValue { oldVersion ->
            if (oldVersion == null) {
                // binding was never activated before

                val remoteVersion = client.pullIfExists(branchRef)
                if (remoteVersion == null) {
                    // repository doesn't exist -> copy the local project to the server
                    val emptyVersion = client.initRepository(branchRef.repositoryId)
                    runSyncToServer(emptyVersion) ?: emptyVersion
                } else {
                    runSyncToMPS(oldVersion, remoteVersion)
                    remoteVersion
                }
            } else {
                // binding is activated again after being deactivated

                // push local changes that happened while the binding was deactivated
                val localChanges = runSyncFromMPS(oldVersion)
                val remoteVersion = if (localChanges != null) {
                    val mergedVersion = client.push(branchRef, localChanges, oldVersion)
                    runSyncToMPS(oldVersion, mergedVersion)
                    mergedVersion
                } else {
                    client.pull(branchRef, oldVersion)
                }

                // load remote changes into MPS
                runSyncToMPS(oldVersion, remoteVersion)

                remoteVersion
            }
        }

        // continuous sync to MPS
        launch {
            jobLoop {
                client.pollHash(branchRef, lastSyncedVersion.getValue()) // just to suspend until anything changes
                lastSyncedVersion.updateValue { oldVersion ->
                    client.pull(branchRef, oldVersion).also { newVersion ->
                        runSyncToMPS(oldVersion, newVersion)
                    }
                }
            }
        }

        // continuous sync to server
        syncToServerTask = launchValidation {
            lastSyncedVersion.updateValue { oldVersion ->
                if (oldVersion == null) {
                    // have to wait for initial sync
                    oldVersion
                } else {
                    val newVersion = runSyncToServer(oldVersion)
                    newVersion ?: oldVersion
                }
            }
        }
    }

    private suspend fun runSyncToMPS(oldVersion: IVersion?, newVersion: IVersion) {
        if (oldVersion?.getContentHash() == newVersion.getContentHash()) return

        val mpsProjects = listOf(mpsProject as MPSProject)
        val baseVersion = oldVersion
        val filter = if (baseVersion != null) {
            val invalidationTree = InvalidationTree(100_000)
            val newTree = newVersion.getTree()
            newTree.visitChanges(
                baseVersion.getTree(),
                InvalidatingVisitor(newTree, invalidationTree),
            )
            invalidationTree
        } else {
            FullSyncFilter()
        }

        val targetRoot = MPSRepositoryAsNode(repository)
        val versionWithUpdatedAssociations = writeToMPS {
            val branch = TreePointer(newVersion.getTree())
            val nodeAssociation = NodeAssociationFromModelServer(branch, targetRoot.getModel())
            ModelSynchronizer(
                filter = filter,
                sourceRoot = branch.getRootNode().asWritableNode(),
                targetRoot = targetRoot,
                nodeAssociation = nodeAssociation,
                sourceMask = MPSProjectSyncMask(mpsProjects, false),
                targetMask = MPSProjectSyncMask(mpsProjects, true)
            ).synchronize()

            // TODO use foreign IDs for nodes not created in MPS?
            // nodeAssociation.writeAssociations()
        }
    }

    private suspend fun <R> writeToMPS(body: () -> R): R {
        val result = ArrayList<R>()
        withContext(Dispatchers.EDT) {
            repository.modelAccess.executeCommand {
                repository.modelAccess.executeUndoTransparentCommand {
                    result += body()
                }
            }
        }
        return result.single()
    }


    /**
     * @return null if nothing changed
     */
    private suspend fun runSyncFromMPS(oldVersion: IVersion): IVersion? {
        check(lastSyncedVersion.isLocked())

        val mpsProjects = listOf(mpsProject as MPSProject)
        val newVersion = repository.modelAccess.computeReadAction {
            fun sync(invalidationTree: IIncrementalUpdateInformation): IVersion? {
                return oldVersion.runWrite(client) { branch ->
                    ModelSynchronizer(
                        filter = invalidationTree,
                        sourceRoot = MPSRepositoryAsNode(ModelixMpsApi.getRepository()),
                        targetRoot = branch.getRootNode().asWritableNode(),
                        nodeAssociation = NodeAssociationToModelServer(branch),
                        sourceMask = MPSProjectSyncMask(mpsProjects, true),
                        targetMask = MPSProjectSyncMask(mpsProjects, false)
                    ).synchronize()
                }
            }

            if (invalidatingListener == null) {
                sync(FullSyncFilter()).also {
                    // registering the listener after the sync is sufficient
                    // because we are in a read action that prevents model changes
                    invalidatingListener = object : MPSInvalidatingListener(repository) {
                        override fun onInvalidation() {
                            syncToServerTask?.invalidate()
                        }
                    }.also { it.start(repository) }
                }
            } else {
                invalidatingListener!!.runSync { sync(it) }
            }
        }

        return newVersion
    }

    /**
     * @return null if nothing changed
     */
    private suspend fun runSyncToServer(oldVersion: IVersion): IVersion? {
        return runSyncFromMPS(oldVersion)?.let { client.push(branchRef, it, oldVersion) }
    }
}

inline fun CoroutineScope.jobLoop(body: () -> Unit) {
    while (isActive) {
        try {
            body()
        } catch (ex: CancellationException) {
            break
        } catch (ex: Throwable) {
            LOG.warn("Exception during synchronization", ex)
        }
    }
}

class ValueWithMutex<E>(private var value: E) {
    private val mutex = Mutex()

    suspend fun updateValue(body: suspend (E) -> E) {
        mutex.withLock {
            value = body(value)
        }
    }

    fun isLocked() = mutex.isLocked

    fun getValue(): E = value
}

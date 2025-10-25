package org.modelix.mps.sync3

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.CancellationException
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.datastructures.model.ContainmentChangedEvent
import org.modelix.datastructures.model.NodeAddedEvent
import org.modelix.datastructures.model.NodeChangeEvent
import org.modelix.datastructures.model.NodeRemovedEvent
import org.modelix.model.IVersion
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.getName
import org.modelix.model.api.getOriginalReference
import org.modelix.model.area.PArea
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.runWriteOnModel
import org.modelix.model.mpsadapters.MPSProjectAdapter
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.model.mpsadapters.MPSProjectReference
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.mpsadapters.computeRead
import org.modelix.model.mutable.DummyIdGenerator
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.sync.bulk.DefaultInvalidationTree
import org.modelix.model.sync.bulk.FullSyncFilter
import org.modelix.model.sync.bulk.IdentityPreservingNodeAssociation
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.NodeAssociationToModelServer
import org.modelix.mps.model.sync.bulk.MPSProjectSyncMask
import org.modelix.streams.iterateSuspending
import java.util.concurrent.atomic.AtomicBoolean

class BindingWorker(
    val coroutinesScope: CoroutineScope,
    val mpsProject: MPSProject,
    val serverConnection: ModelSyncService.Connection,
    val branchRef: BranchReference,
    val initialVersionHash: String?,
    val continueOnError: () -> Boolean,
) {
    companion object {
        val LOG = KotlinLogging.logger { }
    }

    private val activated = AtomicBoolean(false)
    private val lastSyncedVersion = ValueWithMutex<IVersion?>(null)
    private var syncJob: Job? = null
    private var syncToServerTask: ValidatingJob? = null
    private var invalidatingListener: MyInvalidatingListener? = null
    private var activeSynchronizer: ModelSynchronizer? = null
    private var previousSyncStack: List<IReadableNode> = emptyList()
    private var status: IBinding.Status = IBinding.Status.Disabled

    private val repository: SRepository get() = mpsProject.repository
    private suspend fun client() = serverConnection.getClient()

    fun getCurrentVersionHash(): String? = lastSyncedVersion.getValue()?.getContentHash()
    fun getCurrentVersion(): IVersion? = lastSyncedVersion.getValue()
    fun isActive(): Boolean = activated.get()

    fun activate() {
        if (activated.getAndSet(true)) return
        status = IBinding.Status.Initializing
        syncJob = coroutinesScope.launch {
            try {
                syncJob()
            } catch (ex: Throwable) {
                LOG.error(ex) { "Synchronization failed" }
                throw ex
            }
        }
    }

    fun deactivate() {
        if (!activated.getAndSet(false)) return
        status = IBinding.Status.Disabled
        syncJob?.cancel()
        syncJob = null
        syncToServerTask = null
        invalidatingListener?.stop()
        invalidatingListener = null
    }

    fun getStatus(): IBinding.Status = status

    private fun ModelSynchronizer.synchronizeAndStoreInstance() {
        try {
            activeSynchronizer = this
            synchronize()
        } finally {
            activeSynchronizer = null
            previousSyncStack = emptyList()
        }
    }

    fun getSyncProgress(): String? {
        val synchronizer = activeSynchronizer ?: return null
        val current = synchronizer.getCurrentSyncStack()
        val previous = previousSyncStack
        previousSyncStack = current

        // The user should just have enough details to understand where the sync is spending its time.
        val firstChange = current.zip(previous).indexOfFirst { it.first != it.second }
        val path = current.take(firstChange + 1).drop(1)

        return path.joinToString(" > ") { node ->
            runCatching { node.getName() }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: runCatching { node.tryGetConcept()?.getShortName() }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: runCatching { node.getNodeReference().serialize() }.getOrNull()
                ?: runCatching { node.toString() }.getOrNull()
                ?: (node::class.java.simpleName + "@" + System.identityHashCode(node))
        }
    }

    private suspend fun checkInSync(): String? {
        check(activated.get()) { "Binding is deactivated" }
        val version = lastSyncedVersion.flush()?.getOrThrow()
        if (version == null) return "Initial sync isn't done yet"
        if (invalidatingListener == null) return "No change listener registered in MPS"
        if (invalidatingListener?.hasAnyInvalidations() != false) return "There are pending changes in MPS"
        val remoteVersion = client().pullHash(branchRef)
        if (remoteVersion != version.getContentHash()) return "Local version (${version.getContentHash()} differs from remote version ($remoteVersion)"
        return null
    }

    suspend fun flush(): IVersion {
        check(syncJob?.isActive == true) { "Synchronization is not active" }
        var reason = checkInSync()
        var i = 0
        while (reason != null) {
            i++
            if (i % 10 == 0) LOG.debug { "Still waiting for the synchronization to finish: $reason" }
            delay(100)
            reason = checkInSync()
        }
        return lastSyncedVersion.getValue()!!
    }

    suspend fun forceSync(push: Boolean) {
        if (push) {
            syncToServer(incremental = false)
        } else {
            syncToMPS(incremental = false)
        }
    }

    private suspend fun <R : IVersion?> runSync(body: suspend (IVersion?) -> R) = lastSyncedVersion.updateValue {
        try {
            status = IBinding.Status.Syncing(::getSyncProgress)
            body(it).also {
                status = IBinding.Status.Synced(it?.getContentHash() ?: "null")
            }
        } catch (ex: Throwable) {
            status = if (ex is ResponseException && ex.response.status == HttpStatusCode.Forbidden) {
                IBinding.Status.NoPermission(runCatching { client().getUserId() }.getOrNull())
            } else {
                IBinding.Status.Error(ex.message)
            }
            throw ex
        }
    }

    private suspend fun CoroutineScope.syncJob() {
        // initial sync
        while (isActive()) {
            try {
                initialSync()
                break
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Throwable) {
                LOG.error(ex) { "Initial synchronization failed" }
                delay(5_000)
            }
        }

        // continuous sync to MPS
        launchLoop {
            val newHash = client().pollHash(branchRef, lastSyncedVersion.getValue())
            if (newHash != lastSyncedVersion.getValue()?.getContentHash()) {
                LOG.debug { "New remote version detected: $newHash" }
                syncToMPS(incremental = true)
            }
        }

        // continuous sync to server
        syncToServerTask = launchValidation {
            syncToServer(incremental = true)
        }
    }

    suspend fun ensureInitialized() {
        if (lastSyncedVersion.getValue() == null) {
            initialSync()
        }
    }

    private suspend fun initialSync() {
        runSync { oldVersion ->
            LOG.debug { "Running initial synchronization" }

            val baseVersion = oldVersion
                ?: initialVersionHash?.let { client().loadVersion(branchRef.repositoryId, it, null) }
            if (baseVersion == null) {
                // Binding was never activated before. Overwrite local changes or do initial upload.

                val remoteVersion = client().pullIfExists(branchRef)
                if (remoteVersion == null || remoteVersion.isInitialVersion()) {
                    LOG.debug { "Repository doesn't exist. Will copy the local project to the server." }
                    // repository doesn't exist -> copy the local project to the server
                    val emptyVersion = remoteVersion ?: client().initRepository(branchRef.repositoryId)
                    doSyncToServer(emptyVersion, incremental = false) ?: emptyVersion
                } else {
                    LOG.debug { "Repository exists. Will checkout version $remoteVersion" }
                    doSyncToMPS(null, remoteVersion, incremental = false)
                    remoteVersion
                }
            } else {
                // Binding was activated before. Preserve local changes.

                // push local changes that happened while the binding was deactivated
                val localChanges = doSyncFromMPS(baseVersion, incremental = false)
                val remoteVersion = if (localChanges != null) {
                    val mergedVersion = client().push(branchRef, localChanges, baseVersion)
                    doSyncToMPS(baseVersion, mergedVersion, incremental = false)
                    mergedVersion
                } else {
                    client().pull(branchRef, baseVersion)
                }

                // load remote changes into MPS
                doSyncToMPS(baseVersion, remoteVersion, incremental = false)

                remoteVersion
            }
        }
    }

    suspend fun syncToMPS(incremental: Boolean): IVersion {
        return runSync { oldVersion ->
            client().pull(branchRef, oldVersion).also { newVersion ->
                doSyncToMPS(oldVersion, newVersion, incremental)
            }
        }
    }

    suspend fun syncToServer(incremental: Boolean): IVersion? {
        return runSync { oldVersion ->
            if (oldVersion == null) {
                // have to wait for initial sync
                oldVersion
            } else {
                val newVersion = doSyncToServer(oldVersion, incremental)
                newVersion ?: oldVersion
            }
        }
    }

    private suspend fun doSyncToMPS(oldVersion: IVersion?, newVersion: IVersion, incremental: Boolean) {
        if (oldVersion?.getContentHash() == newVersion.getContentHash()) return

        LOG.debug { "Updating MPS project from $oldVersion to $newVersion" }

        val baseVersion = oldVersion
        val filter = if (baseVersion != null && incremental) {
            val newTree = newVersion.getModelTree()
            val model = newTree.asModelSingleThreaded()
            val invalidationTree = DefaultInvalidationTree(newTree.getRootNodeId(), 100_000)
            fun invalidateNode(nodeId: INodeReference) {
                val node = model.tryResolveNode(nodeId) ?: return
                invalidationTree.invalidate(node, false)
            }
            newTree.getChanges(baseVersion.getModelTree(), changesOnly = true).iterateSuspending(newTree.asObject().graph) { event ->
                when (event) {
                    is ContainmentChangedEvent<INodeReference>, is NodeRemovedEvent<INodeReference>, is NodeAddedEvent<INodeReference> -> {
                        // There will be a ChildrenChangedEvent that indirectly handles these cases.
                    }
                    is NodeChangeEvent<INodeReference> -> invalidateNode(event.nodeId)
                }
            }
            invalidationTree
        } else {
            FullSyncFilter()
        }

        val targetRoot = MPSRepositoryAsNode(repository)
        writeToMPS {
            if (invalidatingListener?.hasAnyInvalidations() == true) {
                // Concurrent modification!
                // Write changes from MPS to a new version first and try again after it is merged.
                LOG.debug { "Skipping sync to MPS because there are pending changes in MPS" }
                return@writeToMPS
            }

            getMPSListener().runSync {
                val sourceModel = newVersion.getModelTree().asModelSingleThreaded()
                val sourceRoot = sourceModel.getRootNode()

                // handle renamed projects
                val projectNode: IReadableNode? = findMatchingProjectNode(sourceRoot)
                if (projectNode != null) {
                    val projectId = getProjectId(projectNode)
                    if (projectId != getProjectId(MPSProjectAsNode(mpsProject))) {
                        MPSProjectAdapter(mpsProject).setName(projectId)
                    }
                }

                // val nodeAssociation = NodeAssociationFromModelServer(branch, targetRoot.getModel())
                val nodeAssociation = IdentityPreservingNodeAssociation(
                    targetRoot.getModel(),
                    mapOf(sourceRoot.getNodeReference() to targetRoot.getNodeReference()),
                )

                ModelSynchronizer(
                    filter = filter,
                    sourceRoot = sourceRoot,
                    targetRoot = targetRoot,
                    nodeAssociation = nodeAssociation,
                    sourceMask = MPSProjectSyncMask(listOf(mpsProject), false),
                    targetMask = MPSProjectSyncMask(listOf(mpsProject), true),
                    onException = {
                        if (!continueOnError()) throw it
                        getMPSListener().synchronizationErrorHappened()
                    },
                ).synchronizeAndStoreInstance()
            }
        }
    }

    private suspend fun <R> writeToMPS(body: () -> R): R {
        val result = ArrayList<R>()
        ApplicationManager.getApplication().invokeAndWait({
            ApplicationManager.getApplication().runWriteAction {
                repository.modelAccess.executeUndoTransparentCommand {
                    MPSProjectAsNode.runWithProject(mpsProject) {
                        result += body()
                    }
                }
            }
        }, ModalityState.NON_MODAL)
        return result.single()
    }

    private fun getMPSListener() = invalidatingListener ?: initializeListener()

    private fun initializeListener(): MyInvalidatingListener {
        // Being inside a transaction ensure there are not writes, and we don't lose changes.
        repository.modelAccess.checkReadAccess()
        check(invalidatingListener == null)
        return MyInvalidatingListener().also {
            invalidatingListener = it
            it.start(repository)
        }
    }

    /**
     * @return null if nothing changed
     */
    private suspend fun doSyncFromMPS(oldVersion: IVersion, incremental: Boolean): IVersion? {
        check(lastSyncedVersion.isLocked())

        LOG.debug { "Commiting MPS changes" }

        val client = client()
        val newVersion = repository.computeRead {
            fun sync(invalidationTree: ModelSynchronizer.IIncrementalUpdateInformation): IVersion? {
                return oldVersion.runWriteOnModel(
                    nodeIdGenerator = DummyIdGenerator<INodeReference>(),
                    author = client.getUserId(),
                ) { targetRoot ->
                    MPSProjectAsNode.runWithProject(mpsProject) {
                        val sourceRoot = MPSRepositoryAsNode(mpsProject.repository)

                        val legacyMutableTree = (targetRoot.getModel().asArea() as? PArea)?.branch

                        val nodeAssociation = if (legacyMutableTree != null) {
                            NodeAssociationToModelServer(legacyMutableTree)
                        } else {
                            IdentityPreservingNodeAssociation(
                                targetRoot.getModel(),
                                mapOf(sourceRoot.getNodeReference() to targetRoot.getNodeReference()),
                            )
                        }

                        // handled renamed projects
                        val projectNode: IWritableNode? = findMatchingProjectNode(targetRoot) as IWritableNode?
                        if (projectNode != null && !nodeAssociation.matches(MPSProjectAsNode(mpsProject), projectNode)) {
                            nodeAssociation.associate(MPSProjectAsNode(mpsProject), projectNode)
                        }

                        ModelSynchronizer(
                            filter = invalidationTree,
                            sourceRoot = sourceRoot,
                            targetRoot = targetRoot,
                            nodeAssociation = nodeAssociation,
                            sourceMask = MPSProjectSyncMask(listOf(mpsProject), true),
                            targetMask = MPSProjectSyncMask(listOf(mpsProject), false),
                            onException = { if (!continueOnError()) throw it },
                        ).synchronizeAndStoreInstance()
                    }
                }
            }

            if (invalidatingListener == null) {
                sync(FullSyncFilter()).also {
                    // registering the listener after the sync is sufficient
                    // because we are in a read action that prevents model changes
                    initializeListener()
                }
            } else {
                invalidatingListener!!.runSync { sync(if (incremental) it else FullSyncFilter()) }
            }
        }

        LOG.debug { if (newVersion == null) "Nothing changed" else "New version created: $newVersion" }

        return newVersion
    }

    /**
     * @return null if nothing changed
     */
    private suspend fun doSyncToServer(oldVersion: IVersion, incremental: Boolean): IVersion? {
        return doSyncFromMPS(oldVersion, incremental)?.let {
            client().push(branchRef, it, oldVersion)
        }
    }

    private inner class MyInvalidatingListener : MPSInvalidatingListener(repository) {
        override fun onInvalidation() {
            syncToServerTask?.invalidate()
        }
    }

    /**
     * Projects in MPS don't have an ID. MPSProjectReference uses the name, but that can change.
     * Try to find the best matching project.
     */
    private fun findMatchingProjectNode(targetRoot: IReadableNode): IReadableNode? {
        val projectNodes = targetRoot
            .getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference())
        return when (projectNodes.size) {
            0 -> null
            1 -> projectNodes.single()
            else -> projectNodes.find {
                getProjectId(it) == getProjectId(MPSProjectAsNode(mpsProject))
            }
        }
    }

    private fun getProjectId(node: IReadableNode): String {
        val ref = node.getOriginalReference()?.let { NodeReference(it) } ?: node.getNodeReference()
        return MPSProjectReference.tryConvert(ref)
            ?.projectName
            ?: node.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())
            ?: "0"
    }
}

private fun IVersion.isInitialVersion() = (this as CLVersion).baseVersion == null

package org.modelix.mps.sync3

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
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
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.mpsadapters.computeRead
import org.modelix.model.mutable.DummyIdGenerator
import org.modelix.model.mutable.VersionedModelTree
import org.modelix.model.mutable.asModel
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.sync.bulk.DefaultInvalidationTree
import org.modelix.model.sync.bulk.FullSyncFilter
import org.modelix.model.sync.bulk.IdentityPreservingNodeAssociation
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.NodeAssociationToModelServer
import org.modelix.mps.model.sync.bulk.MPSProjectSyncMask
import org.modelix.mps.multiplatform.model.MPSProjectReference
import org.modelix.streams.iterateSuspending
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.map

class SyncTarget(
    val serverConnection: ModelSyncService.Connection,
    val bindingId: BindingId,
    val initialVersionHash: String?,
    val readonly: Boolean,
    val projectId: MPSProjectReference?,
) {
    suspend fun client() = serverConnection.getClient()
    val branchRef: BranchReference get() = bindingId.branchRef
}

class BindingWorker(
    val coroutinesScope: CoroutineScope,
    val mpsProject: MPSProjectAsNode,
    val syncTargets: List<SyncTarget>,
    val continueOnError: () -> Boolean,
) {
    companion object {
        val LOG = KotlinLogging.logger { }
    }

    private val activated = AtomicBoolean(false)
    private val lastSyncedVersion = ValueWithMutex<List<SynchronizedVersions>?>(null)
    private var syncJob: Job? = null
    private var syncToServerTask: ValidatingJob? = null
    private var invalidatingListener: MyInvalidatingListener? = null
    private var activeSynchronizer: ModelSynchronizer? = null
    private var previousSyncStack: List<IReadableNode> = emptyList()
    private var status: List<IBinding.Status> = syncTargets.map { IBinding.Status.Disabled }

    private val repository: SRepository get() = mpsProject.project.getRepository()

    init {
        require(syncTargets.isNotEmpty())
    }

    fun getCurrentVersionHash(): List<String>? = getCurrentVersion()?.map { it.getContentHash() }
    fun getCurrentVersionHash(bindingId: BindingId): String? = getCurrentVersion(bindingId)?.let { it.getContentHash() }
    fun getCurrentVersion(): List<IVersion>? = lastSyncedVersion.getValue()?.map { it.remoteVersion }
    fun getCurrentVersion(bindingId: BindingId): IVersion? = lastSyncedVersion.getValue()?.let { versions ->
        val index = syncTargets.indexOfFirst { it.bindingId == bindingId }
        versions[index].remoteVersion
    }
    fun initialVersionHash(bindingId: BindingId) = syncTargets.find { it.bindingId == bindingId }?.initialVersionHash
    fun isActive(): Boolean = activated.get()

    fun activate() {
        if (activated.getAndSet(true)) return
        status = syncTargets.map { IBinding.Status.Initializing }
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
        status = syncTargets.map { IBinding.Status.Disabled }
        syncJob?.cancel()
        syncJob = null
        syncToServerTask = null
        invalidatingListener?.stop()
        invalidatingListener = null
    }

    fun getStatus(): List<IBinding.Status> = status

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

    private inline fun <R> forEachTarget(body: SyncTarget.() -> R): List<R> = syncTargets.map { body(it) }
    private inline fun <R> forEachTargetIndexed(body: SyncTarget.(index: Int) -> R): List<R> = syncTargets.withIndex().map { body(it.value, it.index) }

    private suspend fun checkInSync(): String? {
        check(activated.get()) { "Binding is deactivated" }
        val versions = lastSyncedVersion.flush()?.getOrThrow()
        if (versions == null) return "Initial sync isn't done yet"
        if (invalidatingListener == null) return "No change listener registered in MPS"
        if (invalidatingListener?.hasAnyInvalidations() != false) return "There are pending changes in MPS"
        forEachTargetIndexed { index ->
            val version = versions[index]
            val remoteVersion = client().pullHash(branchRef)
            if (remoteVersion != version.remoteVersion.getContentHash()) {
                return "Local version ($version differs from remote version ($remoteVersion)"
            }
        }
        return null
    }

    suspend fun flush(): List<IVersion> {
        check(syncJob?.isActive == true) { "Synchronization is not active" }
        var reason = checkInSync()
        var i = 0
        var delay = 10.0
        while (reason != null) {
            i++
            if (i % 10 == 0) LOG.debug { "Still waiting for the synchronization to finish: $reason" }
            delay(delay.toLong())
            delay = (delay * 1.5).coerceAtMost(3000.0)
            reason = checkInSync()
        }
        return lastSyncedVersion.getValue()!!.map { it.localVersion }
    }

    suspend fun forceSync(push: Boolean) {
        if (push) {
            syncToServer(incremental = false)
        } else {
            syncToMPS(incremental = false)
        }
    }

    private suspend fun <R : List<SynchronizedVersions>?> runSync(body: suspend (List<SynchronizedVersions>?) -> R): R = lastSyncedVersion.updateValue { oldVersions ->
        try {
            status = IBinding.Status.Syncing(::getSyncProgress).let { s -> syncTargets.map { s } }
            body(oldVersions).also { newVersions ->
                status = (newVersions ?: syncTargets.map { null }).map { IBinding.Status.Synced(it?.localVersion?.getContentHash() ?: "null") }
            }
        } catch (ex: Throwable) {
            status = forEachTarget {
                if (ex is ResponseException && ex.response.status == HttpStatusCode.Forbidden) {
                    IBinding.Status.NoPermission(runCatching { client().getUserId() }.getOrNull())
                } else {
                    IBinding.Status.Error(ex.message)
                }
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
        val latestHashes: List<Flow<String>> = forEachTargetIndexed<Flow<String>> { index ->
            channelFlow<String> {
                // The `combine` operator returns the latest values of all flows whenever one of them changes, but it
                // only starts streaming values when each stream returned at least one value.
                // If we don't send a first value here, changes are not detected until the first pollHash call for each
                // repository timed out. The synchronization is effectively blocked for the first ~30 seconds.
                channel.send(lastSyncedVersion.getValue()?.get(index)?.remoteVersion?.getContentHash() ?: "")

                launchLoop {
                    val lastKnownVersion = lastSyncedVersion.getValue()?.get(index)?.remoteVersion
                    channel.send(
                        client().pollHash(branchRef, lastKnownVersion).also { hash ->
                            LOG.trace { "pollHash($branchRef, $lastKnownVersion) -> $hash" }
                        },
                    )
                }
            }
        }
        launch {
            latestHashes.combine().collect { newHashes ->
                if (newHashes != lastSyncedVersion.getValue()?.map { it.remoteVersion.getContentHash() }) {
                    LOG.debug { "New remote version detected: $newHashes" }
                    syncToMPS(incremental = true)
                }
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
        runSync<List<SynchronizedVersions>?> { oldVersions ->
            LOG.debug { "Running initial synchronization" }

            val baseVersions = forEachTargetIndexed { index ->
                oldVersions?.get(index)?.localVersion
                    ?: initialVersionHash?.let { client().loadVersion(branchRef.repositoryId, it, null) }
            }

            if (baseVersions.contains(null)) {
                // Binding was never activated before or branch was switched.
                // Overwrite local changes or do initial upload.

                val existingRemoteVersions = forEachTargetIndexed { client().pullIfExists(branchRef) }
                val createdRemoteVersions = forEachTargetIndexed { index ->
                    existingRemoteVersions[index] ?: client().initRepository(branchRef.repositoryId)
                }

                if (existingRemoteVersions.all { it == null || it.isInitialVersion() }) {
                    LOG.debug { "Repository doesn't exist. Will copy the local project to the server." }
                    // repository doesn't exist -> copy the local project to the server
                    doSyncToServer(createdRemoteVersions.map { SynchronizedVersions(it, it) }, incremental = false)
                        ?: createdRemoteVersions.map { SynchronizedVersions(it, it) }
                } else {
                    LOG.debug { "Repository exists. Will checkout version $createdRemoteVersions" }
                    doSyncToMPS(baseVersions, createdRemoteVersions, incremental = false)
                    createdRemoteVersions.map { SynchronizedVersions(it, it) }
                }
            } else {
                // Binding was activated before. Preserve local changes.

                val createdBaseVersions: List<SynchronizedVersions> = forEachTargetIndexed { index ->
                    baseVersions[index] ?: client().initRepository(branchRef.repositoryId)
                }.map { SynchronizedVersions(it, it) }

                // push local changes that happened while the binding was deactivated
                val localChanges = doSyncFromMPS(createdBaseVersions, incremental = false)

                val mergedVersions: List<SynchronizedVersions> = forEachTargetIndexed { index ->
                    val baseVersion = createdBaseVersions[index]
                    val localChange = localChanges?.get(index)?.takeIf { it.localVersion != baseVersion }
                    val merged = if (localChange == null || syncTargets[index].readonly) {
                        client().pull(branchRef, baseVersion.remoteVersion)
                    } else {
                        client().push(branchRef, localChange.localVersion, baseVersion.remoteVersion)
                    }
                    SynchronizedVersions(
                        remoteVersion = merged,
                        localVersion = localChange?.localVersion ?: merged,
                    )
                }

                // load remote changes into MPS
                doSyncToMPS(
                    oldVersions = createdBaseVersions.map { it.localVersion },
                    newVersions = mergedVersions.map { it.remoteVersion },
                    incremental = false,
                )

                mergedVersions
            }
        }
    }

    suspend fun syncToMPS(incremental: Boolean): List<SynchronizedVersions> {
        return runSync { oldVersions ->
            val oldVersions = oldVersions ?: syncTargets.map { null }
            val latestRemoteVersions = forEachTargetIndexed { index ->
                client().pull(branchRef, oldVersions[index]?.remoteVersion)
            }
            val transitions: List<Pair<IVersion?, IVersion>> = latestRemoteVersions.zip(oldVersions).zip(syncTargets).map {
                val latestRemoteVersion = it.first.first
                val lastSyncResult = it.first.second
                val target = it.second
                if (target.readonly) {
                    // A binding being read-only just means that changes aren't pushed to the server. Local changes are
                    // still allowed.
                    // We preserve the local changes as long as there are no remote changes. If the remote version
                    // changes, we have two options:
                    // - A. merge it with the local changes.
                    // - B. drop the local changes and override them with the remote version.
                    // Option A sounds reasonable, but users aren't supposed to make changes to read-only bindings.
                    // It's just difficult to prevent that. Local changes would only be preserved until a restart of
                    // MPS. It's more consistent not to pretend that they are preserved and just drop them in both
                    // cases.
                    // We could also ignore all changes and not even create a local version, but then we don't know
                    // what to revert when a new remote version arrives. Reverting all changes immediately is probably
                    // very confusing for the users.
                    if (lastSyncResult?.localVersion == null || latestRemoteVersion.getContentHash() != lastSyncResult.remoteVersion.getContentHash()) {
                        // Drop possible local changes and move to the new remote version.
                        lastSyncResult?.localVersion to latestRemoteVersion
                    } else {
                        // Stay on the local version
                        lastSyncResult.localVersion to lastSyncResult.localVersion
                    }
                } else {
                    // Regular bindings are expected to be always in sync with the remote version
                    // (localVersion == remoteVersion) and can just apply the new version received from the server.
                    lastSyncResult?.localVersion to latestRemoteVersion
                }
            }
            doSyncToMPS(transitions.map { it.first }, transitions.map { it.second }, incremental)
            latestRemoteVersions.zip(transitions).map {
                SynchronizedVersions(
                    remoteVersion = it.first,
                    localVersion = it.second.second,
                )
            }
        }
    }

    suspend fun syncToServer(incremental: Boolean): List<SynchronizedVersions>? {
        return runSync { oldVersions ->
            if (oldVersions == null) {
                // have to wait for initial sync
                oldVersions
            } else {
                val newVersions = doSyncToServer(oldVersions, incremental)
                newVersions ?: oldVersions
            }
        }
    }

    private suspend fun doSyncToMPS(oldVersions: List<IVersion?>, newVersions: List<IVersion>, incremental: Boolean) {
        if (oldVersions.zip(newVersions).all { it.first?.getContentHash() == it.second.getContentHash() }) return

        LOG.debug { "Updating MPS project from $oldVersions to $newVersions" }

        val newTrees = newVersions.map { it.getModelTree() }
        val sourceModel = SyncTargetModel(
            mpsProject,
            newVersions.zip(syncTargets).map { (newVersion, syncTarget) ->
                val model = newVersion.getModelTree().asModelSingleThreaded()
                val projectNode: () -> IWritableNode? = {
                    findMatchingProjectNode(model.getRootNode()) as IWritableNode?
                }
                SyncTargetConfig(
                    model = model,
                    readonly = syncTarget.readonly,
                    projectId = syncTarget.projectId
                        ?: projectNode()?.getNodeReference()?.let { MPSProjectReference.tryConvert(it) }
                        ?: mpsProject.getNodeReference(),
                )
            },
        )
        val baseVersions = oldVersions
        val filter = if (baseVersions.all { it != null } && incremental) {
            val invalidationTree = DefaultInvalidationTree(sourceModel.getRootNode().getNodeReference(), 100_000)

            forEachTargetIndexed { index ->
                val baseVersion = baseVersions[index] ?: return@forEachTargetIndexed
                val newTree = newTrees[index]
                fun invalidateNode(nodeId: INodeReference) {
                    val node = sourceModel.tryResolveNode(nodeId) ?: return
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
                val sourceRoot = sourceModel.getRootNode()

                // handle renamed projects
                val projectNode: IReadableNode? = findMatchingProjectNode(sourceRoot)
                if (projectNode != null) {
                    val projectId = getProjectId(projectNode)
                    if (projectId != mpsProject.getNodeReference().projectName) {
                        mpsProject.project.setName(projectId)
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
                    MPSProjectAsNode.runWithProjectNode(mpsProject) {
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
    private suspend fun doSyncFromMPS(oldVersions: List<SynchronizedVersions>, incremental: Boolean): List<SynchronizedVersions>? {
        check(lastSyncedVersion.isLocked())

        LOG.debug { "Commiting MPS changes" }

        val clients = forEachTarget { client() }
        val newVersions = repository.computeRead {
            fun sync(invalidationTree: ModelSynchronizer.IIncrementalUpdateInformation): List<SynchronizedVersions>? {
                val idGenerator = DummyIdGenerator<INodeReference>()
                val versionedTrees = oldVersions.map { VersionedModelTree(it.localVersion, idGenerator) }
                val model = SyncTargetModel(
                    mpsProject,
                    versionedTrees.zip(syncTargets).map { (versionedTree, syncTarget) ->
                        val model = versionedTree.asModel()
                        val projectNode: () -> IWritableNode? = {
                            model.executeRead {
                                findMatchingProjectNode(model.getRootNode()) as IWritableNode?
                            }
                        }
                        SyncTargetConfig(
                            model = model,
                            readonly = syncTarget.readonly,
                            projectId = syncTarget.projectId
                                ?: projectNode()?.getNodeReference()?.let { MPSProjectReference.tryConvert(it) }
                                ?: mpsProject.getNodeReference(),
                        )
                    },
                )
                model.executeWrite {
                    val targetRoot = model.getRootNode()
                    MPSProjectAsNode.runWithProjectNode(mpsProject) {
                        val sourceRoot = MPSRepositoryAsNode(mpsProject.project.getRepository())

                        val legacyMutableTree = (targetRoot.getModel().asArea() as? PArea)?.branch

                        val nodeAssociation = if (legacyMutableTree != null) {
                            NodeAssociationToModelServer(legacyMutableTree)
                        } else {
                            val overrides = mapOf(sourceRoot.getNodeReference() to targetRoot.getNodeReference())
                            IdentityPreservingNodeAssociation(targetRoot.getModel(), overrides)
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

                return oldVersions.zip(versionedTrees, clients).map { (oldVersion, mutableTree, client) ->
                    mutableTree.createVersion(client.getUserId())?.let { SynchronizedVersions(oldVersion.remoteVersion, it) } ?: oldVersion
                }.takeIf { it != oldVersions }
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

        LOG.debug { if (newVersions == null) "Nothing changed" else "New versions created: $newVersions" }

        return newVersions
    }

    /**
     * @return null if nothing changed
     */
    private suspend fun doSyncToServer(oldVersions: List<SynchronizedVersions>, incremental: Boolean): List<SynchronizedVersions>? {
        return doSyncFromMPS(oldVersions, incremental)?.let { newVersions ->
            coroutineScope {
                forEachTargetIndexed { index ->
                    if (readonly) {
                        CompletableDeferred(newVersions[index])
                    } else {
                        async {
                            SynchronizedVersions(
                                remoteVersion = client().push(
                                    branch = branchRef,
                                    version = newVersions[index].localVersion,
                                    baseVersion = oldVersions[index].remoteVersion,
                                ),
                                localVersion = newVersions[index].localVersion,
                            )
                        }
                    }
                }.awaitAll()
            }
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
                getProjectId(it) == getProjectId(mpsProject)
            } ?: projectNodes.first()
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

private fun <T1, T2, T3> Iterable<T1>.zip(other1: Iterable<T2>, other2: Iterable<T3>): List<Triple<T1, T2, T3>> {
    return (this zip other1 zip other2).map { Triple(it.first.first, it.first.second, it.second) }
}

private fun <T> List<Flow<T>>.combine(): Flow<List<T>> {
    return when (size) {
        0 -> emptyFlow()
        1 -> this[0].map { listOf(it) }
        2 -> this[0].combine(this[1]) { a, b -> listOf(a, b) }
        else -> subList(0, size - 1).combine().combine(last()) { a, b -> a + b }
    }
}

/**
 * Mostly used for allowing the local version to diverge in case of read-only bindings.
 */
data class SynchronizedVersions(
    /**
     * The last version that was received from the server.
     */
    val remoteVersion: IVersion,

    /**
     * The version that represents the state of MPS at the end of the last synchronization.
     */
    val localVersion: IVersion,
) {
    override fun toString(): String {
        val hash1 = remoteVersion.getContentHash()
        val hash2 = localVersion.getContentHash()
        return if (hash1 == hash2) hash1 else "$hash1/$hash2"
    }
}

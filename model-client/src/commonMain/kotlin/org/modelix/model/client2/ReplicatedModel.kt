package org.modelix.model.client2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.modelix.model.IVersion
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.operations.OTBranch

/**
 * Dispose should be called on this, as otherwise a regular polling will go on.
 */
class ReplicatedModel(
    val client: IModelClientV2,
    val branchRef: BranchReference,
    private val providedScope: CoroutineScope? = null,
) {
    private val scope = providedScope ?: CoroutineScope(Dispatchers.Default)
    private var state = State.New
    private lateinit var localModel: LocalModel
    private val remoteVersion = RemoteVersion(client, branchRef)
    private var pollingJob: Job? = null

//    private var changeListeners = mutableListOf<ReplicatedModelChangeListener>()
//    fun addReplicatedModelChangeListener(listener : ReplicatedModelChangeListener){
//        this.changeListeners.add(listener)
//    }
//    fun removeReplicatedModelChangeListener(listener: ReplicatedModelChangeListener){
//        this.changeListeners.remove(listener)
//    }

    fun getBranch(): IBranch {
        if (state != State.Started) throw IllegalStateException("state is $state")
        return localModel.otBranch
    }

    suspend fun start(): IBranch {
        if (state != State.New) throw IllegalStateException("already started")
        state = State.Starting

        val initialVersion = remoteVersion.pull()
        localModel = LocalModel(initialVersion, client.getIdGenerator(), { client.getUserId() })

        // receive changes from the server
        pollingJob = scope.launch {
            var nextDelayMs: Long = 0
            while (state != State.Disposed) {
                if (nextDelayMs > 0) delay(nextDelayMs)
                try {
                    val newRemoteVersion = remoteVersion.poll()
                    remoteVersionReceived(newRemoteVersion)
                    nextDelayMs = 0
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    LOG.debug { "Stop to poll branch $branchRef after disposing." }
                    throw ex
                } catch (ex: Throwable) {
                    LOG.error(ex) { "Failed to poll branch $branchRef" }
                    nextDelayMs = (nextDelayMs * 3 / 2).coerceIn(1000, 30000)
                }
            }
        }

        localModel.rawBranch.addListener(object : IBranchListener {
            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                if (isDisposed()) return
                scope.launch {
                    pushLocalChanges()
                }
            }
        })

        state = State.Started
        return getBranch()
    }

    suspend fun resetToServerVersion() {
        localModel.resetToVersion(client.pull(branchRef, lastKnownVersion = null).upcast())
    }

    fun isDisposed(): Boolean = state == State.Disposed

    private fun checkDisposed() {
        if (state == State.Disposed) throw IllegalStateException("disposed")
    }

    fun dispose() {
        if (state == State.Disposed) return
        pollingJob?.cancel("disposed")
        state = State.Disposed
        if (providedScope == null) {
            scope.cancel("disposed")
        }
    }

    private suspend fun remoteVersionReceived(newRemoteVersion: CLVersion) {
        if (isDisposed()) return

        val mergedVersion = try {
            localModel.mergeRemoteVersion(newRemoteVersion)
        } catch (ex: Exception) {
            val currentLocalVersion = localModel.getCurrentVersion()
            LOG.warn(ex) { "Failed to merge remote version $newRemoteVersion into local version $currentLocalVersion. Resetting to remote version." }
            localModel.resetToVersion(newRemoteVersion)
            newRemoteVersion
        }

        if (mergedVersion.getContentHash() != newRemoteVersion.getContentHash()) {
            val received = remoteVersion.push(mergedVersion)
            if (received.getContentHash() != mergedVersion.getContentHash()) {
                remoteVersionReceived(received)
            }
        }
    }

    private suspend fun pushLocalChanges() {
        if (isDisposed()) return

        val version = localModel.createNewLocalVersion() ?: localModel.getCurrentVersion()
        val received = remoteVersion.push(version)
        if (received.getContentHash() != version.getContentHash()) {
            remoteVersionReceived(received)
        }
    }

    private enum class State {
        New,
        Starting,
        Started,
        Disposed,
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger { }
    }
}

fun IModelClientV2.getReplicatedModel(branchRef: BranchReference): ReplicatedModel {
    return ReplicatedModel(this, branchRef)
}

fun IModelClientV2.getReplicatedModel(branchRef: BranchReference, scope: CoroutineScope): ReplicatedModel {
    return ReplicatedModel(this, branchRef, scope)
}

/**
 * Manages the locks during the creation and merge of versions.
 */
private class LocalModel(initialVersion: CLVersion, val idGenerator: IIdGenerator, val author: () -> String?) {

    /**
     * The state of the local model is the state of localVersion.getTree() plus the pending changes in
     * OTBranch.completedChanges.
     *
     * All changes done to OTBranch assume that they are applied on top of the current value of localVersion.
     * This means, before updating localVersion ensure there are no pending changes in OTBranch and that there are no
     * active write transactions that will contribute to the pending changes when successful.
     */
    private var localVersion: CLVersion = initialVersion
        get() {
            check(mutex.isLocked)
            return field
        }
        set(value) {
            check(mutex.isLocked)
            check(otBranch.canWrite()) { "Write transaction required to update the localVersion field" }
            field = value
        }

    val rawBranch: IBranch = PBranch(initialVersion.getTree(), idGenerator)
    val otBranch = OTBranch(rawBranch, idGenerator, initialVersion.store)
    private val merger = VersionMerger(initialVersion.store, idGenerator)

    private val mutex = Mutex()

    suspend fun resetToVersion(version: CLVersion) {
        mutex.withLock {
            otBranch.computeWrite { // write transaction ensures there are no active changes done on an outdated version
                otBranch.getPendingChanges() // discard any pending changes
                localVersion = version
                rawBranch.writeTransaction.tree = version.getTree()
            }
        }
    }

    suspend fun getCurrentVersion() = mutex.withLock { localVersion }

    suspend fun mergeRemoteVersion(remoteVersion: CLVersion): CLVersion {
        return mutex.withLock {
            // Avoid triggering branch listeners (causing endless loops) if there is no change.
            if (localVersion.getContentHash() == remoteVersion.getContentHash()) return localVersion

            otBranch.computeWrite {
                // Writing to localVersion requires that there are no pending operations in OTBranch. By creating a new
                // local version first, the pending operations become part of it.
                // Creating it inside a write transaction, guarantees that the list of pending changes stays empty util
                // we are done.
                doCreateNewLocalVersion()

                // Now we can merge the remote version update the localVersion field without losing local changes.
                // TODO run the (potentially expensive) merge algorithm outside a write transaction to avoid blocking
                //      the branch for too long. This requires to rerun the merge if new local changes were created in
                //      the meantime.
                val mergedVersion = merger.mergeChange(localVersion, remoteVersion)

                // The mutex guarantees that the localVersion field didn't change and the write transaction guarantees
                // that there are no local changes that would get lost. We are in a consistent state again.
                rawBranch.writeTransaction.tree = mergedVersion.getTree()
                localVersion = mergedVersion

                // Return the new localVersion just for convenience.
                mergedVersion
            }
        }
    }

    /**
     * @return null, if there are no pending changes and no new version was created.
     */
    suspend fun createNewLocalVersion(): CLVersion? {
        return mutex.withLock { otBranch.computeWrite { doCreateNewLocalVersion() } }
    }

    private fun doCreateNewLocalVersion(): CLVersion? {
        check(mutex.isLocked)
        val (ops, tree) = otBranch.getPendingChanges()
        check(tree is CLTree)

        val baseVersion = localVersion

        if (ops.isEmpty() && baseVersion.getTree().hash == tree.hash) return null
        val newVersion = CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            author = author(),
            tree = tree,
            baseVersion = baseVersion,
            operations = ops.map { it.getOriginalOp() }.toTypedArray(),
        )
        localVersion = newVersion
        return newVersion
    }
}

private class RemoteVersion(val client: IModelClientV2, val branchRef: BranchReference) {
    private var lastKnownRemoteVersion: CLVersion? = null
    private val unconfirmedVersions: MutableSet<String> = LinkedHashSet()

    fun getNumberOfUnconfirmed() = runSynchronized(unconfirmedVersions) { unconfirmedVersions.size }

    suspend fun pull(): CLVersion {
        return versionReceived(client.pull(branchRef, lastKnownVersion = lastKnownRemoteVersion).upcast())
    }

    suspend fun poll(): CLVersion {
        return versionReceived(client.poll(branchRef, lastKnownVersion = lastKnownRemoteVersion).upcast())
    }

    suspend fun push(version: CLVersion): CLVersion {
        if (lastKnownRemoteVersion?.getContentHash() == version.getContentHash()) return version
        runSynchronized(unconfirmedVersions) {
            if (!unconfirmedVersions.add(version.getContentHash())) return version
        }
        try {
            return versionReceived(client.push(branchRef, version, lastKnownRemoteVersion).upcast())
        } finally {
            runSynchronized(unconfirmedVersions) {
                unconfirmedVersions.remove(version.getContentHash())
            }
        }
    }

    private fun versionReceived(v: CLVersion): CLVersion {
        runSynchronized(unconfirmedVersions) {
            unconfirmedVersions.remove(v.getContentHash())
            lastKnownRemoteVersion = v
        }
        return v
    }
}

private fun IVersion.upcast(): CLVersion = this as CLVersion

interface ReplicatedModelChangeListener {
    fun onUpdate()
}

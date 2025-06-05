package org.modelix.model.client2

import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.TreeId
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mutable.IGenericMutableModelTree
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.INodeIdGenerator
import org.modelix.model.mutable.VersionedModelTree
import org.modelix.model.mutable.asModel

/**
 * Keeps a local replica of the model in sync with the remote model.
 * Changes can be made concurrently and are merged with changes made by other clients.
 *
 * # Algorithm
 * When local changes are made, they are pushed to the server and the server merges them into the remote model.
 * In contrast to Git, a push will not be rejected if the client didn't pull the latest remote version first,
 * but the server will just handle the merge and respond with the merged version.
 *
 * When the response from the server is received there are two cases:
 * - 1. nothing changed locally and the local copy can just be replaced with the received version
 * - 2. the local copy changed again, in which case we have to options:
 *   - A. merge the received version with the local version
 *   - B. ignore the received version and just push the new local changes, hoping that case 1 will apply this time.
 *
 * Option A potentially has to fetch additional data from the server to do a correct merge. This can result in a bigger
 * delay than just letting the server do the merge (option B). The server is located closer to the data and usually can
 * do the merge faster.
 * In case of a JavaScript client it's especially preferred to avoid additional requests. It doesn't support blocking
 * calls and not all code is consistently using async/stream APIs yet.
 *
 * Option B can be problematic in scenarios with a high frequency of concurrent changes. Then the merge algorithm will
 * repeatedly enter case 2 and continue to diverge. It relies on a large enough delay between local changes.
 * This is mostly theoretical and not expected cause any issues in practice.
 *
 * A previous implementation used strategy A, but was changed to strategy B to fix issues in the JS client.
 *
 * Dispose should be called on this, as otherwise a regular polling will go on.
 *
 * @property client the model client to connect to the model server
 * @property branchRef the model server branch to fetch the data from
 * @property providedScope the CoroutineScope to use for the suspendable tasks
 * @property initialRemoteVersion the last version on the server from which we want to start the synchronization
 */
class ReplicatedModel(
    val client: IModelClientV2,
    val branchRef: BranchReference,
    val idGenerator: (TreeId) -> INodeIdGenerator<INodeReference>,
    private val providedScope: CoroutineScope? = null,
    initialRemoteVersion: CLVersion? = null,
) : Closeable {
    private val scope = providedScope ?: CoroutineScope(Dispatchers.Default)
    private var state = State.New
    private var localModel: LocalModel? = null
    private val remoteVersion = RemoteVersion(client, branchRef, initialRemoteVersion)
    private var pollingJob: Job? = null

    init {
        if (initialRemoteVersion != null) {
            localModel = LocalModel(initialRemoteVersion, client.getIdGenerator(), idGenerator(initialRemoteVersion.getModelTree().getId())) { client.getUserId() }
        }
    }

    private fun getLocalModel(): LocalModel = checkNotNull(localModel) { "Model is not initialized yet" }

    fun getModel(): IMutableModel {
        return getLocalModel().versionedModelTree.asModel()
    }

    fun getVersionedModelTree(): IMutableModelTree = getLocalModel().versionedModelTree

    suspend fun start(): IMutableModelTree {
        if (state != State.New) throw IllegalStateException("already started")
        state = State.Starting

        if (localModel == null) {
            val initialVersion = remoteVersion.pull()
            localModel = LocalModel(initialVersion, client.getIdGenerator(), idGenerator(initialVersion.getModelTree().getId())) { client.getUserId() }
        }

        // receive changes from the server
        pollingJob = scope.launch {
            var nextDelayMs: Long = 0
            while (state != State.Disposed && isActive) {
                if (nextDelayMs > 0) delay(nextDelayMs)
                try {
                    val newRemoteVersion = remoteVersion.poll()
                    remoteVersionReceived(newRemoteVersion, null)
                    nextDelayMs = 0
                } catch (ex: CancellationException) {
                    LOG.debug { "Stop polling branch $branchRef after disposing." }
                    throw ex
                } catch (ex: Throwable) {
                    LOG.error(ex) { "Failed polling branch $branchRef" }
                    nextDelayMs = (nextDelayMs * 3 / 2).coerceIn(1000, 30000)
                }
            }
        }

        // send changes to the server
        getLocalModel().versionedModelTree.addListener(object : IGenericMutableModelTree.Listener<INodeReference> {

            override fun treeChanged(
                oldTree: IGenericModelTree<INodeReference>,
                newTree: IGenericModelTree<INodeReference>,
            ) {
                if (isDisposed()) return
                scope.launch {
                    pushLocalChanges()
                }
            }
        })

        state = State.Started
        return getVersionedModelTree()
    }

    suspend fun resetToServerVersion() {
        getLocalModel().resetToVersion(client.pull(branchRef, lastKnownVersion = null).upcast())
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

    override fun close() {
        dispose()
    }

    private suspend fun remoteVersionReceived(newRemoteVersion: CLVersion, responseOf: CLVersion?) {
        if (isDisposed()) return

        val mergedVersion = try {
            getLocalModel().mergeRemoteVersion(newRemoteVersion, responseOf)
        } catch (ex: Exception) {
            val currentLocalVersion = getLocalModel().getCurrentVersion()
            LOG.warn(ex) { "Failed to merge remote version $newRemoteVersion into local version $currentLocalVersion. Resetting to remote version." }
            getLocalModel().resetToVersion(newRemoteVersion)
            newRemoteVersion
        }

        if (mergedVersion.getContentHash() != newRemoteVersion.getContentHash()) {
            val received = remoteVersion.push(mergedVersion)
            if (received.getContentHash() != mergedVersion.getContentHash()) {
                remoteVersionReceived(received, responseOf = mergedVersion)
            }
        }
    }

    private suspend fun pushLocalChanges() {
        if (isDisposed()) return

        for (attempt in 1..10) {
            val version = getLocalModel().createNewLocalVersion() ?: getLocalModel().getCurrentVersion()
            val received = remoteVersion.push(version)
            if (received.getContentHash() == version.getContentHash()) return
            remoteVersionReceived(received, version)
        }

        throw IllegalStateException("Failed to push local changes after 10 attempts")
    }

    fun getCurrentVersion(): CLVersion {
        return getLocalModel().getCurrentVersion()
    }

    private enum class State {
        New,
        Starting,
        Started,
        Disposed,
    }

    companion object {
        private val LOG = KotlinLogging.logger { }
    }
}

fun IModelClientV2.getReplicatedModel(branchRef: BranchReference, idGenerator: (TreeId) -> INodeIdGenerator<INodeReference>): ReplicatedModel {
    return ReplicatedModel(this, branchRef, idGenerator)
}

fun IModelClientV2.getReplicatedModel(branchRef: BranchReference, idGenerator: (TreeId) -> INodeIdGenerator<INodeReference>, scope: CoroutineScope): ReplicatedModel {
    return ReplicatedModel(this, branchRef, idGenerator, scope)
}

/**
 * Manages the locks during the creation and merge of versions.
 */
private class LocalModel(initialVersion: CLVersion, val versionIdGenerator: IIdGenerator, val idGenerator: INodeIdGenerator<INodeReference>, val author: () -> String?) {

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
            return field
        }
        set(value) {
            checkInWriteTransaction()
            field = value
        }

    val versionedModelTree = VersionedModelTree(initialVersion, idGenerator)

    private fun checkInWriteTransaction() {
        check(versionedModelTree.canWrite()) { "Write transaction required to update the localVersion field" }
    }

    fun resetToVersion(version: CLVersion) {
        versionedModelTree.runWrite { // write transaction ensures there are no active changes done on an outdated version
            versionedModelTree.getPendingChanges() // discard any pending changes
            localVersion = version

            (it as VersionedModelTree.VersionedWriteTransaction).t.tree = version.getModelTree()
        }
    }

    fun getCurrentVersion() = localVersion

    /**
     * @return null, if the version couldn't be merged and the local version has to be pushed to the server first.
     */
    fun mergeRemoteVersion(remoteVersion: CLVersion, responseOf: CLVersion?): CLVersion {
        // Avoid triggering branch listeners (causing endless loops) if there is no change.
        if (localVersion.getContentHash() == remoteVersion.getContentHash()) return remoteVersion

        return versionedModelTree.runWrite {
            // Writing to localVersion requires that there are no pending operations in OTBranch. By creating a new
            // local version first, the pending operations become part of it.
            // Creating it inside a write transaction, guarantees that the list of pending changes stays empty util
            // we are done.
            doCreateNewLocalVersion()

            // Handle the most common and simple cases. All other cases are delegated to the server.
            if (canFastForward(remoteVersion, responseOf)) {
                localVersion = remoteVersion
                (it as VersionedModelTree.VersionedWriteTransaction).t.tree = localVersion.getModelTree()
            }

            localVersion
        }
    }

    private fun canFastForward(remoteVersion: CLVersion, responseOf: CLVersion?): Boolean {
        if (remoteVersion == localVersion) return true
        if (responseOf == localVersion) return true
        if (remoteVersion.getParentHashes().contains(localVersion.getObjectHash())) return true
        return false
    }

    /**
     * @return null, if there are no pending changes and no new version was created.
     */
    fun createNewLocalVersion(): CLVersion? {
        return versionedModelTree.runWrite { doCreateNewLocalVersion() }
    }

    private fun doCreateNewLocalVersion(): CLVersion? {
        checkInWriteTransaction()
        val (ops, tree) = versionedModelTree.getPendingChanges()
        val baseVersion = localVersion

        if (ops.isEmpty() && baseVersion.getTreeReference().getHash() == tree.asObject().getHash()) return null
        val newVersion = CLVersion.builder()
            .regularUpdate(baseVersion)
            .author(author())
            .currentTime()
            .tree(tree)
            .operations(ops.map { it.getOriginalOp() })
            .buildLegacy()
        localVersion = newVersion
        return newVersion
    }
}

private class RemoteVersion(
    val client: IModelClientV2,
    val branchRef: BranchReference,
    private var lastKnownRemoteVersion: CLVersion? = null,
) {
    private val unconfirmedVersions: MutableSet<String> = LinkedHashSet()

    fun getNumberOfUnconfirmed() = runSynchronized(unconfirmedVersions) { unconfirmedVersions.size }

    suspend fun pull(): CLVersion {
        return versionReceived(
            client.pull(
                branchRef,
                lastKnownVersion = null,
                filter = ObjectDeltaFilter(
                    knownVersions = setOfNotNull(lastKnownRemoteVersion?.getContentHash()),
                    includeOperations = false,
                    includeHistory = false,
                    includeTrees = true,
                ),
            ).upcast(),
        )
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

package org.modelix.model.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.apache.commons.lang3.mutable.MutableObject
import org.modelix.datastructures.model.asModelTree
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.PBranch
import org.modelix.model.client.SharedExecutors.fixDelay
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CLVersion.Companion.loadFromHash
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.IAppliedOperation
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.OTBranch
import java.time.LocalDateTime
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * Dispose should be called on this, as otherwise a regular polling will go on.
 */
actual open class ReplicatedRepository actual constructor(
    private val client: IModelClient,
    private val branchReference: BranchReference,
    private val user: () -> String,
) {
    private val localBranch: IBranch
    private val localOTBranch: OTBranch
    private val mergeLock = Any()
    private val merger: VersionMerger
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    actual constructor(client: IModelClient, repositoryId: RepositoryId, branchName: String, user: () -> String) :
        this(client, repositoryId.getBranchReference(branchName), user)

    @Volatile
    actual var localVersion: CLVersion?
        private set

    @Volatile
    private var remoteVersion: CLVersion?
    private val versionChangeDetector: VersionChangeDetector
    private val isEditing = AtomicBoolean(false)
    private var disposed = false
    private var divergenceTime = 0
    private val convergenceWatchdog: ScheduledFuture<*>
    actual val branch: IBranch
        get() {
            checkDisposed()
            return localOTBranch
        }

    /**
     * Call this at the beginning of an edit operation in the editor
     */
    fun startEdit() {
        isEditing.set(true)
    }

    /**
     * Call this at the end of an edit operation in the editor
     */
    fun endEdit(): CLVersion? {
        if (disposed) return null
        try {
            synchronized(mergeLock) {
                deleteDetachedNodes()
                return createAndMergeLocalVersion()
            }
        } finally {
            isEditing.set(false)
        }
    }

    protected fun deleteDetachedNodes() {
        val hasDetachedNodes = localOTBranch.computeRead {
            localOTBranch.transaction
                .getChildren(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE).iterator().hasNext()
        }
        // avoid unnecessary write
        if (hasDetachedNodes) {
            localOTBranch.runWrite {
                // clear detached nodes
                val t: IWriteTransaction = localOTBranch.writeTransaction
                t.getChildren(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE).forEach { nodeId: Long -> t.deleteNode(nodeId) }
            }
        }
    }

    protected fun createAndMergeLocalVersion(): CLVersion? {
        checkDisposed()
        var opsAndTree: Pair<List<IAppliedOperation>, ITree>
        var localBase: CLVersion?
        var remoteBase: CLVersion?
        var newLocalVersion: CLVersion
        synchronized(mergeLock) {
            opsAndTree = localOTBranch.getPendingChanges()
            localBase = localVersion
            remoteBase = remoteVersion
            val ops: Array<IOperation> = opsAndTree.first.map { it.getOriginalOp() }.toTypedArray()
            if (ops.isEmpty()) {
                return null
            }
            newLocalVersion = createVersion(opsAndTree.second, ops, localBase)
            localVersion = newLocalVersion
            divergenceTime = 0
        }
        SharedExecutors.FIXED.execute(object : Runnable {
            override fun run() {
                val doMerge: Supplier<Boolean> = object : Supplier<Boolean> {
                    override fun get(): Boolean {
                        var mergedVersion: CLVersion
                        try {
                            mergedVersion = merger.mergeChange(remoteBase!!, newLocalVersion)
                            LOG.debug {
                                String.format(
                                    "Merged local %s with remote %s -> %s",
                                    newLocalVersion.getObjectHash().toString(),
                                    remoteBase!!.getObjectHash().toString(),
                                    mergedVersion.getObjectHash().toString(),
                                )
                            }
                        } catch (ex: Exception) {
                            LOG.error(ex) { "" }
                            mergedVersion = newLocalVersion
                        }
                        synchronized(mergeLock) {
                            writeLocalVersion(localVersion)
                            if (remoteVersion == remoteBase) {
                                writeRemoteVersion(mergedVersion)
                                return true
                            } else {
                                remoteBase = remoteVersion
                                return false
                            }
                        }
                    }
                }

                // Avoid locking during the merge as it may require communication with the model server
                for (mergeAttempt in 0..2) {
                    if (doMerge.get()) {
                        return
                    }
                }
                synchronized(mergeLock) {
                    remoteBase = remoteVersion
                    doMerge.get()
                }
            }
        })
        return newLocalVersion
    }

    protected fun writeRemoteVersion(newVersion: CLVersion) {
        synchronized(mergeLock) {
            if (remoteVersion!!.getObjectHash() != newVersion.getObjectHash()) {
                remoteVersion = newVersion
                client.asyncStore.put(branchReference.getKey(), newVersion.getContentHash())
            }
        }
    }

    protected fun writeLocalVersion(newVersion: CLVersion?) {
        synchronized(mergeLock) {
            if (newVersion!!.getContentHash() != this.localVersion!!.getContentHash()) {
                this.localVersion = newVersion
                divergenceTime = 0
                localBranch.runWrite {
                    val newTree = newVersion.getTree()
                    if (newVersion.getTreeReference().getHash() != localBranch.transaction.tree.asModelTree().asObject().getHash()) {
                        localBranch.writeTransaction.tree = newTree
                    }
                }
            }
        }
    }

    fun createVersion(tree: ITree, operations: Array<IOperation>, previousVersion: CLVersion?): CLVersion {
        checkDisposed()
        val time = LocalDateTime.now().toString()
        return CLVersion.createRegularVersion(
            id = client.idGenerator.generate(),
            time = time,
            author = user(),
            tree = tree,
            baseVersion = previousVersion,
            operations = operations,
        )
    }

    actual fun isDisposed() = disposed

    actual open fun dispose() {
        checkDisposed()
        disposed = true
        try {
            versionChangeDetector.dispose()
        } catch (ex: Exception) {
            LOG.error(ex) { "" }
        }
        convergenceWatchdog.cancel(false)
        coroutineScope.cancel("ReplicatedRepository disposed")
    }

    fun checkDisposed() {
        if (disposed) {
            throw RuntimeException("Already disposed, replicated client $client, branch: $branchReference, author: ${user()}")
        }
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
        private fun getHash(v: CLVersion?): String? {
            return v?.getContentHash()
        }
    }

    init {
        val versionHash = client[branchReference.getKey()]
        val store = client.storeCache
        var initialVersion = if (versionHash.isNullOrEmpty()) null else loadFromHash(versionHash, store)
        var initialTree: ITree
        if (initialVersion == null) {
            initialTree = CLTree.builder(store).useRoleIds(false).build()
            initialVersion = createVersion(initialTree, arrayOf(), null)
            client.asyncStore.put(branchReference.getKey(), initialVersion.getContentHash())
        } else {
            initialTree = initialVersion.getTree()
        }

        // prefetch to avoid HTTP request in command listener
        SharedExecutors.FIXED.execute { initialTree.getChildren(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE) }
        localVersion = initialVersion
        remoteVersion = initialVersion
        localBranch = PBranch(initialTree, client.idGenerator)
        localOTBranch = OTBranch(localBranch, client.idGenerator)
        merger = VersionMerger(store, client.idGenerator)
        versionChangeDetector = object : VersionChangeDetector(client, branchReference.getKey(), coroutineScope) {
            override fun processVersionChange(oldVersion: String?, newVersion: String?) {
                if (disposed) {
                    return
                }
                if (newVersion.isNullOrEmpty()) {
                    return
                }
                if (newVersion == getHash(remoteVersion)) {
                    return
                }
                val newRemoteVersion = loadFromHash(newVersion, store)
                val localBase = MutableObject<CLVersion?>()
                synchronized(mergeLock) {
                    localBase.value = localVersion
                    remoteVersion = newRemoteVersion
                }
                val doMerge = object : Supplier<Boolean> {
                    override fun get(): Boolean {
                        var mergedVersion: CLVersion
                        try {
                            mergedVersion = merger.mergeChange(localBase.value!!, newRemoteVersion)
                            if (LOG.isDebugEnabled) {
                                LOG.debug(
                                    String.format(
                                        "Merged remote %s with local %s -> %s",
                                        newRemoteVersion.getObjectHash().toString(),
                                        localBase.value!!.getObjectHash().toString(),
                                        mergedVersion.getObjectHash().toString(),
                                    ),
                                )
                            }
                        } catch (ex: Exception) {
                            LOG.error(ex) { "" }
                            mergedVersion = newRemoteVersion
                        }
                        val mergedTree = mergedVersion.getTree()
                        synchronized(mergeLock) {
                            remoteVersion = mergedVersion
                            if (localVersion == localBase.value) {
                                writeLocalVersion(mergedVersion)
                                writeRemoteVersion(mergedVersion)
                                return true
                            } else {
                                localBase.setValue(localVersion)
                                return false
                            }
                        }
                    }
                }

                // Avoid locking during the merge as it may require communication with the model server
                for (mergeAttempt in 0..2) {
                    if (doMerge.get()) {
                        return
                    }
                }
                synchronized(mergeLock) {
                    localBase.setValue(localVersion)
                    doMerge.get()
                }
            }
        }
        localOTBranch.addListener(object : IBranchListener {
            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                if (disposed) {
                    return
                }
                if (isEditing.get()) {
                    return
                }
                SharedExecutors.FIXED.execute {
                    if (isEditing.get()) {
                        return@execute
                    }
                    createAndMergeLocalVersion()
                }
            }
        })
        convergenceWatchdog = fixDelay(
            1000,
            Runnable {
                val localHash = if (localVersion == null) null else localVersion!!.getContentHash()
                val remoteHash = if (remoteVersion == null) null else remoteVersion!!.getContentHash()
                if (localHash == remoteHash) {
                    divergenceTime = 0
                } else {
                    divergenceTime++
                }
                if (divergenceTime > 5) {
                    synchronized(mergeLock) { divergenceTime = 0 }
                }
            },
        )
    }
}

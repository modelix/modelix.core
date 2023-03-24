package org.modelix.model.client2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.api.ModelQuery

class ReplicatedModel(val client: IModelClientV2, val branchRef: BranchReference, val query: ModelQuery? = null) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var state = State.New
    private lateinit var otBranch: OTBranch
    private lateinit var rawBranch: IBranch

    private lateinit var lastRemoteVersion: CLVersion
    private lateinit var localVersion: CLVersion
    private val mergeMutex = Mutex()

    private var author: String? = null

    fun getBranch(): IBranch {
        if (state != State.Started) throw IllegalStateException("state is $state")
        return otBranch
    }

    suspend fun start(): IBranch {
        if (state != State.New) throw IllegalStateException("already started")
        state = State.Starting

        author = client.getUserId()

        lastRemoteVersion = if (query == null) {
            client.pull(branchRef, null)
        } else {
            client.pull(branchRef, null, query)
        } as CLVersion
        localVersion = lastRemoteVersion
        rawBranch = PBranch(lastRemoteVersion.getTree(), client.getIdGenerator())
        otBranch = OTBranch(rawBranch, client.getIdGenerator(), lastRemoteVersion.store)

        scope.launch {
            while (state != State.Disposed) {
                val newRemoteVersion = if (query == null) {
                    client.poll(branchRef, lastRemoteVersion)
                } else {
                    client.poll(branchRef, lastRemoteVersion, query)
                } as CLVersion
                remoteVersionReceived(newRemoteVersion)
            }
        }

        rawBranch.addListener(object : IBranchListener {
            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                checkDisposed()
                scope.launch {
                    pushLocalChanges()
                }
            }
        })

        state = State.Started
        return otBranch
    }

    private fun checkDisposed() {
        if (state == State.Disposed) throw IllegalStateException("disposed")
    }

    fun dispose() {
        if (state == State.Disposed) return
        state = State.Disposed
        scope.cancel("disposed")
    }

    private suspend fun remoteVersionReceived(newRemoteVersion: CLVersion) {
        checkDisposed()
        if (lastRemoteVersion.getShaHash() == newRemoteVersion.getShaHash()) return
        lastRemoteVersion = newRemoteVersion
        mergeMutex.withLock {
            if (newRemoteVersion.getShaHash() != localVersion.getShaHash()) {
                otBranch.runWrite {
                    applyPendingLocalChanges()
                    localVersion = VersionMerger(newRemoteVersion.store, client.getIdGenerator())
                        .mergeChange(localVersion, newRemoteVersion)
                    rawBranch.writeTransaction.tree = localVersion.tree
                }
            }
        }
    }

    private suspend fun pushLocalChanges() {
        checkDisposed()
        val createdVersion: CLVersion?
        mergeMutex.withLock {
            createdVersion = applyPendingLocalChanges()
        }
        if (createdVersion != null) {
            remoteVersionReceived(client.push(branchRef, createdVersion) as CLVersion)
        }
    }

    private fun applyPendingLocalChanges(): CLVersion? {
        checkDisposed()
        require(mergeMutex.isLocked)
        val createdVersion = otBranch.computeRead {
            val (ops, newTree) = otBranch.operationsAndTree
            if (ops.isEmpty()) return@computeRead null
            CLVersion.createRegularVersion(
                id = client.getIdGenerator().generate(),
                author = author,
                tree = newTree as CLTree,
                baseVersion = localVersion,
                operations = ops.map { it.getOriginalOp() }.toTypedArray()
            )
        }
        if (createdVersion != null) {
            localVersion = createdVersion
        }
        return createdVersion
    }

    private enum class State {
        New,
        Starting,
        Started,
        Disposed
    }
}

fun IModelClientV2.getReplicatedModel(branchRef: BranchReference, query: ModelQuery? = null): ReplicatedModel {
    return ReplicatedModel(this, branchRef, query)
}

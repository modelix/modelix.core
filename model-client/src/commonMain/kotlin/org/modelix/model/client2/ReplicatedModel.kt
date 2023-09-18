/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.client2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import kotlin.coroutines.cancellation.CancellationException

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
            var nextDelayMs: Long = 0
            while (state != State.Disposed) {
                if (nextDelayMs > 0) delay(nextDelayMs)
                try {
                    val newRemoteVersion = if (query == null) {
                        client.poll(branchRef, lastRemoteVersion)
                    } else {
                        client.poll(branchRef, lastRemoteVersion, query)
                    } as CLVersion
                    remoteVersionReceived(newRemoteVersion)
                    nextDelayMs = 0
                } catch (ex: CancellationException) {
                    LOG.debug { "Stop to poll branch $branchRef after disposing." }
                    throw ex
                } catch (ex: Throwable) {
                    LOG.error(ex) { "Failed to poll branch $branchRef" }
                    nextDelayMs = (nextDelayMs * 3 / 2).coerceIn(1000, 30000)
                }
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
        if (lastRemoteVersion.getContentHash() == newRemoteVersion.getContentHash()) return
        lastRemoteVersion = newRemoteVersion
        mergeMutex.withLock {
            if (newRemoteVersion.getContentHash() != localVersion.getContentHash()) {
                otBranch.runWrite {
                    applyPendingLocalChanges()
                    try {
                        localVersion = VersionMerger(newRemoteVersion.store, client.getIdGenerator())
                            .mergeChange(localVersion, newRemoteVersion)
                    } catch (ex: Exception) {
                        LOG.warn(ex) { "Failed to merge remote version $newRemoteVersion into local version $localVersion. Resetting to remote version." }
                        localVersion = newRemoteVersion
                    }
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
            remoteVersionReceived(client.push(branchRef, createdVersion, baseVersion = lastRemoteVersion) as CLVersion)
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
                operations = ops.map { it.getOriginalOp() }.toTypedArray(),
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
        Disposed,
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger { }
    }
}

fun IModelClientV2.getReplicatedModel(branchRef: BranchReference): ReplicatedModel {
    return ReplicatedModel(this, branchRef)
}

@Deprecated("ModelQuery is not supported and ignored", ReplaceWith("getReplicatedModel(branchRef)"))
fun IModelClientV2.getReplicatedModel(branchRef: BranchReference, query: ModelQuery?): ReplicatedModel {
    return ReplicatedModel(this, branchRef, query)
}

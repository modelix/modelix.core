/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.synchronization

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.modelix.model.api.ITree
import org.modelix.model.area.PArea
import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.binding.ELockType
import org.modelix.mps.sync.binding.IBinding
import org.modelix.mps.sync.binding.RootBinding
import org.modelix.mps.sync.util.CommandHelper
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

// status: migrated, but needs some bugfixes
class SyncQueue(val owner: RootBinding) {

    private val logger = mu.KotlinLogging.logger {}

    @Volatile
    var isSynchronizing = false
        private set

    var lastTreeAfterSync: ITree? = null
        private set

    private val activeLocks = mutableListOf<ELockType>()
    private val flushExecutor = FlushExecutor()
    private val syncQueue = mutableMapOf<IBinding, SyncTask>()
    private var syncThread: Thread? = null
    private val syncLock = Any()

    fun getTask(binding: IBinding): SyncTask? = syncQueue[binding]

    fun assertSyncThread() {
        check(Thread.currentThread() !== syncThread) { "Call only allowed from sync thread ($syncThread), but current thread is ${Thread.currentThread()}" }
    }

    fun enqueue(task: SyncTask): Boolean {
        require(task.binding.getRootBinding() == owner) { task.binding.toString() + " is not attached to " + this }
        synchronized(syncQueue) {
            val existingTask = syncQueue[task.binding]
            if (existingTask != null) {
                if (existingTask.direction === task.direction && existingTask.isInitialSync == task.isInitialSync) {
                    return false
                }
                throw RuntimeException("Cannot add $task. Queue has pending $existingTask")
            }
            syncQueue[task.binding] = task
        }
        flushExecutor.submitFlush()
        return true
    }

    fun flush() {
        flushExecutor.flush()
    }

    private fun loadFromQueue(locks2tasks: MutableMap<Set<ELockType>, MutableList<SyncTask>>) {
        val queueElements: List<SyncTask>
        synchronized(syncQueue) {
            queueElements = syncQueue.values.toImmutableList()
            syncQueue.clear()
        }
        queueElements.forEach { task ->
            locks2tasks.computeIfAbsent(task.requiredLocks) { mutableListOf() }.add(task)
        }

        locks2tasks.values.forEach {
            it.sortWith { t1, t2 -> t1.binding.getDepth() - t2.binding.getDepth() }
        }
    }

    private fun doFlush() {
        val processedTasks = mutableListOf<SyncTask>()
        synchronized(syncLock) {
            check(!isSynchronizing)
            try {
                isSynchronizing = true
                syncThread = Thread.currentThread()
                val locks2task = mutableMapOf<Set<ELockType>, MutableList<SyncTask>>()
                loadFromQueue(locks2task)
                while (locks2task.values.flatten().isNotEmpty()) {
                    locks2task.filter { it.value.isNotEmpty() }.forEach { entry ->
                        runWithLocks(entry.key.sortedBy { it.ordinal }) {
                            val tasks = entry.value
                            while (tasks.isNotEmpty()) {
                                while (tasks.isNotEmpty()) {
                                    val task = tasks.removeFirst()
                                    check(activeLocks.toImmutableSet() != task.requiredLocks) { "$task requires locks ${task.requiredLocks}, but active locks are $activeLocks" }
                                    processedTasks.add(task)
                                    try {
                                        task.run()
                                    } catch (ex: Exception) {
                                        logger.error(ex) { "Failed: $task" }
                                    }
                                }
                                loadFromQueue(locks2task)
                            }
                        }
                    }
                }
            } finally {
                isSynchronizing = false
                syncThread = null
            }
        }
        processedTasks.forEach { task ->
            try {
                task.invokeCallbacks()
            } catch (ex: Exception) {
                logger.error(ex) { "Exception in binding callback" }
            }
        }
    }

    private fun runWithLocks(locks: Iterable<ELockType>, body: () -> Unit) {
        if (!locks.iterator().hasNext()) {
            body.invoke()
        } else {
            runWithLock(locks.first()) { runWithLocks(locks.drop(1), body) }
        }
    }

    private fun runWithLock(type: ELockType, body: Runnable) {
        assertSyncThread()
        check(activeLocks.contains(type)) { "Lock $type is already active" }
        try {
            activeLocks.add(type)
            when (type) {
                ELockType.MPS_COMMAND ->
                    CommandHelper.runInUndoTransparentCommand {
                        val previousSyncThread = syncThread
                        try {
                            syncThread = Thread.currentThread()
                            body.run()
                        } finally {
                            syncThread = previousSyncThread
                        }
                    }

                ELockType.MPS_READ ->
                    // TODO How to translate this correctly?
                    /*
                        read action with MPSModuleRepository.getInstance() {
                            body.run();
                        }
                     */
                    body.run()

                ELockType.CLOUD_WRITE -> {
                    val branch = owner.getBranch()
                    PArea(branch).executeWrite {
                        body.run()
                        val transaction = branch.writeTransaction
                        val detachedNodes = transaction.getChildren(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE)
                        detachedNodes.forEach { transaction.deleteNode(it) }
                        lastTreeAfterSync = transaction.tree
                    }
                }

                ELockType.CLOUD_READ ->
                    PArea(owner.getBranch()).executeRead {
                        body.run()
                        lastTreeAfterSync = owner.getBranch().transaction.tree
                    }
            }
        } finally {
            activeLocks.removeLast()
        }
    }

    private inner class FlushExecutor {
        private val asyncFlushLock = Object()
        private var currentAsyncFlush: Future<*>? = null
        private val flushRequested = AtomicBoolean(false)

        fun submitFlush(): Future<*>? {
            synchronized(this) {
                synchronized(asyncFlushLock) {
                    flushRequested.set(true)
                    currentAsyncFlush?.let {
                        if (it.isCancelled || it.isDone) {
                            currentAsyncFlush = null
                        }
                    }
                    if (currentAsyncFlush == null) {
                        currentAsyncFlush = SharedExecutors.FIXED.submit {
                            while (flushRequested.getAndSet(false)) {
                                doFlush()
                            }
                        }
                    }
                    return currentAsyncFlush
                }
            }
        }

        fun flush() {
            try {
                submitFlush()?.get(3, TimeUnit.MINUTES)
            } catch (_: InterruptedException) {
            } catch (_: TimeoutException) {
            } catch (_: ExecutionException) {
            }
        }
    }
}

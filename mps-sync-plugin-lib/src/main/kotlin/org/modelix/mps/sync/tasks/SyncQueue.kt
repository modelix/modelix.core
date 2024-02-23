/*
 * Copyright (c) 2024.
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

package org.modelix.mps.sync.tasks

import com.intellij.util.containers.headTail
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.modelix.ReplicatedModelRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.MpsCommandHelper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object SyncQueue {

    private val logger = KotlinLogging.logger {}

    private val activeSyncThreadsWithSyncDirection = ConcurrentHashMap<Thread, SyncDirection>()
    private val tasks = ConcurrentLinkedQueue<SyncTask>()

    fun enqueue(
        requiredLocks: LinkedHashSet<SyncLock>,
        syncDirection: SyncDirection,
        inspectionMode: InspectionMode = InspectionMode.OFF,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val task = SyncTask(requiredLocks, syncDirection, action)
        enqueue(task, inspectionMode)
        return ContinuableSyncTask(task)
    }

    fun enqueueBlocking(
        requiredLocks: LinkedHashSet<SyncLock>,
        syncDirection: SyncDirection,
        inspectionMode: InspectionMode = InspectionMode.OFF,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val task = SyncTask(requiredLocks, syncDirection, action)
        enqueueBlocking(task, inspectionMode)
        return ContinuableSyncTask(task)
    }

    fun enqueue(task: SyncTask, inspectionMode: InspectionMode) {
        /**
         * If we have to check the execution thread, then do not schedule Task if it is initiated on a Thread that is
         * running a synchronization and the sync direction is the opposite of what is running on the thread already.
         * This might be a symptom of a "Table tennis" (ping-pong) effect in which a change in MPS triggers a change
         * in Modelix which triggers a change in MPS again via the *ChangeListener and ModelixTreeChangeVisitor chains
         * registered in MPS and in Modelix, respectively.
         *
         * Because the SyncTasks are executed on separate threads by the ExecutorService (see SharedExecutors.FIXED),
         * there is a very little chance of missing an intended change on other side. With other words: there is very
         * little chance that it makes sense that on the same thread two SyncTasks occur.
         */
        if (inspectionMode == InspectionMode.CHECK_EXECUTION_THREAD) {
            val taskSyncDirection = task.syncDirection
            val runningSyncDirection = activeSyncThreadsWithSyncDirection[Thread.currentThread()]

            val noTaskIsRunning = runningSyncDirection == null
            val runningTaskDirectionIsTheSame = taskSyncDirection == runningSyncDirection
            val isNoneDirection = taskSyncDirection == SyncDirection.NONE || runningSyncDirection == SyncDirection.NONE
            if (noTaskIsRunning || isNoneDirection || runningTaskDirectionIsTheSame) {
                enqueueAndFlush(task)
            } else {
                task.result.complete(null)
            }
        } else {
            enqueueAndFlush(task)
        }
    }

    private fun enqueueBlocking(task: SyncTask, inspectionMode: InspectionMode) {
        enqueue(task, inspectionMode)
        task.result.get()
    }

    private fun enqueueAndFlush(task: SyncTask) {
        tasks.add(task)
        scheduleFlush()
    }

    private fun scheduleFlush() {
        SharedExecutors.FIXED.submit {
            doFlush()
        }
    }

    private fun doFlush() {
        while (!tasks.isEmpty()) {
            val task = tasks.poll() ?: return
            runWithLocks(task.sortedLocks, task)
        }
    }

    private fun runWithLocks(locks: LinkedHashSet<SyncLock>, task: SyncTask) {
        val taskResult = task.result

        if (locks.isEmpty()) {
            val result = task.action.invoke(task.previousTaskResult)
            if (result is CompletableFuture<*> && result.isCompletedExceptionally) {
                result.handle { _, throwable -> taskResult.completeExceptionally(throwable) }
            } else {
                taskResult.complete(result)
            }
        } else {
            val lockHeadAndTail = locks.toList().headTail()
            val lockHead = lockHeadAndTail.first

            runWithLock(lockHead) {
                val currentThread = Thread.currentThread()
                val wasAddedHere = !activeSyncThreadsWithSyncDirection.containsKey(currentThread)
                if (wasAddedHere) {
                    activeSyncThreadsWithSyncDirection[currentThread] = task.syncDirection
                }

                try {
                    val lockTail = lockHeadAndTail.second
                    runWithLocks(LinkedHashSet(lockTail), task)
                } catch (t: Throwable) {
                    logger.error(t) { "Exception in task on $currentThread, Thread ID ${currentThread.id}" }

                    if (!taskResult.isCompletedExceptionally) {
                        taskResult.completeExceptionally(t)
                    }

                    throw t
                } finally {
                    if (wasAddedHere) {
                        // do not remove threads that were registered somewhere else
                        activeSyncThreadsWithSyncDirection.remove(currentThread)
                    }
                }
            }
        }
    }

    private fun runWithLock(lock: SyncLock, runnable: () -> Unit) {
        when (lock) {
            SyncLock.MPS_WRITE -> MpsCommandHelper.runInUndoTransparentCommand(runnable)
            SyncLock.MPS_READ -> ActiveMpsProjectInjector.activeMpsProject!!.modelAccess.runReadAction(runnable)
            SyncLock.MODELIX_READ -> ReplicatedModelRegistry.model!!.getBranch().runRead(runnable)
            SyncLock.MODELIX_WRITE -> ReplicatedModelRegistry.model!!.getBranch().runWrite(runnable)
            SyncLock.NONE -> runnable.invoke()
        }
    }
}

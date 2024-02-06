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

package org.modelix.mps.sync.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.headTail
import jetbrains.mps.util.containers.ConcurrentHashSet
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.modelix.ReplicatedModelRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.MpsCommandHelper
import java.util.concurrent.ConcurrentLinkedQueue

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object SyncQueue {

    private val logger = logger<SyncQueue>()

    private val activeSyncThreads = ConcurrentHashSet<Thread>()
    private val tasks = ConcurrentLinkedQueue<SyncTask>()

    fun enqueue(
        requiredLocks: LinkedHashSet<SyncLock>,
        checkExecutionThread: Boolean = false,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val task = SyncTask(requiredLocks, action)
        enqueue(task, checkExecutionThread)
        return ContinuableSyncTask(task, this)
    }

    fun enqueueBlocking(
        requiredLocks: LinkedHashSet<SyncLock>,
        checkExecutionThread: Boolean = false,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val task = SyncTask(requiredLocks, action)
        enqueueBlocking(task, checkExecutionThread)
        return ContinuableSyncTask(task, this)
    }

    fun enqueue(task: SyncTask, checkExecutionThread: Boolean) {
        /**
         * If we have to check the execution thread, then do not schedule Task if it is initiated on a Thread that is
         * running a synchronization. This might be a symptom of a "Table tennis" (ping-pong) effect in which a change
         * in MPS triggers a change in Modelix which triggers a change in MPS again via the *ChangeListener and
         * ModelixTreeChangeVisitor chains registered in MPS and in Modelix, respectively.
         *
         * Because the SyncTasks are executed on separate threads by the ExecutorService (see SharedExecutors.FIXED),
         * there is a very little chance of missing an intended change on other side. With other words: there is very
         * little chance that it makes sense that on the same thread two SyncTasks occur.
         */
        if (checkExecutionThread && activeSyncThreads.contains(Thread.currentThread())) {
            task.result.complete(null)
        } else {
            tasks.add(task)
            scheduleFlush()
        }
    }

    fun enqueueBlocking(task: SyncTask, checkExecutionThread: Boolean) {
        enqueue(task, checkExecutionThread)
        task.result.get()
    }

    private fun scheduleFlush() {
        SharedExecutors.FIXED.submit {
            doFlush()
        }
    }

    private fun doFlush() {
        activeSyncThreads.add(Thread.currentThread())

        while (!tasks.isEmpty()) {
            val task = tasks.poll()
            runWithLocks(task.sortedLocks, task)
        }

        activeSyncThreads.remove(Thread.currentThread())
    }

    private fun runWithLocks(locks: LinkedHashSet<SyncLock>, task: SyncTask) {
        val taskResult = task.result

        if (locks.isEmpty()) {
            val result = task.action.invoke(task.previousTaskResult)
            taskResult.complete(result)
        } else {
            val lockHeadAndTail = locks.toList().headTail()
            val lockHead = lockHeadAndTail.first

            runWithLock(lockHead) {
                val currentThread = Thread.currentThread()
                val wasAddedHere = activeSyncThreads.add(currentThread)

                try {
                    val lockTail = lockHeadAndTail.second
                    runWithLocks(LinkedHashSet(lockTail), task)
                } catch (t: Throwable) {
                    logger.error("Exception in task on $currentThread, Thread ID ${currentThread.id}", t)

                    if (!taskResult.isCompletedExceptionally) {
                        taskResult.completeExceptionally(t)
                    }

                    throw t
                } finally {
                    if (wasAddedHere) {
                        // do not remove threads that were registered somewhere else (i.e. above in the SyncQueue class)
                        activeSyncThreads.remove(currentThread)
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

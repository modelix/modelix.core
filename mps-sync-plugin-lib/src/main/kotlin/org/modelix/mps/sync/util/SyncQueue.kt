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

import com.intellij.util.containers.headTail
import jetbrains.mps.util.containers.ConcurrentHashSet
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.ReplicatedModelRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.MpsCommandHelper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object SyncQueue {

    private val activeSyncThreads = ConcurrentHashSet<Thread>()
    private val tasks = ConcurrentLinkedQueue<SyncTask>()

    fun enqueue(requiredLocks: LinkedHashSet<SyncLock>, checkExecutionThread: Boolean = false, action: Runnable) {
        enqueue(SyncTask(requiredLocks, action), checkExecutionThread)
    }

    fun enqueueBlocking(
        requiredLocks: LinkedHashSet<SyncLock>,
        checkExecutionThread: Boolean = false,
        action: Runnable,
    ) {
        // submit the task
        val future = enqueue(SyncTask(requiredLocks, action), checkExecutionThread)

        // wait for completion (will throw exception if task threw exception; otherwise returns upon successful completion)
        future.get()
    }

    private fun enqueue(task: SyncTask, checkExecutionThread: Boolean): Future<*> {
        /**
         * If we have to check the execution thread, then do not schedule Task if it is initiated on a Thread that is
         * running a synchronization. This might be a symptom of a "Table tennis" (ping-pong) effect in which a change
         * in MPS triggers a change in Modelix which triggers a change in MPS again via the *ChangeListener and
         * ModelixTreeChangeVisitor chains registered in MPS and in Modelix, respectively.
         *
         * Because the SyncTasks are executed on separate threads by the ExecutorService (see SharedExecutors.FIXED),
         * there is a very little chance of missing an intended change on other side. With other words: there is very
         * little chance that it makes sense that on the same thread two SyncTasks occur.
         *
         * WARNING: This might be tricky to refactor, if we change to coroutines instead of Threads in the future.
         */
        if (checkExecutionThread && activeSyncThreads.contains(Thread.currentThread())) {
            val future = CompletableFuture<Unit?>()
            future.complete(null)
            return future
        }

        tasks.add(task)
        return scheduleFlush()
    }

    private fun scheduleFlush(): Future<*> {
        // TODO maybe refactor to coroutines, because they are more idiomatic in kotlin
        // TODO don't forget to shut them down in SyncServiceImpl.dispose (or remove the old shutdown there)
        return SharedExecutors.FIXED.submit {
            // TODO test what happens if an exception is thrown?
            // TODO does it fly outside to the enqueue method?
            // TODO do we continue executing the other tasks?
            // TODO is only that task going to be aborted that threw the exception?
            doFlush()
        }
    }

    private fun doFlush() {
        activeSyncThreads.add(Thread.currentThread())

        while (!tasks.isEmpty()) {
            val task = tasks.poll()
            runWithLocks(task.sortedLocks, task.action)
        }

        activeSyncThreads.remove(Thread.currentThread())
    }

    private fun runWithLocks(locks: LinkedHashSet<SyncLock>, runnable: Runnable) {
        if (locks.isEmpty()) {
            runnable.run()
        } else {
            val lockHeadAndTail = locks.toList().headTail()
            val lockHead = lockHeadAndTail.first

            runWithLock(lockHead) {
                val lockTail = lockHeadAndTail.second
                runWithLocks(LinkedHashSet(lockTail), runnable)
            }
        }
    }

    private fun runWithLock(lock: SyncLock, runnable: () -> Unit) {
        val runnableWithRegisteredThread = {
            val wasAddedHere = activeSyncThreads.add(Thread.currentThread())
            try {
                runnable.invoke()
            } finally {
                if (wasAddedHere) {
                    // do not remove threads that were registered somewhere else (i.e. above in the SyncQueue class)
                    activeSyncThreads.remove(Thread.currentThread())
                }
            }
        }

        when (lock) {
            SyncLock.MPS_WRITE -> MpsCommandHelper.runInUndoTransparentCommand(runnableWithRegisteredThread)
            SyncLock.MPS_READ ->
                ActiveMpsProjectInjector.activeMpsProject!!.modelAccess.runReadAction(runnableWithRegisteredThread)

            SyncLock.MODELIX_READ -> ReplicatedModelRegistry.model!!.getBranch().runRead(runnableWithRegisteredThread)
            SyncLock.MODELIX_WRITE ->
                ReplicatedModelRegistry.model!!.getBranch().runWrite(runnableWithRegisteredThread)

            SyncLock.CUSTOM -> runnableWithRegisteredThread.invoke()
        }
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
private data class SyncTask(
    private val requiredLocks: LinkedHashSet<SyncLock>,
    val action: Runnable,
) {
    val sortedLocks = LinkedHashSet<SyncLock>(requiredLocks.sortedWith(SnycLockComparator()))
}

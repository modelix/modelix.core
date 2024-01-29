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
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.ReplicatedModelRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.MpsCommandHelper
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

// TODO although SyncQueue is singleton, pass it on to the classes just like the nodeMap (just in case we do not want to have it as a singleton)
// TODO do the same refactoring for all other singleton classes, so the classes where they are used can be unit-tested more easily

// TODO How to avoid ping-pong changes, because this condition will almost never be true, thanks to flushing the tasks on SharedExecutors.FIXED (i.e. they will be always on a separate thread compared to where the change listeners are running)?
// TODO ping-pong changes = a change coming from modelix triggers a change in MPS that triggers a cloud sync that triggers a change back here.
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object SyncQueue {

    private val tasks = ConcurrentLinkedQueue<SyncTask>()

    // TODO handle errors that may occur while action execution
    fun enqueue(requiredLocks: LinkedHashSet<SyncLockType>, action: Runnable) {
        enqueue(SyncTask(requiredLocks, action))
    }

    fun enqueueBlocking(requiredLocks: LinkedHashSet<SyncLockType>, action: Runnable) {
        val barrier = CountDownLatch(1)

        // TODO test what happens with the original thread if Task throws an exception.
        // I.e. does that thread on MPS gets blocked and prevents MPS from closing?
        // Or MPS can close and the exception throws outside to the caller place?
        // Besides, it should prevent the thread from running further if an exception had occurred.
        val openBarrierCallback: ((Throwable?) -> Unit) = { throwable: Throwable? ->
            // open the barrier
            barrier.countDown()
            throwable?.let { throw throwable }
        }
        val task = SyncTask(requiredLocks, action, openBarrierCallback)

        // submit the task
        enqueue(task)

        // wait for completion
        barrier.await()
    }

    private fun enqueue(task: SyncTask) {
        tasks.add(task)
        scheduleFlush()
    }

    private fun scheduleFlush() {
        // TODO maybe refactor to coroutines, because they are more idiomatic in kotlin
        // TODO don't forget to shut them down in SyncServiceImpl.dispose (or remove the old shutdown there)
        SharedExecutors.FIXED.submit {
            doFlush()
        }
    }

    private fun doFlush() {
        while (!tasks.isEmpty()) {
            val task = tasks.poll()
            runWithLocks(task.requiredLocks, task.actionBody)
        }
    }

    private fun runWithLocks(locks: LinkedHashSet<SyncLockType>, runnable: Runnable) {
        val lockHeadAndTail = locks.toList().headTail()
        val lockHead = lockHeadAndTail.first
        runWithLock(lockHead, runnable)

        val lockTail = lockHeadAndTail.second
        if (lockTail.isNotEmpty()) {
            runWithLocks(LinkedHashSet(lockTail), runnable)
        }
    }

    private fun runWithLock(lock: SyncLockType, runnable: Runnable) {
        when (lock) {
            SyncLockType.MPS_WRITE -> MpsCommandHelper.runInUndoTransparentCommand(runnable)
            SyncLockType.MPS_READ -> ActiveMpsProjectInjector.activeMpsProject!!.modelAccess.runReadAction(runnable)
            SyncLockType.MODELIX_READ -> ReplicatedModelRegistry.model!!.getBranch().runReadT { runnable.run() }
            SyncLockType.MODELIX_WRITE -> ReplicatedModelRegistry.model!!.getBranch().runWriteT { runnable.run() }
            SyncLockType.CUSTOM -> runnable.run()
        }
    }
}

data class SyncTask(
    val requiredLocks: LinkedHashSet<SyncLockType>,
    private val action: Runnable,
    private val followupCallback: ((Throwable?) -> Unit)? = null,
) {
    val actionBody = Runnable {
        try {
            action.run()
            followupCallback?.invoke(null)
        } catch (t: Throwable) {
            if (followupCallback == null) {
                throw t
            } else {
                followupCallback.invoke(t)
            }
        }
    }
}

enum class SyncLockType {
    MPS_READ,
    MPS_WRITE,
    MODELIX_READ,
    MODELIX_WRITE,
    CUSTOM,
}

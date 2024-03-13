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

import org.modelix.kotlin.utils.UnstableModelixFeature
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncTask(
    requiredLocks: LinkedHashSet<SyncLock>,
    val syncDirection: SyncDirection,
    val action: SyncTaskAction,
    val previousTaskResultHolder: CompletableFuture<Any?>? = null,
    val result: CompletableFuture<Any?> = CompletableFuture(),
) {
    val sortedLocks = LinkedHashSet<SyncLock>(requiredLocks.sortedWith(SnycLockComparator()))
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ContinuableSyncTask(private val previousTask: SyncTask) {

    fun continueWith(
        requiredLocks: LinkedHashSet<SyncLock>,
        syncDirection: SyncDirection,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val continuation = CompletableFuture<Any?>()
        FuturesWaitQueue.add(continuation, setOf(getResult()), true)

        val task = SyncTask(requiredLocks, syncDirection, action, continuation)
        continuation.whenComplete { _, throwable ->
            if (throwable != null) {
                // if a predecessor failed then we have to fail the next task
                task.result.completeExceptionally(throwable)
            } else {
                // this will only run if previousTask is completed according to the FuturesWaitQueue
                SyncQueue.enqueue(task)
            }
        }

        return ContinuableSyncTask(task)
    }

    fun getResult() = previousTask.result
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
typealias SyncTaskAction = (Any?) -> Any?

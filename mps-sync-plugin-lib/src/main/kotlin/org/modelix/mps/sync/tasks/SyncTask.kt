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
    val previousTaskResult: Any? = null,
    val result: CompletableFuture<Any?> = CompletableFuture(),
) {
    val sortedLocks = LinkedHashSet<SyncLock>(requiredLocks.sortedWith(SnycLockComparator()))
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ContinuableSyncTask(private val previousTask: SyncTask) {

    fun continueWith(
        requiredLocks: LinkedHashSet<SyncLock>,
        syncDirection: SyncDirection,
        checkExecutionThread: Boolean = false,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val result = SyncQueue.enqueue(linkedSetOf(SyncLock.NONE), syncDirection) {
            // blocking wait for the result of the previous task
            val previousResult = getResult()
            val task = SyncTask(requiredLocks, syncDirection, action, previousResult)
            SyncQueue.enqueue(task, checkExecutionThread)

            task.result.get()
        }

        return result
    }

    fun getResult() = previousTask.result.get()
}

typealias SyncTaskAction = (Any?) -> Any?

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

import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import java.util.concurrent.CompletableFuture

/**
 * Iterates through each element of the Collection, and calls the `continuableSyncTaskProducer` function on them. I.e.
 * a synchronization operation from modelix to MPS or the other way around. After that, waits until all tasks are
 * completed.
 *
 * If any of them fails then it fails the resulting future. Otherwise, it completes the resulting future with null.
 *
 * Suggestion: use this method as the last statement of a short-living SyncTask, possibly without any SyncLocks to
 * avoid busy-waiting on the lock. The continuation of SyncTasks will take care of waiting for all futures to complete.
 * If you run a SyncTask on its own, then you have to manually wait for the task to complete.
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun <T> Collection<T>.waitForCompletionOfEachTask(
    collectResults: Boolean = false,
    continuableSyncTaskProducer: (T) -> ContinuableSyncTask,
) =
    this.asIterable().waitForCompletionOfEachTask(collectResults, continuableSyncTaskProducer)

/**
 * Iterates through each element of the Iterable, and calls the `continuableSyncTaskProducer` function on them. I.e.
 * a synchronization operation from modelix to MPS or the other way around. After that, waits until all tasks are
 * completed.
 *
 * If any of them fails then it fails the resulting future. Otherwise, it completes the resulting future with null.
 *
 * Suggestion: use this method as the last statement of a short-living SyncTask, possibly without any SyncLocks to
 * avoid busy-waiting on the lock. The continuation of SyncTasks will take care of waiting for all futures to complete.
 * If you run a SyncTask on its own, then you have to manually wait for the task to complete.
 *
 *  @param collectResults if true, then collects the Iterable<Any?> results of futureProducer
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun <T> Iterable<T>.waitForCompletionOfEachTask(
    collectResults: Boolean = false,
    continuableSyncTaskProducer: (T) -> ContinuableSyncTask,
) =
    this.waitForCompletionOfEach(collectResults) {
        continuableSyncTaskProducer.invoke(it).getResult()
    }

/**
 * Iterates through each element of the Iterable, and calls the `futureProducer` function on them. After that,
 * waits until all futures are completed.
 *
 * If any of them fails then it fails the resulting future. Otherwise, it completes the resulting future with null.
 *
 * Suggestion: use this method as the last statement of a short-living SyncTask, possibly without any SyncLocks to
 * avoid busy-waiting on the lock. The continuation of SyncTasks will take care of waiting for all futures to complete.
 * If you run a SyncTask on its own, then you have to manually wait for the task to complete.
 *
 * @param collectResults if true, then collects the Iterable<Any?> results of futureProducer into an Iterable<Any?>
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun <T> Iterable<T>.waitForCompletionOfEach(
    collectResults: Boolean = false,
    futureProducer: (T) -> CompletableFuture<Any?>,
): CompletableFuture<Any?> {
    val compositeFuture = CompletableFuture<Any?>()

    try {
        val futures = mutableSetOf<CompletableFuture<Any?>>()
        this.forEach {
            val future = futureProducer.invoke(it)
            futures.add(future)
        }
        FuturesWaitQueue.add(compositeFuture, futures, collectResults = collectResults)
    } catch (t: Throwable) {
        compositeFuture.completeExceptionally(t)
    }

    return compositeFuture
}

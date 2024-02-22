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
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun <T> Collection<T>.waitForCompletionOfEachTask(continuableSyncTaskProducer: (T) -> ContinuableSyncTask) =
    this.asIterable().waitForCompletionOfEachTask(continuableSyncTaskProducer)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun <T> Iterable<T>.waitForCompletionOfEachTask(continuableSyncTaskProducer: (T) -> ContinuableSyncTask) =
    this.waitForCompletionOfEach {
        continuableSyncTaskProducer.invoke(it).getResult()
    }

fun <T> Iterable<T>.waitForCompletionOfEach(futureProducer: (T) -> CompletableFuture<*>): CompletableFuture<*> {
    val compositeFuture = CompletableFuture<Any?>()

    try {
        // wait for all subtasks to complete
        val futures = mutableSetOf<CompletableFuture<*>>()
        this.forEach {
            val future = futureProducer.invoke(it)
            futures.add(future)
        }
        futures.forEach { it.join() }

        compositeFuture.complete(null)
    } catch (t: Throwable) {
        compositeFuture.completeExceptionally(t)
    }

    return compositeFuture
}

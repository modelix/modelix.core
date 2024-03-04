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

import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.util.completeWithDefault
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import java.util.stream.Stream

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object FuturesWaitQueue : Runnable, AutoCloseable {

    private val logger = KotlinLogging.logger {}
    private val threadPool = Executors.newSingleThreadExecutor()

    private val pauseObject = Object()

    private val continuations = LinkedBlockingQueue<FutureWithPredecessors>()

    init {
        threadPool.submit(this)
    }

    fun add(
        continuation: CompletableFuture<Any?>,
        predecessors: Set<CompletableFuture<Any?>>,
        fillContinuation: Boolean = false,
    ) {
        if (predecessors.isEmpty()) {
            continuation.completeWithDefault()
            return
        }

        continuations.add(FutureWithPredecessors(predecessors, FillableFuture(continuation, fillContinuation)))
        notifyThread()
    }

    override fun close() {
        threadPool.shutdownNow()
    }

    override fun run() {
        val executorThread = Thread.currentThread()
        try {
            while (!executorThread.isInterrupted) {
                while (!continuations.isEmpty()) {
                    if (executorThread.isInterrupted) {
                        throw InterruptedException()
                    }

                    val futureWithPredecessors = continuations.take()
                    val predecessors = futureWithPredecessors.predecessors

                    val fillableFuture = futureWithPredecessors.future
                    val continuation = fillableFuture.future

                    val failedPredecessor =
                        predecessors.firstOrNull { predecessor -> predecessor.isCompletedExceptionally }
                    if (failedPredecessor != null) {
                        failedPredecessor.handle { _, throwable -> continuation.completeExceptionally(throwable) }
                        continue
                    }

                    val anyCancelled = predecessors.any { predecessor -> predecessor.isCancelled }
                    if (anyCancelled) {
                        continuation.cancel(true)
                        continue
                    }

                    val allCompleted = predecessors.all { predecessor -> predecessor.isDone }
                    if (allCompleted) {
                        /**
                         * Check if there is any predecessor whose result (.get()) is a CompletableFuture. Replace such
                         * CompletableFutures with their result and put them at the end of the queue.
                         *
                         * In the normal case, such predecessors are created if Iterable<T>.waitForCompletionOfEach is
                         * the last statement of a SyncTask, that returns a CompletableFuture. This Future will be put
                         * inside SyncTask.result, which is a Future already. However, we are curious about the
                         * completion of the inner Future, thus we have to unpack it here.
                         *
                         * As a consequence of this feature, it is not possible to pass a CF from a SyncTask to a
                         * consecutive SyncTask via the predecessor's return and the successors input parameter, because
                         * this CF will always be unpacked until no more CFs are found.
                         */
                        val cfPredecessors = predecessors.filter { it.get() is CompletableFuture<*> }
                            .map { it.get() as CompletableFuture<Any?> }
                        if (cfPredecessors.isNotEmpty()) {
                            val normalPredecessors = predecessors.filter { it.get() !is CompletableFuture<*> }
                            val newPredecessors = Stream.concat(normalPredecessors.stream(), cfPredecessors.stream())
                                .collect(Collectors.toSet())

                            val nextRound = FutureWithPredecessors(newPredecessors, fillableFuture)
                            continuations.add(nextRound)
                            continue
                        }

                        val fillContinuation = fillableFuture.shallBeFilled
                        val result = if (fillContinuation) {
                            val candidate = predecessors.first()
                            if (candidate.isCompletedExceptionally) {
                                candidate.exceptionally { continuation.completeExceptionally(it) }
                                continue
                            }
                            candidate.get()
                        } else {
                            null
                        }

                        if (result == null) {
                            continuation.completeWithDefault()
                        } else {
                            continuation.complete(result)
                        }
                        continue
                    }

                    // re-queue item if it was not processed
                    continuations.add(futureWithPredecessors)
                }

                waitForNotification()
            }
        } catch (t: Throwable) {
            /**
             * TODO this might be normal (i.e. if it's an InterruptedException), but in other cases we have to
             * notify the user, because synchronization does not work without this class!
             */
            logger.error(t) { "BusyWaitQueue is shutting down, because it got an Exception" }
            continuations.forEach { it.future.future.completeExceptionally(t) }
            return
        }
    }

    private fun notifyThread() {
        synchronized(pauseObject) {
            pauseObject.notifyAll()
        }
    }

    private fun waitForNotification() {
        synchronized(pauseObject) {
            pauseObject.wait()
        }
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class FutureWithPredecessors(val predecessors: Set<CompletableFuture<Any?>>, val future: FillableFuture)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class FillableFuture(val future: CompletableFuture<Any?>, val shallBeFilled: Boolean = false)

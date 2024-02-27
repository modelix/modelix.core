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
import org.modelix.mps.sync.util.getActualResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object FuturesWaitQueue : Runnable, AutoCloseable {

    private val logger = KotlinLogging.logger {}
    private val threadPool = Executors.newFixedThreadPool(1)

    private val pauseObject = Object()

    private val continuationByPredecessors = ConcurrentHashMap<Set<CompletableFuture<Any?>>, FillableFuture>()

    init {
        threadPool.submit(this)
    }

    fun add(
        continuation: CompletableFuture<Any?>,
        predecessors: Set<CompletableFuture<Any?>>,
        fillContinuation: Boolean = false,
    ) {
        continuationByPredecessors[predecessors] = FillableFuture(continuation, fillContinuation)
        pauseObject.notify()
    }

    override fun close() {
        threadPool.shutdownNow()
    }

    override fun run() {
        val executorThread = Thread.currentThread()
        try {
            while (!executorThread.isInterrupted) {
                while (!continuationByPredecessors.isEmpty()) {
                    continuationByPredecessors.forEach {
                        if (executorThread.isInterrupted) {
                            // TODO test it
                            throw InterruptedException()
                        }

                        val predecessors = it.key
                        val continuation = it.value.future

                        val failedPredecessor =
                            predecessors.firstOrNull { predecessor -> predecessor.isCompletedExceptionally }
                        if (failedPredecessor != null) {
                            failedPredecessor.handle { _, throwable -> continuation.completeExceptionally(throwable) }
                            return@forEach remove(predecessors)
                        }

                        val anyCancelled = predecessors.any { predecessor -> predecessor.isCancelled }
                        if (anyCancelled) {
                            continuation.cancel(true)
                            return@forEach remove(predecessors)
                        }

                        val allCompleted = predecessors.all { predecessor -> predecessor.isDone }
                        if (allCompleted) {
                            val fillContinuation = it.value.shallBeFilled
                            val result = if (fillContinuation) {
                                predecessors.first().getActualResult()
                            } else {
                                null
                            }
                            continuation.complete(result)
                            remove(predecessors)
                        }
                    }
                }

                pauseObject.wait()
            }
        } catch (ex: InterruptedException) {
            logger.info { "BusyWaitQueue is shutting down, because it got interrupted" }
            continuationByPredecessors.values.forEach { it.future.completeExceptionally(ex) }
            return
        }
    }

    private fun remove(predecessors: Set<CompletableFuture<Any?>>) {
        continuationByPredecessors.remove(predecessors)
    }
}

data class FillableFuture(val future: CompletableFuture<Any?>, val shallBeFilled: Boolean = false)

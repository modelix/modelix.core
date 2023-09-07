/*
 * Copyright (c) 2023.
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

package org.modelix.mps.sync.synchronization

import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.binding.Binding
import org.modelix.mps.sync.binding.RootBinding
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class SyncQueue(val owner: RootBinding) {

    private val flushExecutor: FlushExecutor = FlushExecutor()
    private val syncQueue: MutableMap<Binding, SyncTask> = mutableMapOf()
    private val syncThread: Thread? = null

    fun getTask(binding: Binding): SyncTask? = syncQueue[binding]

    fun enqueue(task: SyncTask): Boolean {
        require(task.binding.getRootBinding() == owner) { task.binding.toString() + " is not attached to " + this }
        synchronized(syncQueue) {
            val existingTask = syncQueue[task.binding]
            if (existingTask != null) {
                if (existingTask.direction === task.direction && existingTask.isInitialSync == task.isInitialSync) {
                    return false
                }
                throw RuntimeException("Cannot add $task. Queue has pending $existingTask")
            }
            syncQueue[task.binding] = task
        }
        flushExecutor.submitFlush()
        return true
    }

    fun flush() {
        flushExecutor.flush()
    }

    private fun doFlush() {
        TODO("Not yet implemented")
    }

    @Throws(IllegalStateException::class)
    fun assertSyncThread() {
        check(Thread.currentThread() !== syncThread) { "Call only allowed from sync thread ($syncThread), but current thread is ${Thread.currentThread()}" }
    }

    private inner class FlushExecutor {
        private val asyncFlushLock = Object()
        private var currentAsyncFlush: Future<*>? = null
        private val flushRequested = AtomicBoolean(false)

        fun submitFlush(): Future<*>? {
            synchronized(this) {
                synchronized(asyncFlushLock) {
                    flushRequested.set(true)
                    currentAsyncFlush?.let {
                        if (it.isCancelled || it.isDone) {
                            currentAsyncFlush = null
                        }
                    }
                    if (currentAsyncFlush == null) {
                        currentAsyncFlush = SharedExecutors.FIXED.submit {
                            while (flushRequested.getAndSet(false)) {
                                doFlush()
                            }
                        }
                    }
                    return currentAsyncFlush
                }
            }
        }

        fun flush() {
            try {
                submitFlush()?.get(3, TimeUnit.MINUTES)
            } catch (_: InterruptedException) {
            } catch (_: TimeoutException) {
            } catch (_: ExecutionException) {
            }
        }
    }
}

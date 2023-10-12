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

import com.intellij.openapi.diagnostic.logger
import org.modelix.mps.sync.binding.Binding
import org.modelix.mps.sync.binding.ELockType
import java.util.Collections

// status: ready to test
class SyncTask(
    val binding: Binding,
    val direction: SyncDirection,
    initialSync: Boolean,
    requiredLocks: Set<ELockType>,
    private val implementation: Runnable,
) : Runnable {

    private val logger = logger<SyncTask>()
    private val callbacks: MutableList<Runnable> = mutableListOf()
    val isInitialSync: Boolean = initialSync
    val requiredLocks: Set<ELockType>
    private var state: State = State.NEW

    init {
        this.requiredLocks = Collections.unmodifiableSet(HashSet<ELockType>(requiredLocks))
    }

    override fun run() {
        check(state == State.NEW) { "Current state: $state" }
        try {
            state = State.RUNNING
            if (binding.isActive) {
                binding.runningTask = this
                implementation.run()
            } else {
                logger.warn("Skipped $this, because the binding is inactive")
                return
            }
        } finally {
            binding.runningTask = null
            state = State.DONE
        }
    }

    fun invokeCallbacks() {
        callbacks.forEach { callback ->
            try {
                callback.run()
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
        }
    }

    fun isDone(): Boolean = state === State.DONE

    fun isRunning(): Boolean = state === State.RUNNING

    fun whenDone(callback: Runnable?) = callback?.let { callbacks.add(it) }

    override fun toString(): String = "task[$binding, $direction, $requiredLocks]"
}

private enum class State {
    NEW, RUNNING, DONE
}

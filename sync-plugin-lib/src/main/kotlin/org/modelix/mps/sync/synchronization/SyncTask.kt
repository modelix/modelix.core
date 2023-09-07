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

import org.modelix.mps.sync.binding.BaseBinding
import org.modelix.mps.sync.binding.ELockType
import java.util.Collections

class SyncTask : Runnable {

    val binding: BaseBinding
    private val callbacks: MutableList<Runnable> = mutableListOf()
    val direction: SyncDirection
    private val implementation: Runnable
    val isInitialSync: Boolean
    private val requiredLocks: Set<ELockType>
    private val state: State = State.NEW

    constructor(
        binding: BaseBinding,
        direction: SyncDirection,
        initialSync: Boolean,
        requiredLocks: Set<ELockType>,
        implementation: Runnable,
    ) {
        this.binding = binding
        this.direction = direction
        this.isInitialSync = initialSync
        this.implementation = implementation
        this.requiredLocks = Collections.unmodifiableSet(HashSet<ELockType>(requiredLocks))
    }

    override fun run() {
        TODO("Not yet implemented")
    }

    fun isDone(): Boolean = state === State.DONE

    fun isRunning(): Boolean = state === State.RUNNING

    fun whenDone(callback: Runnable?) = callback?.let { callbacks.add(it) }
}

private enum class State {
    NEW, RUNNING, DONE
}

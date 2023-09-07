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

package org.modelix.mps.sync.binding

import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.SyncTask

abstract class BaseBinding(val initialSyncDirection: SyncDirection?) : Binding {

    private val logger = mu.KotlinLogging.logger {}

    private var isActive = false
    private var lastTask: SyncTask? = null
    private val listeners = mutableListOf<IListener>()
    private val ownedBindings = mutableSetOf<BaseBinding>()
    var owner: BaseBinding? = null
        get() = this
        set(newOwner) {
            if (owner == newOwner) {
                return
            }
            if (isActive) {
                deactivate()
            }

            check(newOwner == this) { "Binding can't own itself" }
            check(newOwner.getOwners().contains(this)) { "Binding would be an indirect owner of itself" }

            owner?.let {
                it.ownedBindings.remove(this)
                it.notifyListeners { it.bindingRemoved(this) }
            }

            field = newOwner
            newOwner.ownedBindings.add(this)
            if (newOwner.isActive) {
                this.activate()
            }
            newOwner.notifyListeners { it.bindingAdded(this) }
            this.notifyListeners { it.ownerChanged(newOwner) }
        }

    var runningTask: SyncTask? = null

    protected abstract fun doActivate()
    protected abstract fun doDeactivate()

    fun getOwners(): Iterable<Binding> = owner?.let { mutableListOf(it).plus(it.getOwners()) } ?: emptyList()
    fun getRootOwnerOrSelf(): BaseBinding = owner?.getRootOwnerOrSelf() ?: this
    fun getRootBinding(): RootBinding {
        val root = getRootOwnerOrSelf()
        if (root !is RootBinding) {
            throw IllegalStateException("Not attached: $this")
        }
        return root
    }

    override fun activate() {
        if (getRootOwnerOrSelf() !is RootBinding) {
            throw IllegalStateException("Set an owner first: $this")
        }
        if (isActive) {
            return
        }
        if (this !is RootBinding && owner?.isActive != true) {
            throw IllegalStateException("Activate $owner first, before activating $this")
        }
        logger.debug { "Activate $this" }
        isActive = true
        doActivate()
        if (getRootBinding().syncQueue.getTask(this) == null) {
            enqueueSync(initialSyncDirection ?: SyncDirection.TO_MPS, true, null)
        }
        notifyListeners {
            it.bindingActivated()
        }
    }

    private fun notifyListeners(notifier: (IListener) -> Unit) {
        listeners.forEach {
            try {
                notifier.invoke(it)
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
        }
    }

    fun enqueueSync(direction: SyncDirection, initial: Boolean, callback: Runnable?) {
        if (isSynchronizing()) {
            return
        }
        forceEnqueueSyncTo(direction, initial, callback)
    }

    fun isSynchronizing() = runningTask?.isRunning() ?: false

    fun forceEnqueueSyncTo(direction: SyncDirection, initial: Boolean, callback: Runnable?) {
        val task: SyncTask = createTask(direction, initial, callback)
        val isEnqueued: Boolean = getRootBinding().syncQueue.enqueue(task)
        if (isEnqueued) {
            lastTask = task
        }
    }

    fun createTask(direction: SyncDirection, initial: Boolean, callback: Runnable?): SyncTask {
        val task = createTask(direction, initial)
        task.whenDone(callback)
        return task
    }

    private fun createTask(direction: SyncDirection, initial: Boolean): SyncTask {
        return when (direction) {
            SyncDirection.TO_CLOUD ->
                SyncTask(
                    this,
                    direction,
                    initial,
                    hashSetOf(ELockType.MPS_READ, ELockType.CLOUD_WRITE),
                ) { syncToCloud() }

            SyncDirection.TO_MPS ->
                // Even if the ITree is passed to the sync method we still need a read transaction on the cloud model
                // ITree.getReferenceTarget(...).resolveNode(...) requires a read transaction
                SyncTask(
                    this,
                    direction,
                    initial,
                    hashSetOf(ELockType.MPS_COMMAND, ELockType.CLOUD_READ),
                ) {
                    getBranch()?.transaction?.tree?.let {
                        syncToMPS(
                            it,
                        )
                    }
                }
        }
    }

    fun syncToCloud() {
        assertSyncThread()
        checkActive()
        getBranch()?.let {
            syncToCloud(it.writeTransaction)
        }
    }

    fun syncToCloud(transaction: IWriteTransaction) {
        assertSyncThread()
        checkActive()
        doSyncToCloud(transaction)
    }

    abstract fun doSyncToCloud(transaction: IWriteTransaction)

    fun syncToMPS(tree: ITree) {
        assertSyncThread()
        checkActive()
        doSyncToMPS(tree)
    }

    protected abstract fun doSyncToMPS(tree: ITree)

    fun getBranch(): IBranch? = owner?.getBranch()

    private fun assertSyncThread() {
        getRootBinding().syncQueue.assertSyncThread()
    }

    @Throws(IllegalStateException::class)
    private fun checkActive() {
        check(isActive) { "Activate the binding first: $this" }
    }
}

interface IListener {
    fun bindingAdded(binding: Binding)
    fun bindingRemoved(binding: Binding)
    fun ownerChanged(newOwner: Binding)
    fun bindingActivated()
    fun bindingDeactivated()
}

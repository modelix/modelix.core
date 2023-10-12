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

import com.intellij.openapi.diagnostic.logger
import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.ICloudRepository
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.SyncTask
import java.util.Collections

// status: ready to test
abstract class Binding(val initialSyncDirection: SyncDirection?) : IBinding {

    private val logger = logger<Binding>()

    var isActive = false
        private set
    private var lastTask: SyncTask? = null
    private val listeners = mutableListOf<IListener>()
    val ownedBindings = mutableSetOf<Binding>()
    var owner: Binding? = null
        get() = this
        set(newOwner) {
            if (owner == newOwner || newOwner == null) {
                return
            }
            if (isActive) {
                deactivate()
            }

            check(newOwner != this) { "Binding can't own itself" }
            check(!newOwner.getOwners().contains(this)) { "Binding would be an indirect owner of itself" }

            owner?.let {
                it.ownedBindings.remove(this)
                it.notifyListeners { listener -> listener.bindingRemoved(this) }
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

    /**
     * It's more efficient to diff the tree only once and notify all bindings together about changes instead of calling
     * ITree.visitChanges in each binding.
     * First the visitor is notified about changes and then syncToMPS is called. The binding has to remember which model
     * elements are dirty.
     */
    abstract fun getTreeChangeVisitor(oldTree: ITree?, newTree: ITree?): ITreeChangeVisitor?

    protected abstract fun doSyncToCloud(transaction: IWriteTransaction)
    protected abstract fun doSyncToMPS(tree: ITree)

    protected fun assertSyncThread() = getRootBinding().syncQueue.assertSyncThread()

    fun getDepth(): Int = owner?.let { it.getDepth() + 1 } ?: 0

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

    fun createTask(direction: SyncDirection, initial: Boolean, callback: Runnable?): SyncTask {
        val task = createTask(direction, initial)
        task.whenDone(callback)
        return task
    }

    fun getRequiredSyncLocks(direction: SyncDirection?): Set<ELockType> {
        return direction?.let {
            when (direction) {
                SyncDirection.TO_CLOUD -> setOf(ELockType.MPS_READ, ELockType.CLOUD_WRITE)
                SyncDirection.TO_MPS ->
                    // Even if the ITree is passed to the sync method we still need a read transaction on the cloud model
                    // ITree.getReferenceTarget(...).resolveNode(...) requires a read transaction
                    setOf(ELockType.MPS_COMMAND, ELockType.CLOUD_READ)
            }
        } ?: Collections.emptySet()
    }

    fun enqueueSync(direction: SyncDirection, initial: Boolean, callback: Runnable?) {
        if (isSynchronizing()) {
            return
        }
        forceEnqueueSyncTo(direction, initial, callback)
    }

    fun forceEnqueueSyncTo(direction: SyncDirection, initial: Boolean, callback: Runnable?) {
        val task: SyncTask = createTask(direction, initial, callback)
        val isEnqueued: Boolean = getRootBinding().syncQueue.enqueue(task)
        if (isEnqueued) {
            lastTask = task
        }
    }

    private fun isDone(): Boolean = (lastTask == null || lastTask!!.isDone()) && ownedBindings.all { it.isDone() }

    protected open fun getBranch(): IBranch? = owner?.getBranch()

    @Throws(IllegalStateException::class)
    private fun checkActive() = check(!isActive) { "Activate the binding first: $this" }

    fun isSynchronizing() = runningTask?.isRunning() ?: false

    private fun syncToMPS(tree: ITree) {
        assertSyncThread()
        checkActive()
        doSyncToMPS(tree)
    }

    private fun syncToCloud(transaction: IWriteTransaction? = null) {
        assertSyncThread()
        checkActive()

        val tr = transaction ?: getBranch()?.writeTransaction
        tr?.let { doSyncToCloud(it) }
    }

    open fun getCloudRepository(): ICloudRepository? = owner?.getCloudRepository()

    private fun getOwners(): Iterable<IBinding> = owner?.let { mutableListOf(it).plus(it.getOwners()) } ?: emptyList()

    private fun getRootOwnerOrSelf(): Binding = owner?.getRootOwnerOrSelf() ?: this

    fun getRootBinding(): RootBinding {
        val root = getRootOwnerOrSelf()
        if (root !is RootBinding) {
            throw IllegalStateException("Not attached: $this")
        }
        return root
    }

    fun getAllBindings(): Iterable<Binding> =
        mutableListOf(this).plus(ownedBindings.flatMap { it.getAllBindings() })

    override fun activate(callback: Runnable?) {
        if (getRootOwnerOrSelf() !is RootBinding) {
            throw IllegalStateException("Set an owner first: $this")
        }
        if (isActive) {
            return
        }
        if (this !is RootBinding && owner?.isActive != true) {
            throw IllegalStateException("Activate $owner first, before activating $this")
        }
        logger.debug("Activate $this")
        isActive = true
        doActivate()
        if (getRootBinding().syncQueue.getTask(this) == null) {
            enqueueSync(initialSyncDirection ?: SyncDirection.TO_MPS, true, null)
        }
        notifyListeners {
            it.bindingActivated()
        }

        callback?.run()
    }

    override fun deactivate(callback: Runnable?) {
        if (!isActive) return

        logger.debug("Deactivate: $this")
        isActive = false
        ownedBindings.forEach { it.deactivate() }
        doDeactivate()
        notifyListeners { it.bindingDeactivated() }
        callback?.run()
    }

    fun addListener(listener: IListener) = listeners.add(listener)

    fun removeListener(listener: IListener) = listeners.remove(listener)

    private fun notifyListeners(notifier: (IListener) -> Unit) {
        listeners.forEach {
            try {
                notifier.invoke(it)
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
        }
    }

    interface IListener {
        fun bindingAdded(binding: IBinding)
        fun bindingRemoved(binding: IBinding)
        fun ownerChanged(newOwner: IBinding)
        fun bindingActivated()
        fun bindingDeactivated()
    }
}

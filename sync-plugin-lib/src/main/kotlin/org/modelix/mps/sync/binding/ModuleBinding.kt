package org.modelix.mps.sync.binding

import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SPropertyChangeEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.Synchronizer

abstract class ModuleBinding(val moduleNodeId: Long, initialSyncDirection: SyncDirection) :
    BaseBinding(initialSyncDirection) {

    private val logger = mu.KotlinLogging.logger {}

    private val moduleListener = object : SModuleListener {

        override fun modelAdded(module: SModule, model: SModel) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun modelRemoved(module: SModule, ref: SModelReference) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }

        override fun modelRenamed(module: SModule, model: SModel, oldRef: SModelReference) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
        }
    }

    private val ownedBindings = hashSetOf<Binding>()

    override fun doActivate() {
        getModule().addModuleListener(moduleListener)
        if (getRootBinding().syncQueue.getTask(this) == null) {
            enqueueSync(initialSyncDirection ?: SyncDirection.TO_MPS, true, null)
        }
    }

    abstract fun getModule(): SModule

    var nodeChangedListener = object : SNodeChangeListener {
        override fun propertyChanged(event: SPropertyChangeEvent) {
            TODO("Not yet implemented")
        }

        override fun referenceChanged(event: SReferenceChangeEvent) {
            TODO("Not yet implemented")
        }

        override fun nodeAdded(event: SNodeAddEvent) {
            TODO("Not yet implemented")
        }

        override fun nodeRemoved(event: SNodeRemoveEvent) {
            TODO("Not yet implemented")
        }
    }

    override fun deactivate() {
        TODO("Not yet implemented")
    }

    override fun doDeactivate() {
        getModule().removeModuleListener(moduleListener)
    }

    override fun doSyncToMPS(tree: ITree) {
        if (runningTask!!.isInitialSync &&
            getModelsSynchronizer().getMPSChildren().iterator().hasNext() &&
            !getModelsSynchronizer().getCloudChildren(tree).iterator().hasNext()
        ) {
            // TODO remove this workaround
            forceEnqueueSyncTo(SyncDirection.TO_CLOUD, true, null)
            return
        }
        val mappings = getModelsSynchronizer().syncToMPS(tree)
        updateBindings(mappings, SyncDirection.TO_MPS)
    }

    override fun doSyncToCloud(transaction: IWriteTransaction) {
        val mappings = getModelsSynchronizer().syncToCloud(transaction)
        updateBindings(mappings, SyncDirection.TO_CLOUD)
    }

    private fun updateBindings(mappings: Map<Long, SModel>, syncDirection: SyncDirection) {
        val bindings = mutableMapOf<Long, ModelBinding>()
        ownedBindings.filterIsInstance<ModelBinding>().forEach { bindings[it.modelNodeId] = it }

        val toAdd = mappings.keys.minus(bindings.keys).toList()
        val toRemove = bindings.keys.minus(mappings.keys).toList()

        toRemove.forEach {
            val binding = bindings[it]!!
            binding.deactivate()
            binding.owner = null
        }

        toAdd.forEach {
            val binding = ModelBinding(it, mappings[it]!!, syncDirection)
            binding.owner = this
            binding.activate()
        }
    }

    protected fun getModelsSynchronizer(): Synchronizer<SModel> = ModelsSynchronizer(moduleNodeId, getModule())
}

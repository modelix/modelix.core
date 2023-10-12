package org.modelix.mps.sync.binding

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListenerBase
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.Synchronizer

// status: ready to test
abstract class ModuleBinding(val moduleNodeId: Long, initialSyncDirection: SyncDirection) :
    Binding(initialSyncDirection) {

    abstract val module: SModule

    private val logger = logger<ModuleBinding>()

    private val moduleListener = object : SModuleListenerBase() {

        override fun modelAdded(module: SModule, model: SModel) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
        }

        override fun modelRemoved(module: SModule, ref: SModelReference) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
        }

        override fun modelRenamed(module: SModule, model: SModel, oldRef: SModelReference) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
        }
    }

    private val treeChangeVisitor = object : ITreeChangeVisitor {
        override fun childrenChanged(nodeId: Long, role: String?) {
            assertSyncThread()
            if (nodeId == moduleNodeId) {
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }
        }

        override fun containmentChanged(nodeId: Long) {}
        override fun referenceChanged(nodeId: Long, role: String) {}
        override fun propertyChanged(nodeId: Long, role: String) {}
    }

    override fun getTreeChangeVisitor(oldTree: ITree?, newTree: ITree?): ITreeChangeVisitor? = treeChangeVisitor

    override fun doActivate() {
        module.addModuleListener(moduleListener)
        if (getRootBinding().syncQueue.getTask(this) == null) {
            enqueueSync(initialSyncDirection ?: SyncDirection.TO_MPS, true, null)
        }
    }

    override fun doDeactivate() {
        module.removeModuleListener(moduleListener)
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

    private fun getModelsSynchronizer(): Synchronizer<SModel> = ModelsSynchronizer(moduleNodeId, module)

    override fun toString() = "Module: ${java.lang.Long.toHexString(moduleNodeId)} -> ${module.moduleName}"
}

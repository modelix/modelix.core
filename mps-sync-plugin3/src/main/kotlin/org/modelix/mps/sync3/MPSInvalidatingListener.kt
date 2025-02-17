package org.modelix.mps.sync3

import jetbrains.mps.smodel.SModelInternal
import jetbrains.mps.smodel.event.SModelChildEvent
import jetbrains.mps.smodel.event.SModelDevKitEvent
import jetbrains.mps.smodel.event.SModelImportEvent
import jetbrains.mps.smodel.event.SModelLanguageEvent
import jetbrains.mps.smodel.event.SModelListener
import jetbrains.mps.smodel.event.SModelPropertyEvent
import jetbrains.mps.smodel.event.SModelReferenceEvent
import jetbrains.mps.smodel.event.SModelRenamedEvent
import jetbrains.mps.smodel.event.SModelRootEvent
import jetbrains.mps.smodel.loading.ModelLoadingState
import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SPropertyChangeEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryListener
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.toSerialized
import org.modelix.model.mpsadapters.GlobalModelListener
import org.modelix.model.mpsadapters.MPSModelAsNode
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.mpsadapters.asReadableNode
import org.modelix.model.sync.bulk.DefaultInvalidationTree
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = mu.KotlinLogging.logger { }

abstract class MPSInvalidatingListener(val repository: SRepository) :
    GlobalModelListener(),
    SNodeChangeListener,
    SModuleListener,
    SRepositoryListener,
    SModelListener,
    org.jetbrains.mps.openapi.model.SModelListener {

    private val syncActive = AtomicBoolean(false)
    private val invalidationTree: DefaultInvalidationTree =
        DefaultInvalidationTree(MPSRepositoryAsNode(repository).getNodeReference().toSerialized())

    fun hasAnyInvalidations() = synchronized(invalidationTree) { invalidationTree.hasAnyInvalidations() }

    fun <R> runSync(body: (DefaultInvalidationTree) -> R): R {
        check(!syncActive.getAndSet(true)) { "Synchronization is already running" }
        try {
            synchronized(invalidationTree) {
                return body(invalidationTree).also {
                    LOG.trace { "Resetting invalidations" }
                    invalidationTree.reset()
                }
            }
        } catch (ex: Throwable) {
            LOG.error(ex) { "Sync from MPS failed" }
            throw ex
        } finally {
            syncActive.set(false)
        }
    }

    abstract fun onInvalidation()

    private fun invalidate(node: IReadableNode, includingDescendants: Boolean = false) {
        if (syncActive.get()) return
        synchronized(invalidationTree) {
            LOG.trace { "Invalidating ${node.getNodeReference()}" }
            invalidationTree.invalidate(node, includingDescendants)
        }
        onInvalidation()
    }

    private fun invalidate(node: SNode) {
        invalidate(node.asReadableNode())
    }

    private fun invalidate(model: SModel) {
        invalidate(MPSModelAsNode(model))
    }

    private fun invalidate(module: SModule) {
        invalidate(MPSModuleAsNode(module))
    }

    private fun invalidate(repository: SRepository) {
        invalidate(MPSRepositoryAsNode(repository))
    }

    override fun addListener(model: SModel) {
        model.addChangeListener(this)
        model.addModelListener(this)
        (model as SModelInternal).addModelListener(this)
    }

    override fun removeListener(model: SModel) {
        model.removeChangeListener(this)
        model.removeModelListener(this)
        (model as SModelInternal).removeModelListener(this)
    }

    override fun addListener(module: SModule) {
        module.addModuleListener(this)
    }

    override fun removeListener(module: SModule) {
        module.removeModuleListener(this)
    }

    override fun addListener(repository: SRepository) {
        repository.addRepositoryListener(this)
    }

    override fun removeListener(repository: SRepository) {
        repository.removeRepositoryListener(this)
    }

    override fun propertyChanged(e: SPropertyChangeEvent) {
        invalidate(e.node)
    }

    override fun referenceChanged(e: SReferenceChangeEvent) {
        invalidate(e.node)
    }

    override fun nodeAdded(e: SNodeAddEvent) {
        val parent = e.parent
        if (parent != null) {
            invalidate(parent)
        } else {
            invalidate(e.model)
        }
    }

    override fun nodeRemoved(e: SNodeRemoveEvent) {
        val parent = e.parent
        if (parent != null) {
            invalidate(parent)
        } else {
            invalidate(e.model)
        }
    }

    override fun beforeChildRemoved(event: SModelChildEvent) {}
    override fun beforeModelDisposed(model: SModel) {}
    override fun beforeModelRenamed(event: SModelRenamedEvent) {}
    override fun beforeRootRemoved(event: SModelRootEvent) {}
    override fun childAdded(event: SModelChildEvent) { invalidate(event.parent) }
    override fun childRemoved(event: SModelChildEvent) { invalidate(event.parent) }
    override fun devkitAdded(event: SModelDevKitEvent) { invalidate(event.model) }
    override fun devkitRemoved(event: SModelDevKitEvent) { invalidate(event.model) }
    override fun getPriority(): SModelListener.SModelListenerPriority {
        return SModelListener.SModelListenerPriority.CLIENT
    }

    override fun importAdded(event: SModelImportEvent) { invalidate(event.model) }
    override fun importRemoved(event: SModelImportEvent) { invalidate(event.model) }
    override fun languageAdded(event: SModelLanguageEvent) { invalidate(event.model) }
    override fun languageRemoved(event: SModelLanguageEvent) { invalidate(event.model) }
    override fun modelLoadingStateChanged(model: SModel, state: ModelLoadingState) {}
    override fun modelRenamed(event: SModelRenamedEvent) { invalidate(event.model) }
    override fun modelSaved(model: SModel) {}
    override fun propertyChanged(event: SModelPropertyEvent) { invalidate(event.node) }
    override fun referenceAdded(event: SModelReferenceEvent) { invalidate(event.reference.sourceNode) }
    override fun referenceRemoved(event: SModelReferenceEvent) { invalidate(event.reference.sourceNode) }

    @Deprecated("")
    override fun rootAdded(event: SModelRootEvent) {
    }

    @Deprecated("")
    override fun rootRemoved(event: SModelRootEvent) {
    }

    override fun modelLoaded(model: SModel, partially: Boolean) {}
    override fun modelReplaced(model: SModel) {
        invalidate(MPSModelAsNode(model), includingDescendants = true)
    }

    override fun modelUnloaded(model: SModel) {}
    override fun modelAttached(model: SModel, repository: SRepository) {}
    override fun modelDetached(model: SModel, repository: SRepository) {}
    override fun conflictDetected(model: SModel) {}
    override fun problemsDetected(model: SModel, problems: Iterable<SModel.Problem>) {}
    override fun modelAdded(module: SModule, model: SModel) {
        invalidate(module)
    }

    override fun beforeModelRemoved(module: SModule, model: SModel) {}

    override fun modelRemoved(module: SModule, reference: SModelReference) {
        invalidate(module)
    }
    override fun beforeModelRenamed(module: SModule, model: SModel, reference: SModelReference) {}
    override fun modelRenamed(module: SModule, model: SModel, reference: SModelReference) {
        invalidate(model)
    }

    override fun dependencyAdded(module: SModule, dependency: SDependency) { invalidate(module) }
    override fun dependencyRemoved(module: SModule, dependency: SDependency) { invalidate(module) }
    override fun languageAdded(module: SModule, language: SLanguage) { invalidate(module) }
    override fun languageRemoved(module: SModule, language: SLanguage) { invalidate(module) }
    override fun moduleChanged(module: SModule) { invalidate(module) }
    override fun moduleAdded(module: SModule) { invalidate(repository) }
    override fun beforeModuleRemoved(module: SModule) {}
    override fun moduleRemoved(reference: SModuleReference) { invalidate(repository) }
    override fun commandStarted(repository: SRepository) {}
    override fun commandFinished(repository: SRepository) {}
}

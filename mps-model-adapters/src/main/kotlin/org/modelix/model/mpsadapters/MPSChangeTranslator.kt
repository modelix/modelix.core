@file:Suppress("removal")

package org.modelix.model.mpsadapters

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
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
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
import org.jetbrains.mps.openapi.module.SRepositoryListenerBase
import org.modelix.incremental.DependencyTracking
import org.modelix.incremental.IStateVariableReference

class MPSChangeTranslator :
    GlobalModelListener(),
    SNodeChangeListener,
    SModuleListener,
    SRepositoryListener by object : SRepositoryListenerBase() {},
    SModelListener,
    org.jetbrains.mps.openapi.model.SModelListener {

    private fun notifyChange(change: IStateVariableReference<*>) {
        DependencyTracking.modified(change)
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
        notifyChange(MPSPropertyDependency(e.node, e.property))
    }

    override fun referenceChanged(e: SReferenceChangeEvent) {
        notifyChange(MPSReferenceDependency(e.node, e.associationLink))
    }

    override fun nodeAdded(e: SNodeAddEvent) {
        if (e.parent != null) {
            notifyChange(MPSChildrenDependency(e.parent!!, e.aggregationLink!!))
        } else {
            notifyChange(MPSRootNodesListDependency(e.model))
        }
        notifyChange(MPSContainmentDependency(e.child))
    }

    override fun nodeRemoved(e: SNodeRemoveEvent) {
        if (e.parent != null) {
            notifyChange(MPSChildrenDependency(e.parent!!, e.aggregationLink!!))
        } else {
            notifyChange(MPSRootNodesListDependency(e.model))
        }
        notifyChange(MPSContainmentDependency(e.child))
    }

    override fun beforeChildRemoved(event: SModelChildEvent) {}
    override fun beforeModelDisposed(model: SModel) {}
    override fun beforeModelRenamed(event: SModelRenamedEvent) {}
    override fun beforeRootRemoved(event: SModelRootEvent) {}
    override fun childAdded(event: SModelChildEvent) {}
    override fun childRemoved(event: SModelChildEvent) {}
    override fun devkitAdded(event: SModelDevKitEvent) {}
    override fun devkitRemoved(event: SModelDevKitEvent) {}
    override fun getPriority(): SModelListener.SModelListenerPriority {
        return SModelListener.SModelListenerPriority.CLIENT
    }

    override fun importAdded(event: SModelImportEvent) {
        notifyChange(MPSModelDependency(event.model))
    }

    override fun importRemoved(event: SModelImportEvent) {
        notifyChange(MPSModelDependency(event.model))
    }

    override fun languageAdded(event: SModelLanguageEvent) {}
    override fun languageRemoved(event: SModelLanguageEvent) {}
    override fun modelLoadingStateChanged(model: SModel, state: ModelLoadingState) {}
    override fun modelRenamed(event: SModelRenamedEvent) {}
    override fun modelSaved(model: SModel) {}
    override fun propertyChanged(event: SModelPropertyEvent) {}
    override fun referenceAdded(event: SModelReferenceEvent) {}
    override fun referenceRemoved(event: SModelReferenceEvent) {}

    @Deprecated("")
    override fun rootAdded(event: SModelRootEvent) {
    }

    @Deprecated("")
    override fun rootRemoved(event: SModelRootEvent) {
    }

    override fun modelLoaded(model: SModel, partially: Boolean) {}
    override fun modelReplaced(model: SModel) {
        notifyChange(MPSModelContentDependency(model))
    }

    override fun modelUnloaded(model: SModel) {}
    override fun modelAttached(model: SModel, repository: SRepository) {}
    override fun modelDetached(model: SModel, repository: SRepository) {}
    override fun conflictDetected(model: SModel) {}
    override fun problemsDetected(model: SModel, problems: Iterable<SModel.Problem>) {}
    override fun modelAdded(module: SModule, model: SModel) {
        notifyChange(MPSModuleDependency(module))
    }

    override fun beforeModelRemoved(module: SModule, model: SModel) {
        notifyChange(MPSModuleDependency(module))
    }

    override fun modelRemoved(module: SModule, reference: SModelReference) {}
    override fun beforeModelRenamed(module: SModule, model: SModel, reference: SModelReference) {}
    override fun modelRenamed(module: SModule, model: SModel, reference: SModelReference) {
        notifyChange(MPSModelDependency(model))
    }

    override fun dependencyAdded(module: SModule, dependency: SDependency) {}
    override fun dependencyRemoved(module: SModule, dependency: SDependency) {}
    override fun languageAdded(module: SModule, language: SLanguage) {}
    override fun languageRemoved(module: SModule, language: SLanguage) {}
    override fun moduleChanged(module: SModule) {}
    override fun moduleAdded(module: SModule) {
        notifyChange(MPSRepositoryDependency)
    }

    override fun beforeModuleRemoved(module: SModule) {
        notifyChange(MPSRepositoryDependency)
    }

    override fun moduleRemoved(reference: SModuleReference) {}
    override fun commandStarted(repository: SRepository) {}
    override fun commandFinished(repository: SRepository) {}
}

abstract class MPSDependencyBase : IStateVariableReference<Any?> {
    override fun read(): Any? = null
}

data class MPSNodeDependency(val node: SNode) : MPSDependencyBase() {
    override fun getGroup() = node.parent?.let { MPSNodeDependency(it) }
}

data class MPSPropertyDependency(val node: SNode, val property: SProperty) : MPSDependencyBase() {
    override fun getGroup() = MPSAllPropertiesDependency(node)
}

data class MPSReferenceDependency(val node: SNode, val link: SReferenceLink) : MPSDependencyBase() {
    override fun getGroup() = MPSAllReferencesDependency(node)
}

data class MPSChildrenDependency(val node: SNode, val link: SContainmentLink) : MPSDependencyBase() {
    override fun getGroup() = MPSAllChildrenDependency(node)
}

data class MPSAllChildrenDependency(val node: SNode) : MPSDependencyBase() {
    override fun getGroup() = MPSNodeDependency(node)
}

data class MPSAllPropertiesDependency(val node: SNode) : MPSDependencyBase() {
    override fun getGroup() = MPSNodeDependency(node)
}

data class MPSAllReferencesDependency(val node: SNode) : MPSDependencyBase() {
    override fun getGroup() = MPSNodeDependency(node)
}

data class MPSContainmentDependency(val node: SNode) : MPSDependencyBase() {
    override fun getGroup() = MPSNodeDependency(node)
}

/**
 * No SRepository parameter, because there is only one repository in MPS.
 * If one repository changes, all of them change.
 */
object MPSRepositoryDependency : MPSDependencyBase() {
    override fun getGroup() = null
}

data class MPSModuleDependency(val model: SModule) : MPSDependencyBase() {
    override fun getGroup() = MPSRepositoryDependency
}

data class MPSModelDependency(val model: SModel) : MPSDependencyBase() {
    override fun getGroup() = MPSModuleDependency(model.module)
}

data class MPSRootNodesListDependency(val model: SModel) : MPSDependencyBase() {
    override fun getGroup() = MPSModelDependency(model)
}

/**
 * This is used to handle the case that MPS reloads a model from disk.
 */
data class MPSModelContentDependency(val model: SModel) : MPSDependencyBase() {
    override fun getGroup() = MPSModelDependency(model)
}

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

package org.modelix.mps.sync.transformation.mpsToModelix.incremental

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
import org.jetbrains.mps.openapi.model.SModel
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.mps.ApplicationLifecycleTracker
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelChangeListener(branch: IBranch, private val binding: ModelBinding) : SModelListener {

    private val modelSynchronizer = ModelSynchronizer(branch)
    private val nodeSynchronizer = NodeSynchronizer(branch)

    override fun importAdded(event: SModelImportEvent) {
        modelSynchronizer.addModelImport(event.model, event.modelUID)
    }

    override fun importRemoved(event: SModelImportEvent) {
        nodeSynchronizer.removeNode(
            parentNodeIdProducer = { it[event.model]!! },
            childNodeIdProducer = { it[event.model, event.modelUID]!! },
        )
    }

    override fun languageAdded(event: SModelLanguageEvent) {
        modelSynchronizer.addLanguageDependency(event.model, event.eventLanguage)
    }

    override fun languageRemoved(event: SModelLanguageEvent) {
        nodeSynchronizer.removeNode(
            parentNodeIdProducer = { it[event.model]!! },
            childNodeIdProducer = { it[event.model, event.eventLanguage.sourceModuleReference]!! },
        )
    }

    override fun devkitAdded(event: SModelDevKitEvent) {
        modelSynchronizer.addDevKitDependency(event.model, event.devkitNamespace)
    }

    override fun devkitRemoved(event: SModelDevKitEvent) {
        nodeSynchronizer.removeNode(
            parentNodeIdProducer = { it[event.model]!! },
            childNodeIdProducer = { it[event.model, event.devkitNamespace]!! },
        )
    }

    override fun modelRenamed(event: SModelRenamedEvent) {
        nodeSynchronizer.setProperty(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name,
            event.newName,
        ) { it[event.model]!! }
    }

    override fun beforeModelDisposed(model: SModel) {
        if (!ApplicationLifecycleTracker.applicationClosing) {
            binding.deactivate(removeFromServer = true)
        }
    }

    override fun getPriority(): SModelListener.SModelListenerPriority = SModelListener.SModelListenerPriority.CLIENT

    @Deprecated("Deprecated in Java")
    override fun rootAdded(event: SModelRootEvent) {
        // duplicate of SNodeChangeListener.nodeAdded
    }

    @Deprecated("Deprecated in Java")
    override fun rootRemoved(event: SModelRootEvent) {
        // duplicate of SNodeChangeListener.nodeRemoved
    }

    override fun propertyChanged(event: SModelPropertyEvent) {
        // duplicate of SNodeChangeListener.propertyChanged
    }

    override fun childAdded(event: SModelChildEvent) {
        // duplicate of SNodeChangeListener.childAdded
    }

    override fun childRemoved(event: SModelChildEvent) {
        // duplicate of SNodeChangeListener.nodeRemoved
    }

    override fun referenceAdded(event: SModelReferenceEvent) {
        // duplicate of SNodeChangeListener.referenceChanged
    }

    override fun referenceRemoved(event: SModelReferenceEvent) {
        // duplicate of SNodeChangeListener.referenceChanged
    }

    override fun beforeChildRemoved(event: SModelChildEvent) {}
    override fun beforeRootRemoved(event: SModelRootEvent) {}
    override fun beforeModelRenamed(event: SModelRenamedEvent) {}
    override fun modelSaved(model: SModel) {}
    override fun modelLoadingStateChanged(model: SModel?, state: ModelLoadingState) {}

    fun resolveModelImports() = modelSynchronizer.resolveModelImportsInTask()
}

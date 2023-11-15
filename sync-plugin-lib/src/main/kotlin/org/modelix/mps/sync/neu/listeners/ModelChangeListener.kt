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

package org.modelix.mps.sync.neu.listeners

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

class ModelChangeListener : SModelListener {
    override fun languageAdded(event: SModelLanguageEvent?) {
        TODO("Not yet implemented")
    }

    override fun languageRemoved(event: SModelLanguageEvent?) {
        TODO("Not yet implemented")
    }

    override fun importAdded(event: SModelImportEvent?) {
        TODO("Not yet implemented")
    }

    override fun importRemoved(event: SModelImportEvent?) {
        TODO("Not yet implemented")
    }

    override fun devkitAdded(event: SModelDevKitEvent?) {
        TODO("Not yet implemented")
    }

    override fun devkitRemoved(event: SModelDevKitEvent?) {
        TODO("Not yet implemented")
    }

    override fun rootAdded(event: SModelRootEvent?) {
        TODO("Not yet implemented")
    }

    override fun rootRemoved(event: SModelRootEvent?) {
        TODO("Not yet implemented")
    }

    override fun beforeRootRemoved(event: SModelRootEvent?) {
        TODO("Not yet implemented")
    }

    override fun beforeModelRenamed(event: SModelRenamedEvent?) {
        TODO("Not yet implemented")
    }

    override fun modelRenamed(event: SModelRenamedEvent?) {
        TODO("Not yet implemented")
    }

    override fun propertyChanged(event: SModelPropertyEvent?) {
        TODO("Not yet implemented")
    }

    override fun childAdded(event: SModelChildEvent?) {
        TODO("Not yet implemented")
    }

    override fun childRemoved(event: SModelChildEvent?) {
        TODO("Not yet implemented")
    }

    override fun beforeChildRemoved(event: SModelChildEvent?) {
        TODO("Not yet implemented")
    }

    override fun referenceAdded(event: SModelReferenceEvent?) {
        TODO("Not yet implemented")
    }

    override fun referenceRemoved(event: SModelReferenceEvent?) {
        TODO("Not yet implemented")
    }

    override fun modelSaved(model: SModel?) {
        TODO("Not yet implemented")
    }

    override fun modelLoadingStateChanged(model: SModel?, state: ModelLoadingState?) {
        TODO("Not yet implemented")
    }

    override fun beforeModelDisposed(model: SModel?) {
        TODO("Not yet implemented")
    }

    override fun getPriority(): SModelListener.SModelListenerPriority {
        TODO("Not yet implemented")
    }
}

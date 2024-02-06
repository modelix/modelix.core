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

package org.modelix.mps.sync.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.extapi.model.SModelBase
import org.jetbrains.mps.openapi.model.SModel
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.modelix.ReplicatedModelRegistry
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.util.SyncQueue

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelSyncAction : AnAction {

    companion object {
        val CONTEXT_MODEL = DataKey.create<SModel>("MPS_Context_SModel")

        fun create() = ModelSyncAction("Synchronize model to server")
    }

    private val logger = logger<ModelSyncAction>()

    constructor() : super()

    constructor(text: String) : super(text)

    override fun actionPerformed(event: AnActionEvent) {
        try {
            val model = event.getData(CONTEXT_MODEL)!! as SModelBase
            val branch = ReplicatedModelRegistry.model!!.getBranch()
            // TODO fixme: warn the user if the model imports another model that is not on the model server yet
            ModelSynchronizer(branch, MpsToModelixMap, BindingsRegistry, SyncQueue).addModelAndActivate(model)
        } catch (ex: Exception) {
            logger.error("Model sync error occurred", ex)
        }
    }
}

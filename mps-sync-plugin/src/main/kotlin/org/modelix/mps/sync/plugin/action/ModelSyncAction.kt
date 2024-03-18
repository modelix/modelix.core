/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import jetbrains.mps.extapi.model.SModelBase
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.modelix.ReplicatedModelRegistry
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelSyncAction : AnAction {

    companion object {
        val CONTEXT_MODEL = DataKey.create<SModel>("MPS_Context_SModel")

        fun create() = ModelSyncAction("Synchronize model to server")
    }

    private val logger = KotlinLogging.logger {}

    constructor() : super()

    constructor(text: String) : super(text)

    override fun actionPerformed(event: AnActionEvent) {
        try {
            val model = event.getData(CONTEXT_MODEL)!! as SModelBase

            val binding = BindingsRegistry.getModelBinding(model)
            require(binding == null) { "Model is already synchronized to server." }

            val replicatedModel = ReplicatedModelRegistry.model
            require(replicatedModel != null) { "Synchronization to server has not been established yet" }

            val branch = replicatedModel.getBranch()
            ModelSynchronizer(branch).addModelAndActivate(model)
        } catch (ex: Exception) {
            logger.error(ex) { "Model sync error occurred" }
        }
    }
}

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
import jetbrains.mps.project.AbstractModule
import mu.KotlinLogging
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.modelix.ReplicatedModelRegistry
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModuleSynchronizer

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleSyncAction : AnAction {

    companion object {
        val CONTEXT_MODULE = DataKey.create<SModule>("MPS_Context_SModule")

        fun create() = ModuleSyncAction("Synchronize module to server")
    }

    private val logger = KotlinLogging.logger {}

    constructor() : super()

    constructor(text: String) : super(text)

    override fun actionPerformed(event: AnActionEvent) {
        try {
            val module = event.getData(CONTEXT_MODULE)!! as AbstractModule

            val binding = BindingsRegistry.getModuleBinding(module)
            require(binding == null) { "Module is already synchronized to server." }

            val replicatedModel = ReplicatedModelRegistry.model
            require(replicatedModel != null) { "Synchronization to server has not been established yet" }

            val branch = replicatedModel.getBranch()
            ModuleSynchronizer(branch).addModuleAndActivate(module)
        } catch (ex: Exception) {
            logger.error(ex) { "Module sync error occurred" }
        }
    }
}

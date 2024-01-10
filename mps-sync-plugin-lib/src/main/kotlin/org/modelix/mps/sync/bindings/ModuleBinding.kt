/*
 * Copyright (c) 2024.
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

package org.modelix.mps.sync.bindings

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.project.AbstractModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModuleChangeListener
import org.modelix.mps.sync.util.SyncBarrier

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleBinding(
    val module: AbstractModule,
    branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    isSynchronizing: SyncBarrier,
    private val bindingsRegistry: BindingsRegistry,
) : IBinding {

    private val logger = logger<ModelBinding>()

    private val moduleId = module.moduleId
    private val changeListener = ModuleChangeListener(branch, nodeMap, isSynchronizing)

    private var isDisposed = false
    private var isActivated = false

    override fun activate(callback: Runnable?) {
        check(!isDisposed) { "Module binding of $moduleId is disposed." }
        if (isActivated) {
            return
        }

        // register module listener
        module.addModuleListener(changeListener)

        // activate child models' bindings
        bindingsRegistry.getModelBindings(module)?.forEach { it.activate() }

        isActivated = true

        logger.info("Binding for module $moduleId is activated.")

        callback?.run()
    }

    override fun deactivate(callback: Runnable?) {
        if (isDisposed) {
            return
        }

        // unregister listener
        module.removeModuleListener(changeListener)

        // deactivate child models' bindings
        bindingsRegistry.getModelBindings(module)?.forEach { it.deactivate() }

        isDisposed = true
        isActivated = false

        logger.info("Binding for module $moduleId is deactivated.")

        // delete module
        // TODO is it going to delete it from the cloud as well?
        module.dispose()
        nodeMap.remove(module)

        logger.info("Module is removed.")

        callback?.run()

        bindingsRegistry.removeModuleBinding(this)
    }

    override fun name() = "Binding of Module \"${module.moduleName}\"."
}

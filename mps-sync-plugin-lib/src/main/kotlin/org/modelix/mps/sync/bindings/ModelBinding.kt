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
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.extapi.module.SModuleBase
import org.jetbrains.mps.openapi.module.ModelAccess
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.mps.util.runWriteActionCommandBlocking
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModelChangeListener
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.NodeChangeListener
import org.modelix.mps.sync.util.SyncBarrier

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelBinding(
    val model: SModelBase,
    branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    isSynchronizing: SyncBarrier,
    private val modelAccess: ModelAccess,
    private val bindingsRegistry: BindingsRegistry,
) : IBinding {

    private val logger = logger<ModelBinding>()

    private val modelId = model.modelId
    private val modelChangeListener = ModelChangeListener(branch, nodeMap, isSynchronizing, this)
    private val nodeChangeListener = NodeChangeListener(branch, nodeMap, isSynchronizing)

    private var isDisposed = false
    private var isActivated = false

    override fun activate(callback: Runnable?) {
        check(!isDisposed) { "Model binding of $modelId is disposed." }
        if (isActivated) {
            return
        }

        // register listeners
        model.addChangeListener(nodeChangeListener)
        model.addModelListener(modelChangeListener)

        isActivated = true
        logger.info("Binding for model $modelId is activated.")

        callback?.run()
    }

    override fun deactivate(callback: Runnable?) {
        if (isDisposed) {
            return
        }

        // remove from module
        val parentModule = model.module
        check(parentModule is SModuleBase) { "Parent Module ${parentModule?.moduleId} of Model $modelId is not an SModuleBase." }
        // TODO test if removing the model from the module was successful before deleting the model. Otherwise the module's modelDeleted event listener will be invoked.
        modelAccess.runWriteActionCommandBlocking {
            parentModule.unregisterModel(model)
            model.module = null
        }

        // unregister listeners
        model.removeChangeListener(nodeChangeListener)
        model.removeModelListener(modelChangeListener)

        isDisposed = true
        isActivated = false

        logger.info("Binding for model $modelId is deactivated.")

        // delete model
        model.detach()
        nodeMap.remove(model)

        logger.info("Model is removed.")

        callback?.run()

        bindingsRegistry.removeModelBinding(this)
    }

    override fun name() = "Binding of Model \"${model.name}\" in Module \"${model.module?.moduleName}\"."
}

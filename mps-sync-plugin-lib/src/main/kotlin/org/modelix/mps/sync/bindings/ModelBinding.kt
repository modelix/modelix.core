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
import jetbrains.mps.model.ModelDeleteHelper
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModelChangeListener
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.NodeChangeListener
import org.modelix.mps.sync.util.SyncLock
import org.modelix.mps.sync.util.SyncQueue

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelBinding(
    val model: SModelBase,
    branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val bindingsRegistry: BindingsRegistry,
    private val syncQueue: SyncQueue,
) : IBinding {

    private val logger = logger<ModelBinding>()

    private val modelChangeListener = ModelChangeListener(branch, nodeMap, bindingsRegistry, syncQueue, this)
    private val nodeChangeListener = NodeChangeListener(branch, nodeMap, syncQueue)

    private var isDisposed = false
    private var beingDisposed = false
    private var isActivated = false

    override fun activate(callback: Runnable?) {
        check(!isDisposed) { "${name()} is disposed." }
        if (isActivated) {
            return
        }

        // register listeners
        model.addChangeListener(nodeChangeListener)
        model.addModelListener(modelChangeListener)

        isActivated = true
        logger.info("${name()} is activated.")

        callback?.run()
    }

    override fun deactivate(removeFromServer: Boolean, callback: Runnable?) {
        if (isDisposed || beingDisposed) {
            return
        }
        beingDisposed = true

        // unregister listeners
        model.removeChangeListener(nodeChangeListener)
        model.removeModelListener(modelChangeListener)

        val parentModule = model.module!!
        if (removeFromServer) {
            // remove from bindings, so when removing the model from the module we'll know that this model is not assumed to exist, therefore we'll not delete it in the cloud (see ModuleChangeListener's modelRemoved method)
            bindingsRegistry.removeModelBinding(parentModule, this)
        }

        // delete model
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE)) {
            try {
                if (!removeFromServer) {
                    // to delete the files locally
                    // otherwise, MPS has to take care of triggering ModelDeleteHelper(model).delete() to delete the model
                    ModelDeleteHelper(model).delete()
                }
            } catch (ex: Exception) {
                logger.error("Exception occurred while deactivating ${name()}.", ex)
                // if any error occurs, then we put the binding back to let the rest of the application know that it exists
                bindingsRegistry.addModelBinding(this)
                throw ex
            }
        }.continueWith(linkedSetOf(SyncLock.NONE)) {
            bindingsRegistry.removeModelBinding(parentModule, this)

            if (!removeFromServer) {
                // when deleting the model (modelix Node) from the cloud, then the NodeSynchronizer.removeNode takes care of the node deletion
                nodeMap.remove(model)
            }

            isDisposed = true
            beingDisposed = false
            isActivated = false

            logger.info(
                "${name()} is deactivated and model is removed locally${
                    if (removeFromServer) {
                        " and from server"
                    } else {
                        ""
                    }
                }.",
            )

            callback?.run()
        }
    }

    override fun name() = "Binding of Model \"${model.name}\""

    override fun toString() = name()
}

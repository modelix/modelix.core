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

import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.model.ModelDeleteHelper
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModelChangeListener
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.NodeChangeListener
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelBinding(val model: SModelBase, branch: IBranch) : IBinding {

    private val logger = KotlinLogging.logger {}
    private val nodeMap = MpsToModelixMap
    private val syncQueue = SyncQueue
    private val bindingsRegistry = BindingsRegistry

    private val modelChangeListener = ModelChangeListener(branch, this)
    private val nodeChangeListener = NodeChangeListener(branch)

    @Volatile
    private var isDisposed = false

    @Volatile
    private var isActivated = false

    @Volatile
    private var modelDeletedLocally = false

    @Synchronized
    override fun activate(callback: Runnable?) {
        if (isDisposed || isActivated) {
            return
        }

        // register listeners
        model.addChangeListener(nodeChangeListener)
        model.addModelListener(modelChangeListener)

        isActivated = true

        bindingsRegistry.bindingActivated(this)

        logger.info { "${name()} is activated." }

        callback?.run()
    }

    override fun deactivate(removeFromServer: Boolean, callback: Runnable?): CompletableFuture<Any?> {
        if (isDisposed) {
            return CompletableFuture.completedFuture(null)
        }

        return syncQueue.enqueue(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            synchronized(this) {
                if (isActivated) {
                    // unregister listeners
                    model.removeChangeListener(nodeChangeListener)
                    model.removeModelListener(modelChangeListener)

                    if (removeFromServer) {
                        /*
                         * remove from bindings, so when removing the model from the module we'll know that this model
                         * is not assumed to exist, therefore we'll not delete it in the cloud
                         * (see ModuleChangeListener's modelRemoved method)
                         */
                        bindingsRegistry.removeModelBinding(model.module!!, this)
                    }

                    isActivated = false
                }
            }
        }.continueWith(linkedSetOf(SyncLock.MPS_WRITE), SyncDirection.MPS_TO_MODELIX) {
            synchronized(this) {
                try {
                    // delete model
                    if (!removeFromServer && !modelDeletedLocally) {
                        /*
                         * to delete the files locally, otherwise MPS takes care of calling
                         * ModelDeleteHelper(model).delete() to delete the model (if removeFromServer is true)
                         */
                        ModelDeleteHelper(model).delete()
                        modelDeletedLocally = true
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Exception occurred while deactivating ${name()}." }
                    /*
                     * if any error occurs, then we put the binding back to let the rest of the application know that
                     * it exists
                     */
                    bindingsRegistry.addModelBinding(this)
                    activate()

                    throw ex
                }
            }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            bindingsRegistry.removeModelBinding(model.module!!, this)

            if (!removeFromServer) {
                /*
                 * when deleting the model (modelix Node) from the cloud, then the NodeSynchronizer.removeNode takes
                 * care of the node deletion
                 */
                nodeMap.remove(model)
            }

            isDisposed = true

            logger.info {
                "${name()} is deactivated and model is removed locally${
                    if (removeFromServer) {
                        " and from server"
                    } else {
                        ""
                    }
                }."
            }

            callback?.run()
        }.getResult()
    }

    override fun name() = "Binding of Model \"${model.name}\""

    override fun toString() = name()
}

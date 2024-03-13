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

import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.AbstractModule
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModuleChangeListener
import org.modelix.mps.sync.util.waitForCompletionOfEach
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleBinding(val module: AbstractModule, branch: IBranch) : IBinding {

    private val logger = KotlinLogging.logger {}
    private val nodeMap = MpsToModelixMap
    private val syncQueue = SyncQueue
    private val bindingsRegistry = BindingsRegistry

    private val changeListener = ModuleChangeListener(branch)

    @Volatile
    private var isDisposed = false

    @Volatile
    private var isActivated = false

    @Volatile
    private var moduleDeletedLocally = false

    @Synchronized
    override fun activate(callback: Runnable?) {
        if (isDisposed || isActivated) {
            return
        }

        // register module listener
        module.addModuleListener(changeListener)

        // activate child models' bindings
        bindingsRegistry.getModelBindings(module)?.forEach { it.activate() }

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
                    // unregister listener
                    module.removeModuleListener(changeListener)

                    val modelBindings = bindingsRegistry.getModelBindings(module)

                    /*
                     * deactivate child models' bindings and wait for their successful completion
                     * throws ExecutionException if any deactivation failed
                     */
                    return@enqueue modelBindings?.waitForCompletionOfEach { it.deactivate(removeFromServer) }
                }
            }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            synchronized(this) {
                /*
                 * delete the binding, because if binding exists then module is assumed to exist,
                 * i.e. RepositoryChangeListener.moduleRemoved(...) will not delete the module
                 */
                bindingsRegistry.removeModuleBinding(this)
                isActivated = false
            }
        }.continueWith(linkedSetOf(SyncLock.MPS_WRITE), SyncDirection.MPS_TO_MODELIX) {
            synchronized(this) {
                // delete module
                try {
                    if (!removeFromServer && !moduleDeletedLocally) {
                        /*
                         * if we just delete it locally, then we have to call ModuleDeleteHelper manually.
                         * otherwise, MPS will call us via the event-handler chain starting from
                         * ModuleDeleteHelper.deleteModules --> RepositoryChangeListener -->
                         * moduleListener.deactivate(removeFromServer = true)
                         */
                        ModuleDeleteHelper(ActiveMpsProjectInjector.activeMpsProject!!)
                            .deleteModules(listOf(module), false, true)
                        moduleDeletedLocally = true
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Exception occurred while deactivating ${name()}." }
                    /*
                     * if any error occurs, then we put the binding back to let the rest of the application know that
                     * it exists
                     */
                    bindingsRegistry.addModuleBinding(this)
                    activate()
                    throw ex
                }
            }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            nodeMap.remove(module)

            isDisposed = true

            logger.info {
                "${name()} is deactivated and module is removed locally${
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

    override fun name() = "Binding of Module \"${module.moduleName}\""

    override fun toString() = name()
}

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
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.AbstractModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModuleChangeListener
import org.modelix.mps.sync.util.SyncLock
import org.modelix.mps.sync.util.SyncQueue
import java.util.concurrent.CountDownLatch

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleBinding(
    val module: AbstractModule,
    branch: IBranch,
    private val nodeMap: MpsToModelixMap,
    private val bindingsRegistry: BindingsRegistry,
    private val syncQueue: SyncQueue,
) : IBinding {

    private val logger = logger<ModelBinding>()

    private val changeListener = ModuleChangeListener(branch, nodeMap, bindingsRegistry, syncQueue)

    private var isDisposed = false
    private var beingDisposed = false
    private var isActivated = false

    override fun activate(callback: Runnable?) {
        check(!isDisposed) { "${name()} is disposed." }
        if (isActivated) {
            return
        }

        // register module listener
        module.addModuleListener(changeListener)

        // activate child models' bindings
        bindingsRegistry.getModelBindings(module)?.forEach { it.activate() }

        isActivated = true

        logger.info("${name()} is activated.")

        callback?.run()
    }

    override fun deactivate(removeFromServer: Boolean, callback: Runnable?) {
        if (isDisposed || beingDisposed) {
            return
        }

        beingDisposed = true

        // unregister listener
        module.removeModuleListener(changeListener)

        syncQueue.enqueue(linkedSetOf(SyncLock.NONE)) {
            val modelBindings = bindingsRegistry.getModelBindings(module)
            val barrier = CountDownLatch(modelBindings?.size ?: 0)

            // deactivate child models' bindings
            modelBindings?.forEach {
                it.deactivate(removeFromServer) { barrier.countDown() }
            }

            // wait for all model bindings to be deactivated
            barrier.await()

            // delete the binding, because if binding exists then module is assumed to exist, i.e. RepositoryChangeListener.moduleRemoved(...) will not delete the module
            bindingsRegistry.removeModuleBinding(this)
        }.continueWith(linkedSetOf(SyncLock.MPS_WRITE)) {
            // delete module
            try {
                if (!removeFromServer) {
                    // if we just delete it locally, then we have to call ModuleDeleteHelper manually.
                    // otherwise, MPS will call us via the event-handler chain starting from ModuleDeleteHelper.deleteModules --> RepositoryChangeListener --> moduleListener.deactivate(removeFromServer = true)
                    ModuleDeleteHelper(ActiveMpsProjectInjector.activeMpsProject!!)
                        .deleteModules(listOf(module), false, true)
                }
            } catch (ex: Exception) {
                logger.error("Exception occurred while deactivating ${name()}.", ex)
                // if any error occurs, then we put the binding back to let the rest of the application know that it exists
                bindingsRegistry.addModuleBinding(this)
                throw ex
            }
        }.continueWith(linkedSetOf(SyncLock.NONE)) {
            // deactivate binding

            nodeMap.remove(module)

            isDisposed = true
            beingDisposed = false
            isActivated = false

            logger.info(
                "${name()} is deactivated and module is removed locally${
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

    override fun name() = "Binding of Module \"${module.moduleName}\""

    override fun toString() = name()
}

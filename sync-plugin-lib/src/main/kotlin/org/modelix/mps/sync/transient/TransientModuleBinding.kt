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

package org.modelix.mps.sync.transient

import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.MPSModuleRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.area.PArea
import org.modelix.model.client.SharedExecutors
import org.modelix.mps.sync.binding.ModuleBinding
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.util.ModelixNotifications
import java.util.concurrent.atomic.AtomicInteger

// status: migrated, but needs some bugfixes
class TransientModuleBinding(moduleNodeId: Long) : ModuleBinding(moduleNodeId, SyncDirection.TO_MPS) {

    companion object {
        private val NAME_SEQUENCE = AtomicInteger(0)
    }

    override lateinit var module: CloudTransientModule

    override fun doActivate() {
        val branch = getBranch()!!
        var moduleName = PArea(branch).executeRead {
            PNodeAdapter(
                moduleNodeId,
                branch,
            ).getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        }
        val moduleIdStr = PArea(branch).executeRead {
            PNodeAdapter(moduleNodeId, branch).getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id)
        }
        if (moduleName?.isEmpty() == true) {
            moduleName = "cloud.module ${NAME_SEQUENCE.incrementAndGet()}"
        }
        var moduleId =
            ModuleId.foreign("${getCloudRepository()?.completeId()}-${java.lang.Long.toHexString(moduleNodeId)}")

        if (moduleIdStr != null) {
            val temptativeModuleId = ModuleId.fromString(moduleIdStr)
            // This could happen because someone clone a module to Modelix and then try to bind it.
            // In this case we want to give a warning to the user
            if (CloudTransientModules.instance.isModuleIdUsed(temptativeModuleId)) {
                ModelixNotifications.notifyWarning(
                    "Module ID already used",
                    "We cannot load the module with the ID $temptativeModuleId as the module id seems to be already used. We will load it with module id $moduleId instead",
                )
            } else {
                moduleId = temptativeModuleId
            }
        }
        module = CloudTransientModules.instance.createModule(moduleName!!, moduleId)
        super.doActivate()
    }

    override fun doDeactivate() {
        super.doDeactivate()
        SharedExecutors.FIXED.execute {
            synchronized(this) {
                MPSModuleRepository.getInstance().modelAccess.runWriteAction {
                    CloudTransientModules.instance.disposeModule(module)
                }
            }
        }
    }
}

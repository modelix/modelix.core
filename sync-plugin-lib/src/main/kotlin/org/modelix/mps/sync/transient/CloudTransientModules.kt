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

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.extapi.module.SRepositoryExt
import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.MPSModuleOwner
import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.module.SModuleId
import org.modelix.mps.sync.util.WriteAccessUtil

// status: migrated, but needs some bugfixes
class CloudTransientModules private constructor(private val mpsRepository: SRepositoryExt) {

    companion object {
        val instance = CloudTransientModules(MPSModuleRepository.getInstance())
    }

    private val logger = logger<CloudTransientModules>()

    private val modules = mutableListOf<CloudTransientModule>()

    private val moduleOwner = MPSModuleOwner { false }

    fun isModuleIdUsed(moduleId: SModuleId): Boolean {
        var result: Boolean? = null
        mpsRepository.modelAccess.runReadAction {
            result = this.mpsRepository.getModule(moduleId) != null
        }
        return result!!
    }

    fun createModule(name: String, id: ModuleId): CloudTransientModule {
        var module: CloudTransientModule? = null
        mpsRepository.modelAccess.runWriteAction {
            module = CloudTransientModule(name, id)
            modules.add(module!!)
            logger.debug("Register module $id")
            mpsRepository.registerModule(module!!, moduleOwner)
        }
        return module!!
    }

    fun disposeModule(module: CloudTransientModule) {
        mpsRepository.modelAccess.runWriteAction {
            doDisposeModule(module)
            modules.remove(module)
        }
    }

    private fun doDisposeModule(module: CloudTransientModule) {
        if (module.repository != null) {
            logger.debug("Unregister module ${module.moduleId}")
            mpsRepository.unregisterModule(module, moduleOwner)
        }
    }

    fun dispose() {
        WriteAccessUtil.runWrite(mpsRepository) {
            try {
                modules.forEach {
                    doDisposeModule(it)
                }
                modules.clear()
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
        }
    }
}

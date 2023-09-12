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

    private val logger = mu.KotlinLogging.logger {}

    private val modules = mutableListOf<CloudTransientModule>()

    private val moduleOwner = MPSModuleOwner { false }

    fun isModuleIdUsed(moduleId: SModuleId): Boolean {
        // TODO How to translate this correctly?
        /**
         read action with mpsRepository {
         result = this.mpsRepository.getModule(moduleId) != null;
         }
         */
        return this.mpsRepository.getModule(moduleId) != null
    }

    fun createModule(name: String, id: ModuleId): CloudTransientModule {
        // TODO How to translate this correctly?
        /**
         write action with mpsRepository {
         module = new CloudTransientModule (name, id);
         modules.add(module);
         log debug "Register module " + id, <no throwable>;
         mpsRepository.registerModule(module, moduleOwner);
         }
         */

        val module = CloudTransientModule(name, id)
        modules.add(module)
        logger.debug { "Register module $id" }
        mpsRepository.registerModule(module, moduleOwner)
        return module
    }

    fun disposeModule(module: CloudTransientModule) {
        // TODO How to translate this correctly?
        /**
         write action with mpsRepository {
         doDisposeModule(module);
         modules.remove(module);
         }
         */
        doDisposeModule(module)
        modules.remove(module)
    }

    private fun doDisposeModule(module: CloudTransientModule) {
        if (module.repository != null) {
            logger.debug { "Unregister module ${module.moduleId}" }
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
                logger.error(ex) { ex.message }
            }
        }
    }
}

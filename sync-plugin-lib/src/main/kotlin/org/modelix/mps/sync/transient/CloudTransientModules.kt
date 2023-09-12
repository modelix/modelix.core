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
import jetbrains.mps.smodel.MPSModuleRepository

class CloudTransientModules private constructor(private val mpsRepository: SRepositoryExt) {

    companion object {
        val instance = CloudTransientModules(MPSModuleRepository.getInstance())
    }

    private val modules = mutableListOf<CloudTransientModule>()

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
        TODO()
    }

    fun isModuleIdUsed(temptativeModuleId: ModuleId): Boolean {
        TODO("Not yet implemented")
    }

    fun createModule(moduleName: String, moduleId: ModuleId): CloudTransientModule {
        TODO()
    }
}

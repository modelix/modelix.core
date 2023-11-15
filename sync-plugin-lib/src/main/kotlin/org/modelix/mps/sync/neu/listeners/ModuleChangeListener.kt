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

package org.modelix.mps.sync.neu.listeners

import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener

class ModuleChangeListener : SModuleListener {
    override fun modelAdded(module: SModule?, model: SModel?) {
        TODO("Not yet implemented")
    }

    override fun beforeModelRemoved(module: SModule?, model: SModel?) {
        TODO("Not yet implemented")
    }

    override fun modelRemoved(module: SModule?, reference: SModelReference?) {
        TODO("Not yet implemented")
    }

    override fun beforeModelRenamed(module: SModule?, model: SModel?, reference: SModelReference?) {
        TODO("Not yet implemented")
    }

    override fun modelRenamed(module: SModule?, model: SModel?, reference: SModelReference?) {
        TODO("Not yet implemented")
    }

    override fun dependencyAdded(module: SModule?, dependency: SDependency?) {
        TODO("Not yet implemented")
    }

    override fun dependencyRemoved(module: SModule?, dependency: SDependency?) {
        TODO("Not yet implemented")
    }

    override fun languageAdded(module: SModule?, language: SLanguage?) {
        TODO("Not yet implemented")
    }

    override fun languageRemoved(module: SModule?, language: SLanguage?) {
        TODO("Not yet implemented")
    }

    override fun moduleChanged(module: SModule?) {
        TODO("Not yet implemented")
    }
}

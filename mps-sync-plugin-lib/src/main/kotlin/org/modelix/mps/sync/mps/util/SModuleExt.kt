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

package org.modelix.mps.sync.mps.util

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.persistence.ModelCannotBeCreatedException
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.ModelPersistenceWithFixedId

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun SModule.createModel(name: String, id: SModelId): SModel? {
    val modelName = SModelName(name)
    val modelRoot = this.modelRoots.filterIsInstance<DefaultModelRoot>().firstOrNull { it.canCreateModel(modelName) }
    try {
        return modelRoot?.createModel(modelName, null, null, ModelPersistenceWithFixedId(this.moduleReference, id))
    } catch (e: ModelCannotBeCreatedException) {
        logger<SModule>().error("Failed to create model $modelName", e)
    }
    return null
}

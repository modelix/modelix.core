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

package org.modelix.mps.sync.binding

import org.jetbrains.mps.openapi.model.SModel
import org.modelix.model.api.ITree
import org.modelix.mps.sync.ICloudRepository

class ModelPropertiesSynchronizer(modelNodeId: Long, model: SModel, cloudRepository: ICloudRepository) {
    fun syncModelPropertiesFromMPS() {
    }

    fun syncUsedLanguagesAndDevKitsFromMPS() {
        TODO("Not yet implemented")
    }

    fun syncModelImportsFromMPS() {
        TODO("Not yet implemented")
    }

    companion object {
        fun syncModelPropertiesToMPS(tree: ITree, model: SModel, modelNodeId: Long, cloudRepository: ICloudRepository) {
        }
    }
}

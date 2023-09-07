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

package org.modelix.mps.sync

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.DataSource
import org.jetbrains.mps.openapi.persistence.ModelFactory
import org.jetbrains.mps.openapi.persistence.ModelFactoryType
import org.jetbrains.mps.openapi.persistence.ModelLoadingOption
import org.jetbrains.mps.openapi.persistence.datasource.DataSourceType

// TODO must extend DefaultModelPersistence instead of implementing ModelFactory
class ModelPersistenceWithFixedId(val moduleRef: SModuleReference, val modelId: SModelId) : // DefaultModelPersistence
    ModelFactory {
    override fun supports(dataSource: DataSource): Boolean {
        TODO("Should be implemented by DefaultModelPersistence")
    }

    override fun create(dataSource: DataSource, modelName: SModelName, vararg options: ModelLoadingOption?): SModel {
        TODO("Should be implemented by DefaultModelPersistence")
    }

    override fun load(dataSource: DataSource, vararg options: ModelLoadingOption?): SModel {
        TODO("Should be implemented by DefaultModelPersistence")
    }

    override fun save(model: SModel, dataSource: DataSource) {
        TODO("Should be implemented by DefaultModelPersistence")
    }

    override fun getType(): ModelFactoryType {
        TODO("Should be implemented by DefaultModelPersistence")
    }

    override fun getPreferredDataSourceTypes(): MutableList<DataSourceType> {
        TODO("Should be implemented by DefaultModelPersistence")
    }
}

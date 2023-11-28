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

package org.modelix.mps.sync.mps

import jetbrains.mps.extapi.model.SModelData
import jetbrains.mps.persistence.DefaultModelPersistence
import jetbrains.mps.persistence.LazyLoadFacility
import jetbrains.mps.smodel.DefaultSModel
import jetbrains.mps.smodel.DefaultSModelDescriptor
import jetbrains.mps.smodel.SModel
import jetbrains.mps.smodel.SModelHeader
import jetbrains.mps.smodel.loading.ModelLoadResult
import jetbrains.mps.smodel.loading.ModelLoadingState
import jetbrains.mps.smodel.persistence.def.ModelPersistence
import jetbrains.mps.smodel.persistence.def.ModelReadException
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.DataSource
import org.jetbrains.mps.openapi.persistence.ModelLoadingOption
import org.jetbrains.mps.openapi.persistence.ModelSaveException
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.persistence.StreamDataSource
import org.jetbrains.mps.openapi.persistence.UnsupportedDataSourceException
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.io.IOException

/**
 * Uses the provided model ID instead of SModelId.generate().
 * Everything else is just copied from DefaultModelPersistence.
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
open class ModelPersistenceWithFixedId(val moduleRef: SModuleReference, val modelId: SModelId) :
    DefaultModelPersistence() {

    @Throws(UnsupportedDataSourceException::class)
    override fun create(
        dataSource: DataSource,
        modelName: SModelName,
        vararg options: ModelLoadingOption,
    ): org.jetbrains.mps.openapi.model.SModel {
        if (!supports(dataSource)) {
            throw UnsupportedDataSourceException(dataSource)
        }
        val header = SModelHeader.create(ModelPersistence.LAST_VERSION)
        val modelReference: SModelReference =
            PersistenceFacade.getInstance().createModelReference(moduleRef, modelId, modelName.value)
        header.modelReference = modelReference
        val rv = DefaultSModelDescriptor(ModelPersistenceFacility(this, dataSource as StreamDataSource), header)
        if (dataSource.getTimestamp() != -1L) {
            rv.replace(DefaultSModel(modelReference, header))
        }
        return rv
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
open class ModelPersistenceFacility(modelFactory: DefaultModelPersistence, dataSource: StreamDataSource) :
    LazyLoadFacility(modelFactory, dataSource, true) {
    protected val source0: StreamDataSource
        get() = super.getSource() as StreamDataSource

    @Throws(ModelReadException::class)
    override fun readHeader(): SModelHeader {
        return ModelPersistence.loadDescriptor(source0)
    }

    @Throws(ModelReadException::class)
    override fun readModel(header: SModelHeader, state: ModelLoadingState): ModelLoadResult {
        return ModelPersistence.readModel(header, source0, state)
    }

    override fun doesSaveUpgradePersistence(header: SModelHeader): Boolean {
        // not sure !=-1 is really needed, just left to be ensured about compatibility
        return header.persistenceVersion != ModelPersistence.LAST_VERSION && header.persistenceVersion != -1
    }

    @Throws(IOException::class)
    override fun saveModel(header: SModelHeader, modelData: SModelData) {
        try {
            ModelPersistence.saveModel(modelData as SModel, source0, header.persistenceVersion)
        } catch (e: ModelSaveException) {
            throw RuntimeException(e)
        }
    }
}

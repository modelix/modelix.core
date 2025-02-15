package org.modelix.model.mpsadapters

import jetbrains.mps.extapi.model.SModelData
import jetbrains.mps.persistence.DefaultModelPersistence
import jetbrains.mps.persistence.DefaultModelRoot
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
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.DataSource
import org.jetbrains.mps.openapi.persistence.ModelLoadingOption
import org.jetbrains.mps.openapi.persistence.ModelSaveException
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.persistence.StreamDataSource
import org.jetbrains.mps.openapi.persistence.UnsupportedDataSourceException
import java.io.IOException

/**
 * Uses the provided model ID instead of SModelId.generate().
 * Everything else is just copied from DefaultModelPersistence.
 */
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
        val modelReference: SModelReference = PersistenceFacade.getInstance().createModelReference(
            moduleRef.takeIf { !modelId.isGloballyUnique },
            modelId,
            modelName.value,
        )
        header.modelReference = modelReference
        val rv = DefaultSModelDescriptor(ModelPersistenceFacility(this, dataSource as StreamDataSource), header)
        if (dataSource.getTimestamp() != -1L) {
            rv.replace(DefaultSModel(modelReference, header))
        }
        return rv
    }
}

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

internal fun SModule.createModel(name: String, id: SModelId): org.jetbrains.mps.openapi.model.SModel {
    val modelName = SModelName(name)
    val modelRoot = checkNotNull(this.modelRoots.filterIsInstance<DefaultModelRoot>().firstOrNull { it.canCreateModel(modelName) }) {
        "$this contains no model roots for creating new models"
    }
    return modelRoot.createModel(modelName, null, null, ModelPersistenceWithFixedId(this.moduleReference, id))
}

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

import jetbrains.mps.extapi.model.TransientSModel
import jetbrains.mps.smodel.EditableModelDescriptor
import jetbrains.mps.smodel.ModelLoadResult
import jetbrains.mps.smodel.SModel
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.NullDataSource
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.mps.sync.userobject.IUserObjectContainer
import org.modelix.mps.sync.userobject.UserObjectKey

class CloudTransientModel(module: CloudTransientModule, name: String, modelId: SModelId) :
    EditableModelDescriptor(
        createModelRef(name, module.moduleReference, modelId),
        NullDataSource(),
    ),
    EditableSModel,
    IUserObjectContainer,
    TransientSModel {

    companion object {
        private fun createModelRef(
            modelName: String,
            moduleReference: SModuleReference,
            modelId: SModelId,
        ): SModelReference {
            return PersistenceFacade.getInstance().createModelReference(moduleReference, modelId, modelName)
        }
    }

    override fun createModel(): ModelLoadResult<SModel> {
        TODO("Not yet implemented")
    }

    override fun rename(newModelName: String, changeFile: Boolean) {
        TODO("Not yet implemented")
    }

    override fun updateTimestamp() {
        TODO("Not yet implemented")
    }

    override fun needsReloading(): Boolean {
        TODO("Not yet implemented")
    }

    override fun reloadFromSource() {
        TODO("Not yet implemented")
    }

    override fun <T> putUserObject(key: UserObjectKey?, value: T) {
        TODO("Not yet implemented")
    }

    override fun <T> getUserObject(key: UserObjectKey?): T {
        TODO("Not yet implemented")
    }
}

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

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.extapi.model.TransientSModel
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.ide.undo.MPSUndoUtil
import jetbrains.mps.smodel.EditableModelDescriptor
import jetbrains.mps.smodel.ModelLoadResult
import jetbrains.mps.smodel.SModel
import jetbrains.mps.smodel.SNodeUndoableAction
import jetbrains.mps.smodel.loading.ModelLoadingState
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.NullDataSource
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.util.pmap.CustomPMap
import org.modelix.model.util.pmap.SmallPMap.Companion.empty
import org.modelix.mps.sync.MpsReplicatedRepository
import org.modelix.mps.sync.userobject.IUserObjectContainer
import org.modelix.mps.sync.userobject.UserObjectKey

// status: ready to test
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
        ): SModelReference =
            PersistenceFacade.getInstance().createModelReference(moduleReference, modelId, modelName)
    }

    private val logger = logger<CloudTransientModel>()

    private var userObjects: CustomPMap<Any, Any> = empty()

    override fun <T> getUserObject(key: UserObjectKey): T = userObjects[key] as T

    override fun <T> putUserObject(key: UserObjectKey, value: T) {
        userObjects = userObjects.put(key, value as Any)!!
    }

    override fun updateTimestamp() {}

    override fun needsReloading(): Boolean = false

    override fun createModel(): ModelLoadResult<SModel> {
        val smodel = object : SModel(reference) {
            override fun performUndoableAction(action: SNodeUndoableAction) {
                try {
                    val project = CommandProcessor.getInstance().currentCommandProject ?: return
                    val repository = ProjectHelper.getProjectRepository(project) ?: return
                    val affectedNode = action.affectedNode ?: return
                    val rootNode = affectedNode.containingRoot
                    val doc = MPSUndoUtil.getDoc(repository, rootNode.reference)
                    MpsReplicatedRepository.documentChanged(MPSUndoUtil.getRefForDoc(doc))
                } catch (ex: Exception) {
                    logger.error(ex.message, ex)
                }
            }
        }
        return ModelLoadResult<SModel>(smodel, ModelLoadingState.FULLY_LOADED)
    }

    override fun isChanged(): Boolean = false

    override fun save() {}

    override fun rename(newModelName: String, changeFile: Boolean): Unit = throw UnsupportedOperationException()

    override fun isReadOnly(): Boolean = false

    override fun reloadFromSource(): Unit = throw UnsupportedOperationException()
}

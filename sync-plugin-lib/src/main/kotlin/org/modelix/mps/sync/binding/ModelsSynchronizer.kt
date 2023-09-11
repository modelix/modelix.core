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

import jetbrains.mps.lang.migration.runtime.base.VersionFixer
import jetbrains.mps.model.ModelDeleteHelper
import jetbrains.mps.project.ProjectManager
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.Synchronizer
import org.modelix.mps.sync.util.createModel
import org.modelix.mps.sync.util.getModelsWithoutDescriptor

// status: migrated, but needs some bugfixes
// TODO concepts in this class are from the org.modelix.modelix.repositoryconcepts language
// TODO instead of "models" it must be link/Module: models/.getName() --> i.e. get the name of the module.models reference
open class ModelsSynchronizer(cloudParentId: Long, val module: SModule) :
    Synchronizer<SModel>(cloudParentId, "models") {

    open fun getModule() = module

    override fun getMPSChildren(): Iterable<SModel> = module.getModelsWithoutDescriptor().filter { !it.isReadOnly }

    override fun createMPSChild(tree: ITree, cloudChildId: Long): SModel? {
        val id = getModelId(tree, cloudChildId) ?: jetbrains.mps.smodel.SModelId.foreign("cloud-$cloudChildId")
        // TODO instead of "name" it must be property/Model: name/.getName()
        val name = tree.getProperty(cloudChildId, "name")!!
        return createModel(name, id, cloudChildId)
    }

    protected open fun createModel(name: String, id: SModelId, modelNodeId: Long) = module.createModel(name, id)

    override fun removeMPSChild(mpsChild: SModel) = ModelDeleteHelper(mpsChild).delete()

    override fun associate(
        tree: ITree,
        cloudChildren: List<Long>,
        mpsChildren: List<SModel>,
        direction: SyncDirection,
    ): MutableMap<Long, SModel> {
        val result = mutableMapOf<Long, SModel>()
        val availableModels = mpsChildren.toMutableList()

        cloudChildren.forEach { cloudModelId ->
            val id = getModelId(tree, cloudModelId)
            // TODO instead of "name" it must be property/Model: name/.getName()
            val name = tree.getProperty(cloudModelId, "name")

            // There can be models with duplicate names. That's why we can't just search in a map
            val iterator = availableModels.iterator()
            while (iterator.hasNext()) {
                val it = iterator.next()
                if (id != null && it.modelId == id || it.name.value == name) {
                    result[cloudModelId] = it
                    iterator.remove()
                    break
                }
            }
        }

        return result
    }

    private fun getModelId(tree: ITree, cloudModelId: Long): SModelId? {
        // TODO instead of "id" it must be property/Model: id/.getName()
        val serializedId = tree.getProperty(cloudModelId, "id")
        return if (serializedId.isNullOrEmpty()) {
            return null
        } else {
            PersistenceFacade.getInstance().createModelId(serializedId)
        }
    }

    override fun createCloudChild(transaction: IWriteTransaction, mpsChild: SModel): Long {
        // TODO fix last parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
        val modelNodeId = 0L // transaction.addNewChild(cloudParentId, "models", -1, SConceptAdapter.wrap(Model));
        // TODO instead of "id" it must be property/Model: id/.getName()
        transaction.setProperty(modelNodeId, "id", mpsChild.modelId.toString())
        // TODO instead of "name" it must be property/Model: name/.getName()
        transaction.setProperty(modelNodeId, "name", mpsChild.name.value)
        return modelNodeId
    }

    override fun syncToMPS(tree: ITree): Map<Long, SModel> {
        val result = super.syncToMPS(tree)
        val projects = ProjectManager.getInstance().openedProjects
        if (projects.isNotEmpty()) {
            VersionFixer(projects.first(), module, true).updateImportVersions()
        }
        return result
    }
}

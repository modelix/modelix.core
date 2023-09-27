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

import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IConcept
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.Synchronizer
import org.modelix.mps.sync.util.createModule
import java.util.Collections

// status: migrated, but needs some bugfixes
class ProjectModulesSynchronizer(cloudParentId: Long, private val project: MPSProject) :
    Synchronizer<SModule>(cloudParentId, BuiltinLanguages.MPSRepositoryConcepts.Project.modules.getSimpleName()) {

    override fun getMPSChildren(): Iterable<SModule> = project.projectModules.filterIsInstance<Solution>()

    override fun getCloudChildren(tree: ITree): Iterable<Long> {
        return super.getCloudChildren(tree).filter { childId ->
            val concept = tree.getConcept(childId)
            // TODO fixme. SConceptAdapter.wrap does not exist anymore in modelix...
            // concept.isExactly(SConceptAdapter.wrap(concept/Module/)) || concept.isSubConceptOf(SConceptAdapter.wrap(concept/Solution/))
            concept?.let { it.isExactly(concept) || it.isSubConceptOf(concept) } ?: false
        }
    }

    override fun createMPSChild(tree: ITree, cloudChildId: Long): SModule? {
        val id = getModuleId(tree, cloudChildId) ?: ModuleId.foreign("cloud-$cloudChildId")
        val name =
            tree.getProperty(cloudChildId, BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName())
        val concept = tree.getConcept(cloudChildId)
        // TODO fixme. Problem SConceptAdapter.unwrap does not exist anymore in modelix...
        // createModule(name, id, SConceptAdapter.unwrap(concept))
        val module: SModule? = null
        return module
    }

    private fun getModuleId(tree: ITree, cloudModuleId: Long): SModuleId? {
        val serializedId =
            tree.getProperty(cloudModuleId, BuiltinLanguages.MPSRepositoryConcepts.Module.id.getSimpleName())
                ?: return null
        return if (serializedId.isEmpty()) {
            null
        } else {
            PersistenceFacade.getInstance().createModuleId(serializedId)
        }
    }

    private fun createModule(name: String, id: SModuleId, type: IConcept): SModule? {
        // TODO fixme, we need org.modelix.model.repositoryconcepts.Language concept here
        return if (type.isSubConceptOf(null)) {
            null
            // TODO fixme, we need org.modelix.model.repositoryconcepts.DevKit concept here
        } else if (type.isSubConceptOf(null)) {
            null
        } else {
            return project.createModule(name, id as ModuleId, this)
        }
    }

    override fun removeMPSChild(mpsChild: SModule) {
        ModuleDeleteHelper(project).deleteModules(Collections.singletonList(mpsChild), false, true)
        project.removeModule(mpsChild)
    }

    override fun associate(
        tree: ITree,
        cloudChildren: List<Long>,
        mpsChildren: List<SModule>,
        direction: SyncDirection,
    ): MutableMap<Long, SModule> {
        val result = mutableMapOf<Long, SModule>()
        val availableModules = mpsChildren.toMutableList()

        cloudChildren.forEach { cloudModuleId ->
            val id = getModuleId(tree, cloudModuleId)
            val name = tree.getProperty(
                cloudModuleId,
                BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName(),
            )

            // There can be modules with duplicate names. That's why we can't just search in a map.
            val iterator = availableModules.iterator()
            while (iterator.hasNext()) {
                val it = iterator.next()
                if (id != null && it.moduleId == id || it.moduleName == name) {
                    result[cloudModuleId] = it
                    iterator.remove()
                    break
                }
            }
        }

        return result
    }

    override fun createCloudChild(transaction: IWriteTransaction, mpsChild: SModule): Long {
        // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
        // transaction.addNewChild(cloudParentId, BuiltinLanguages.MPSRepositoryConcepts.Project.modules, -1, SConceptAdapter.wrap(concept/Module/))
        val modelNodeId = 0L
        transaction.setProperty(
            modelNodeId,
            BuiltinLanguages.MPSRepositoryConcepts.Module.id.getSimpleName(),
            mpsChild.moduleId.toString(),
        )
        transaction.setProperty(
            modelNodeId,
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName(),
            mpsChild.moduleName,
        )
        return modelNodeId
    }
}

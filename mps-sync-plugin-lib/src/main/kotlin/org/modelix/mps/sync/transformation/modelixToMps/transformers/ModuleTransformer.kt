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

package org.modelix.mps.sync.transformation.modelixToMps.transformers

import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import jetbrains.mps.project.structure.modules.ModuleReference
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.mps.sync.mps.factories.SolutionProducer
import org.modelix.mps.sync.mps.util.runWriteInEDTBlocking
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleTransformer(private val project: MPSProject, private val nodeMap: MpsToModelixMap) {

    @OptIn(UnstableModelixFeature::class)
    private val solutionProducer = SolutionProducer(project)

    fun transformToModule(iNode: INode) {
        val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) ?: ""
        check(serializedId.isNotEmpty()) { "Module's ($iNode) ID is empty" }

        val moduleId = PersistenceFacade.getInstance().createModuleId(serializedId)
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        check(name != null) { "Module's ($iNode) name is null" }

        val sModule = solutionProducer.createOrGetModule(name, moduleId as ModuleId)
        nodeMap.put(sModule, iNode.nodeIdAsLong())

        iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies).forEach {
            transformModuleDependency(it, sModule)
        }
    }

    private fun transformModuleDependency(iNode: INode, module: Solution) {
        val reexport = (
            iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport)
                ?: "false"
            ).toBoolean()
        val uuid = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid)!!
        val moduleId = PersistenceFacade.getInstance().createModuleId(uuid)
        val moduleName = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name)

        val moduleReference = ModuleReference(moduleName, moduleId)
        nodeMap.put(moduleReference, iNode.nodeIdAsLong())

        project.modelAccess.runWriteInEDTBlocking {
            module.addDependency(moduleReference, reexport)
        }
    }
}

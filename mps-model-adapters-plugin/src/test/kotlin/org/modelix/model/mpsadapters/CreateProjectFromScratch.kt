package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages

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

class CreateProjectFromScratch : MpsAdaptersTestBase() {

    fun testModuleCreation() {
        readAction {
            assertEquals(0, mpsProject.projectModules.size)
        }

        val repositoryNode = MPSRepositoryAsNode(mpsProject.repository)

        // The name and ID must be known when creating the solution. It's not possible to first create a new solution
        // and then set its name and ID. Since creating MPS modules via the INode API usually happens during an import
        // we can pass the source node to INode.addNewChild and let implementations handle as much of the import as they
        // need.
        val templateNode = SimpleNode(BuiltinLanguages.MPSRepositoryConcepts.Solution).apply {
            setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, "my.first.solution")
            setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id, "0b49f010-06cf-44a0-8e00-3cc341b9768e")
        }

        val createdSolutionNode = writeActionOnEdt {
            repositoryNode.addNewChild(
                BuiltinLanguages.MPSRepositoryConcepts.Repository.modules,
                -1,
                templateNode,
            )
        } as MPSModuleAsNode

        readAction {
            // Adding the solution to the current project happens implicitly, because it has to be created
            // somewhere on the file system and there is no way yet to specify the location,
            // but a module doesn't have to be part of a project to be available in the MPSModuleRepository.
            assertEquals(1, mpsProject.projectModules.size)
            val createdSolution = mpsProject.projectModules.single()
            assertEquals(createdSolutionNode.module, createdSolution)

            assertEquals("my.first.solution", createdSolution.moduleName)
            assertEquals("my.first.solution", createdSolutionNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name))

            assertEquals("0b49f010-06cf-44a0-8e00-3cc341b9768e", createdSolution.moduleId.toString())
            assertEquals("0b49f010-06cf-44a0-8e00-3cc341b9768e", createdSolutionNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id))
        }
    }
}

package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode

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

class MPSAreaTest : MpsAdaptersTestBase("SimpleProject") {

    fun testResolveModuleInNonExistingProject() {
        val repositoryNode: INode = MPSRepositoryAsNode(mpsProject.repository)
        val area = repositoryNode.getArea()
        readAction {
            val nonExistingProject = MPSProjectReference("nonExistingProject")
            val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
            val projectModuleReference = MPSProjectModuleReference((module.reference as MPSModuleReference).moduleReference, nonExistingProject)

            val resolutionResult = area.resolveNode(projectModuleReference)

            assertNull(resolutionResult)
        }
    }
}

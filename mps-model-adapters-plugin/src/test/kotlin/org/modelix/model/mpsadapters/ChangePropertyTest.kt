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

class ChangePropertyTest : MpsAdaptersTestBase("SimpleProject") {

    fun testModuleCreation() {
        readAction {
            assertEquals(1, mpsProject.projectModules.size)
        }

        val repositoryNode: INode = MPSRepositoryAsNode(mpsProject.repository)

        writeActionOnEdt {
            val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).single()
            val rootNode = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single()
            assertEquals("Class1", rootNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name))
            rootNode.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, "MyRenamedClass")
            assertEquals("MyRenamedClass", rootNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name))
        }
    }
}

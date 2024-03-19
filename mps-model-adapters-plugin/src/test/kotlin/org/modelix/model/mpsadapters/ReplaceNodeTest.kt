/*
 * Copyright (c) 2024.
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

package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode

class ReplaceNodeTest : MpsAdaptersTestBase("SimpleProject") {

    fun testReplaceNode() {
        readAction {
            assertEquals(1, mpsProject.projectModules.size)
        }

        val repositoryNode: INode = MPSRepositoryAsNode(mpsProject.repository)

        writeActionOnEdt {
            val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).single()
            val rootNode = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single()

            val oldProperties = rootNode.getAllProperties().toSet()
            val oldReferences = rootNode.getAllReferenceTargetRefs().toSet()
            val oldChildren = rootNode.allChildren.toList()

            val newConcept = ConceptReference("mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1083245097125")
            val newNode = rootNode.replaceNode(newConcept)

            assertEquals(oldProperties, newNode.getAllProperties().toSet())
            assertEquals(oldReferences, newNode.getAllReferenceTargetRefs().toSet())
            assertEquals(oldChildren, newNode.allChildren.toList())
            assertEquals(newConcept, newNode.getConceptReference())
        }
    }
}

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

class ChangeReferenceTest : MpsAdaptersTestBase("SimpleProject") {

    fun testCanRemoveReference() {
        // This is some reference link that is technically not part of the concept of the node it is used with.
        // But for this test, this is fine because nodes might have invalid references.
        val referenceLink = BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model
        val repositoryNode: INode = MPSRepositoryAsNode(mpsProject.repository)
        runCommandOnEDT {
            val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1.model1" }
            val rootNode = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single()
            rootNode.setReferenceTarget(referenceLink, rootNode)
            assertEquals(rootNode, rootNode.getReferenceTarget(referenceLink))

            rootNode.setReferenceTarget(referenceLink, null as INode?)

            assertEquals(null, rootNode.getReferenceTarget(referenceLink))
        }
    }

    fun testCanNotSetNonMPSNodeAsReferenceTarget() {
        val referenceLink = BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model
        val repositoryNode: INode = MPSRepositoryAsNode(mpsProject.repository)
        runCommandOnEDT {
            val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1.model1" }
            val rootNode = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single()

            try {
                rootNode.setReferenceTarget(referenceLink, model)
                fail("Expected exception")
            } catch (e: IllegalArgumentException) {
                assertEquals(e.message, "`target` has to be an `MPSNode` or `null`.")
            }
        }
    }
}

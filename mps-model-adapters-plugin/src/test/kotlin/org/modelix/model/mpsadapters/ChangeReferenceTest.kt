package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode

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

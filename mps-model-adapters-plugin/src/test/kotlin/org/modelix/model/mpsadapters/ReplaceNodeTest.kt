package org.modelix.model.mpsadapters

import org.junit.Ignore
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.IReplaceableNode

@Ignore("Replacing a node through MPS-model-adapters is broken. See MODELIX-920")
class ReplaceNodeTest : MpsAdaptersTestBase("SimpleProject") {

    fun testReplaceNode() {
        readAction {
            assertEquals(1, mpsProject.projectModules.size)
        }

        val repositoryNode: INode = MPSRepositoryAsNode(mpsProject.repository)

        runCommandOnEDT {
            val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1.model1" }

            val rootNode = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single() as IReplaceableNode

            val oldProperties = rootNode.getAllProperties().toSet()
            check(oldProperties.isNotEmpty()) { "Test should replace node with properties." }
            val oldReferences = rootNode.getAllReferenceTargetRefs().toSet()
            check(oldReferences.isNotEmpty()) { "Test should replace node with references." }
            val oldChildren = rootNode.allChildren.toList()
            check(oldChildren.isNotEmpty()) { "Test should replace node with children." }

            val newConcept = ConceptReference("mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1083245097125")
            val newNode = rootNode.replaceNode(newConcept)

            assertEquals(oldProperties, newNode.getAllProperties().toSet())
            assertEquals(oldReferences, newNode.getAllReferenceTargetRefs().toSet())
            assertEquals(oldChildren, newNode.allChildren.toList())
            assertEquals(newConcept, newNode.getConceptReference())
        }
    }
}

package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IReplaceableNode

class ReplaceNodeTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test replace node`() = runCommandOnEDT {
        val rootNode = getRootUnderTest()
        val nodeToReplace = rootNode.allChildren.first() as IReplaceableNode
        val oldProperties = nodeToReplace.getAllProperties().toSet()
        check(oldProperties.isNotEmpty()) { "Test should replace node with properties." }
        val oldReferences = nodeToReplace.getAllReferenceTargetRefs().toSet()
        check(oldReferences.isNotEmpty()) { "Test should replace node with references." }
        val oldChildren = nodeToReplace.allChildren.toList()
        check(oldChildren.isNotEmpty()) { "Test should replace node with children." }
        val newConcept = ConceptReference("mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1083245097125")

        val newNode = nodeToReplace.replaceNode(newConcept)

        assertEquals(oldProperties, newNode.getAllProperties().toSet())
        assertEquals(oldReferences, newNode.getAllReferenceTargetRefs().toSet())
        assertEquals(oldChildren, newNode.allChildren.toList())
        assertEquals(newConcept, newNode.getConceptReference())
    }

    fun `test fail to replace node without parent`() = runCommandOnEDT {
        val rootNode = getRootUnderTest()
        val oldChildren = rootNode.allChildren.toList()
        check(oldChildren.isNotEmpty()) { "Test should replace node with children." }

        val newConcept = ConceptReference(BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept.getUID())

        val expectedMessage = "Cannot replace node `Class1` because it has no parent."
        assertThrows(IllegalArgumentException::class.java, expectedMessage) {
            rootNode.replaceNode(newConcept)
        }
        // Assert that precondition is check before children are deleted.
        assertEquals(oldChildren, rootNode.allChildren.toList())
    }

    fun `test fail to replace node with null concept`() = runCommandOnEDT {
        val rootNode = getRootUnderTest()
        val nodeToReplace = rootNode.allChildren.first() as IReplaceableNode

        val expectedMessage = "Cannot replace node `method1` with a null concept. Explicitly specify a concept (e.g., `BaseConcept`)."
        assertThrows(IllegalArgumentException::class.java, expectedMessage) {
            nodeToReplace.replaceNode(null)
        }
    }

    fun `test fail to replace node with non mps concept`() = runCommandOnEDT {
        val rootNode = getRootUnderTest()
        val nodeToReplace = rootNode.allChildren.first() as IReplaceableNode
        val newConcept = ConceptReference("notMpsConcept")

        val expectedMessage = "Concept UID `notMpsConcept` cannot be parsed as MPS concept."
        assertThrows(IllegalArgumentException::class.java, expectedMessage) {
            nodeToReplace.replaceNode(newConcept)
        }
    }

    private fun getRootUnderTest(): IReplaceableNode {
        val repositoryNode = MPSRepositoryAsNode(mpsProject.repository)
        val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
            .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
        val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).single()
        return model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single() as IReplaceableNode
    }
}

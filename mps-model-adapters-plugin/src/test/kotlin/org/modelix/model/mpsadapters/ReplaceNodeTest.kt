package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.SNode
import jetbrains.mps.smodel.adapter.MetaAdapterByDeclaration
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.IReplaceableNode

class ReplaceNodeTest : MpsAdaptersTestBase("SimpleProject") {

    private val sampleConcept = MetaAdapterByDeclaration.asInstanceConcept(
        MPSConcept.tryParseUID(BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept.getUID())!!.concept,
    )

    fun `test replace node with parent and module (aka regular node)`() = runCommandOnEDT {
        val rootNode = getRootUnderTest()
        val nodeToReplace = rootNode.allChildren.first() as IReplaceableNode
        val oldContainmentLink = nodeToReplace.getContainmentLink()
        val nodesToKeep = rootNode.allChildren.drop(1)
        val oldProperties = nodeToReplace.getAllProperties().toSet()
        check(oldProperties.isNotEmpty()) { "Test should replace node with properties." }
        val oldReferences = nodeToReplace.getAllReferenceTargetRefs().toSet()
        check(oldReferences.isNotEmpty()) { "Test should replace node with references." }
        val oldChildren = nodeToReplace.allChildren.toList()
        check(oldChildren.isNotEmpty()) { "Test should replace node with children." }
        val newConcept = ConceptReference("mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1083245097125")

        val newNode = nodeToReplace.replaceNode(newConcept)

        assertEquals(listOf(newNode) + nodesToKeep, rootNode.allChildren.toList())
        assertEquals((nodeToReplace as MPSNode).node.nodeId, (newNode as MPSNode).node.nodeId)
        assertEquals(oldContainmentLink, newNode.getContainmentLink())
        assertEquals(newConcept, newNode.getConceptReference())
        assertEquals(oldProperties, newNode.getAllProperties().toSet())
        assertEquals(oldReferences, newNode.getAllReferenceTargetRefs().toSet())
        assertEquals(oldChildren, newNode.allChildren.toList())
    }

    fun `test replace node without parent but with module (aka root node)`() = runCommandOnEDT {
        val rootNode = getRootUnderTest()
        val oldContainmentLink = rootNode.getContainmentLink()
        val model = getModelUnderTest()
        val newConcept = ConceptReference("mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1083245097125")

        val newNode = rootNode.replaceNode(newConcept)

        assertEquals(listOf(newNode), model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes))
        assertEquals((rootNode as MPSNode).node.nodeId, (newNode as MPSNode).node.nodeId)
        assertEquals(oldContainmentLink, newNode.getContainmentLink())
        assertEquals(newConcept, newNode.getConceptReference())
    }

    fun `test replace node without parent and without module (aka free-floating node)`() = runCommandOnEDT {
        val untouchedRootNode = getRootUnderTest()
        val model = getModelUnderTest()
        val freeFloatingSNode = SNode(sampleConcept)
        val freeFloatingNode = MPSNode(freeFloatingSNode)
        val oldContainmentLink = freeFloatingNode.getContainmentLink()
        val newConcept = ConceptReference("mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1083245097125")

        val newNode = freeFloatingNode.replaceNode(newConcept)

        assertEquals(listOf(untouchedRootNode), model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes))
        assertEquals(freeFloatingNode.node.nodeId, (newNode as MPSNode).node.nodeId)
        assertEquals(oldContainmentLink, newNode.getContainmentLink())
        assertEquals(newConcept, newNode.getConceptReference())
    }

    fun `test replace node with parent but without module (aka descendant of free-floating node)`() = runCommandOnEDT {
        val freeFloatingSNode = SNode(sampleConcept)
        val freeFloatingNode = MPSNode(freeFloatingSNode)
        val nodeToReplace = freeFloatingNode.addNewChild(
            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages,
            -1,
            BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept,
        ) as IReplaceableNode
        val oldContainmentLink = nodeToReplace.getContainmentLink()
        val newConcept = ConceptReference("mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1083245097125")

        val newNode = nodeToReplace.replaceNode(newConcept)

        assertEquals(listOf(newNode), freeFloatingNode.allChildren.toList())
        assertEquals((nodeToReplace as MPSNode).node.nodeId, (newNode as MPSNode).node.nodeId)
        assertEquals(oldContainmentLink, newNode.getContainmentLink())
        assertEquals(newConcept, newNode.getConceptReference())
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

    private fun getModelUnderTest(): INode {
        val repositoryNode = MPSRepositoryAsNode(mpsProject.repository)
        val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
            .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
        return module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).single()
    }

    private fun getRootUnderTest(): IReplaceableNode = getModelUnderTest()
        .getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single() as IReplaceableNode
}

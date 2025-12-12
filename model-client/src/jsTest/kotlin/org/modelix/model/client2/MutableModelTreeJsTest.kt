package org.modelix.model.client2

import GeneratedConcept
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MutableModelTreeJsTest {

    private val emptyRoot = """
        {
            "root": {
            }
        }
    """.trimIndent()

    private val rootWithChild = """
        {
            "root": {
                "children": [
                    {
                        "id": "aNode"
                    }
                ]
            }
        }
    """.trimIndent()

    @Test
    fun canResolveNode() {
        // Arrange
        val branch = loadModelsFromJsonAsBranch(arrayOf(rootWithChild))
        val aNode = branch.rootNode.getAllChildren()[0]
        val aNodeReference = aNode.getReference()

        // Act
        val resolvedNode = branch.resolveNode(aNodeReference)

        // Assert
        assertEquals(aNode, resolvedNode)
    }

    @Test
    fun canResolveNodeNonExistingNode() {
        // Arrange
        val data = """
        {
            "root": {
                "children": [
                    {
                        "id": "aNode"
                    }
                ]
            }
        }
        """.trimIndent()
        val branch = loadModelsFromJsonAsBranch(arrayOf(data))
        val aNode = branch.rootNode.getAllChildren()[0]
        val aNodeReference = aNode.getReference()
        branch.rootNode.removeChild(aNode)

        // Act
        val resolvedNode = branch.resolveNode(aNodeReference)

        // Assert
        assertNull(resolvedNode)
    }

    @Test
    fun changeHandlerCanBeAdded() {
        val branch = loadModelsFromJsonAsBranch(arrayOf(rootWithChild))
        var changeCount = 0
        val changeListener: ChangeHandler = { _ -> changeCount++ }
        branch.addListener(changeListener)

        val aNode = branch.rootNode.getAllChildren()[0]
        branch.rootNode.removeChild(aNode)

        assertEquals(1, changeCount)
    }

    @Test
    fun changeHandlerCanBeRemoved() {
        val branch = loadModelsFromJsonAsBranch(arrayOf(rootWithChild))
        var changeCount = 0
        val changeListener: ChangeHandler = { _ -> changeCount++ }
        branch.addListener(changeListener)
        branch.removeListener(changeListener)

        val aNode = branch.rootNode.getAllChildren()[0]
        branch.rootNode.removeChild(aNode)

        assertEquals(0, changeCount)
    }

    @Test
    fun changeDetectionWorksForPropertyUpdate() {
        // Arrange
        var propertyChanged = 0
        val branch = loadModelsFromJsonAsBranch(arrayOf(emptyRoot))
        branch.addListener {
            when (it) {
                is PropertyChanged -> propertyChanged++
                else -> {}
            }
        }
        val rootNode = branch.rootNode

        // Act
        rootNode.setPropertyValue("aProperty", "aValue")

        // Assert
        assertEquals(1, propertyChanged)
    }

    @Test
    fun changeDetectionWorksForReferenceUpdate() {
        // Arrange
        var referenceChanged = 0
        val branch = loadModelsFromJsonAsBranch(arrayOf(emptyRoot))
        branch.addListener {
            when (it) {
                is ReferenceChanged -> referenceChanged++
                else -> {}
            }
        }
        val rootNode = branch.rootNode

        // Act
        rootNode.setReferenceTargetNode("aRef", rootNode)

        // Assert
        assertEquals(1, referenceChanged)
    }

    @Test
    fun changeDetectionWorksForAddedChild() {
        // Arrange
        var childrenChanged = 0
        val branch = loadModelsFromJsonAsBranch(arrayOf(emptyRoot))
        branch.addListener {
            when (it) {
                is ChildrenChanged -> childrenChanged++
                else -> {}
            }
        }
        val rootNode = branch.rootNode

        // Act
        rootNode.addNewChild("aRole", -1, GeneratedConcept("aConceptUid"))

        // Assert
        assertEquals(1, childrenChanged)
    }

    @Test
    fun changeDetectionWorksForMovedChild() {
        // Arrange
        var childrenChanged = 0
        var containmentChanged = 0
        val branch = loadModelsFromJsonAsBranch(arrayOf(emptyRoot))
        branch.addListener {
            when (it) {
                is ChildrenChanged -> childrenChanged++
                is ContainmentChanged -> containmentChanged++
                else -> {}
            }
        }
        val rootNode = branch.rootNode
        val childNode = rootNode.addNewChild("aRole", -1, GeneratedConcept("aConceptUid"))
        childrenChanged = 0

        // Act
        rootNode.moveChild("anotherRole", -1, childNode)

        // Assert
        assertEquals(2, childrenChanged)
        assertEquals(1, containmentChanged)
    }

    @Test
    fun changeDetectionWorksForRemovedChild() {
        // Arrange
        var childrenChanged = 0
        var containmentChanged = 0
        val branch = loadModelsFromJsonAsBranch(arrayOf(emptyRoot))
        branch.addListener {
            when (it) {
                is ChildrenChanged -> childrenChanged++
                is ContainmentChanged -> containmentChanged++
                else -> {}
            }
        }
        val rootNode = branch.rootNode
        val childNode = rootNode.addNewChild("aRole", -1, GeneratedConcept("aConceptUid"))
        childrenChanged = 0

        // Act
        rootNode.removeChild(childNode)

        // Assert
        assertEquals(1, childrenChanged)
    }

    @Test
    fun compositeModelResolvesNodesFromMultipleTrees() {
        // Arrange - Create two separate models
        val model1Json = """
        {
            "root": {
                "id": "model1Root",
                "children": [
                    {
                        "id": "model1Child"
                    }
                ]
            }
        }
        """.trimIndent()

        val model2Json = """
        {
            "root": {
                "id": "model2Root",
                "children": [
                    {
                        "id": "model2Child"
                    }
                ]
            }
        }
        """.trimIndent()

        // Create a composite branch with both models
        val branch = loadModelsFromJsonAsBranch(arrayOf(model1Json, model2Json))
        val rootNodes = branch.getRootNodes()

        // Act & Assert - Verify both models are accessible
        assertEquals(2, rootNodes.size, "Should have 2 root nodes")

        val model1Child = rootNodes[0].getAllChildren()[0]
        val model2Child = rootNodes[1].getAllChildren()[0]

        // Act - Get references from each model
        val model1ChildRef = model1Child.getReference()
        val model2ChildRef = model2Child.getReference()

        // Assert - Both nodes should be resolvable from the composite branch
        val resolved1 = branch.resolveNode(model1ChildRef)
        val resolved2 = branch.resolveNode(model2ChildRef)

        assertEquals(model1Child, resolved1, "Should resolve node from first model")
        assertEquals(model2Child, resolved2, "Should resolve node from second model")
    }

    @Test
    fun compositeModelChangeListenerResolvesNodesFromAllTrees() {
        // Arrange - Create two separate models
        val model1Json = """
        {
            "root": {
                "id": "model1Root"
            }
        }
        """.trimIndent()

        val model2Json = """
        {
            "root": {
                "id": "model2Root"
            }
        }
        """.trimIndent()

        val branch = loadModelsFromJsonAsBranch(arrayOf(model1Json, model2Json))
        val rootNodes = branch.getRootNodes()

        var changesReceived = 0
        var changeNodeFromModel1 = false
        var changeNodeFromModel2 = false

        branch.addListener { change ->
            changesReceived++
            when (change) {
                is PropertyChanged -> {
                    // Verify that the node in the change event can be accessed
                    val changedNode = change.node
                    // Check which model the changed node belongs to
                    if (changedNode == rootNodes[0]) {
                        changeNodeFromModel1 = true
                    } else if (changedNode == rootNodes[1]) {
                        changeNodeFromModel2 = true
                    }
                }
                else -> {}
            }
        }

        // Act - Make changes to both models
        rootNodes[0].setPropertyValue("prop1", "value1")
        rootNodes[1].setPropertyValue("prop2", "value2")

        // Assert - Both changes should be detected
        assertEquals(2, changesReceived, "Should receive 2 change events")
        assert(changeNodeFromModel1) { "Should detect change in model 1" }
        assert(changeNodeFromModel2) { "Should detect change in model 2" }
    }

    @Test
    fun compositeModelReturnsConsistentNodeWrappersForSameNode() {
        // Arrange - Create a model with a child node
        val modelJson = """
        {
            "root": {
                "id": "rootNode",
                "children": [
                    {
                        "id": "childNode"
                    }
                ]
            }
        }
        """.trimIndent()

        val branch = loadModelsFromJsonAsBranch(arrayOf(modelJson))
        val rootNode = branch.rootNode
        val childNode = rootNode.getAllChildren()[0]
        val childRef = childNode.getReference()

        // Act - Resolve the same node reference multiple times
        val resolved1 = branch.resolveNode(childRef)
        val resolved2 = branch.resolveNode(childRef)

        // Assert - Should return the exact same wrapper object (identity check)
        // This is critical for Vue.js reactivity cache to work correctly
        assertEquals(resolved1, resolved2, "Should return equal nodes")
    }
}

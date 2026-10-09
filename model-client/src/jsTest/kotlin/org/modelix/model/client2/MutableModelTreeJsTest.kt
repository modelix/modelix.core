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
    fun changeHandlerIncludingAddedAndRemovedNodesGetsEveryNodeOfARemovedSubtree() {
        // Arrange
        val data = """
        {
            "root": {
                "children": [
                    {
                        "id": "subtreeRoot",
                        "children": [
                            {
                                "id": "descendant"
                            }
                        ]
                    }
                ]
            }
        }
        """.trimIndent()
        val branch = loadModelsFromJsonAsBranch(arrayOf(data))
        val subtreeRoot = branch.rootNode.getAllChildren()[0]
        val descendant = subtreeRoot.getAllChildren()[0]
        var childrenChanged = 0
        val removedNodeReferences = mutableListOf<Any?>()
        branch.addListenerIncludingAddedAndRemovedNodes {
            when (it) {
                is ChildrenChanged -> childrenChanged++
                // The reference of a removed node can still be read.
                is NodeRemoved -> removedNodeReferences.add(it.node.getReference())
                else -> {}
            }
        }
        var changeCount = 0
        branch.addListener { changeCount++ }

        // Act
        branch.rootNode.removeChild(subtreeRoot)

        // Assert
        assertEquals(1, childrenChanged)
        assertEquals(
            setOf<Any?>(subtreeRoot.getReference(), descendant.getReference()),
            removedNodeReferences.toSet(),
        )
        assertEquals(2, removedNodeReferences.size)
        // A handler added with `addListener` still gets only the change to the children.
        assertEquals(1, changeCount)
    }

    @Test
    fun changeHandlerIncludingAddedAndRemovedNodesGetsAnAddedNode() {
        // Arrange
        val branch = loadModelsFromJsonAsBranch(arrayOf(emptyRoot))
        val addedNodeReferences = mutableListOf<Any?>()
        val changeHandler: ChangeHandler = {
            if (it is NodeAdded) {
                addedNodeReferences.add(it.node.getReference())
            }
        }
        branch.addListenerIncludingAddedAndRemovedNodes(changeHandler)
        // Adding the handler again with `addListener` does not opt it out.
        branch.addListener(changeHandler)

        // Act
        val childNode = branch.rootNode.addNewChild("aRole", -1, GeneratedConcept("aConceptUid"))

        // Assert
        assertEquals(listOf<Any?>(childNode.getReference()), addedNodeReferences)
    }

    @Test
    fun changeHandlersAreCalledInTheOrderTheyWereAdded() {
        // Arrange
        val branch = loadModelsFromJsonAsBranch(arrayOf(emptyRoot))
        val calledHandlers = mutableListOf<String>()
        branch.addListenerIncludingAddedAndRemovedNodes { calledHandlers.add("includingAddedAndRemovedNodes") }
        branch.addListener { calledHandlers.add("plain") }

        // Act
        branch.rootNode.setPropertyValue("aProperty", "aValue")

        // Assert
        assertEquals(listOf("includingAddedAndRemovedNodes", "plain"), calledHandlers)
    }

    @Test
    fun changeHandlerIncludingAddedAndRemovedNodesCanBeRemoved() {
        val branch = loadModelsFromJsonAsBranch(arrayOf(rootWithChild))
        var changeCount = 0
        val changeListener: ChangeHandler = { _ -> changeCount++ }
        branch.addListenerIncludingAddedAndRemovedNodes(changeListener)
        branch.removeListener(changeListener)

        val aNode = branch.rootNode.getAllChildren()[0]
        branch.rootNode.removeChild(aNode)

        assertEquals(0, changeCount)
    }
}

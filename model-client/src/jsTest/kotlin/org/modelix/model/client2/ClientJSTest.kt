package org.modelix.model.client2

import GeneratedConcept
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientJSTest {

    private val emptyRoot = """
        {
            "root": {
            }
        }
    """.trimIndent()

    @Test
    fun canAddChildrenWithUnregisteredConcept() {
        // Arrange
        val rootNode = loadModelsFromJson(arrayOf(emptyRoot))
        val jsConcept = GeneratedConcept("aConceptUid")

        // Act
        // This call should work, even if no GeneratedLanguage for this concept
        // was registered in the global ILanguageRepository.
        rootNode.addNewChild("aRole", -1, jsConcept)

        // Assert
        val children = rootNode.getChildren("aRole")
        assertEquals(1, children.size)
        assertEquals("aConceptUid", children.get(0).getConceptUID())
    }

    @Test
    fun canResolveReference() {
        // Arrange
        val data = """
        {
            "root": {
                "children": [
                    {
                        "id": "child0"
                    },
                    {
                        "id": "child1"
                    }
                ]
            }
        }
        """.trimIndent()
        val rootNode = loadModelsFromJson(arrayOf(data))
        val child0 = rootNode.getAllChildren()[0]
        val child1 = rootNode.getAllChildren()[1]

        // Act
        child0.setReferenceTargetRef("aReference", child1.getReference())

        // Assert
        assertEquals(child0.getReferenceTargetNode("aReference"), child1)
    }

    @Test
    fun extractBindingKey_returnsKeyForBranchRequest() {
        val key = extractBindingKey("/v2/repositories/myRepo/branches/main/delta")
        assertEquals("myRepo/main", key)
    }

    @Test
    fun extractBindingKey_returnsNullForNonBranchRequest() {
        assertNull(extractBindingKey("/v2/server-id"))
        assertNull(extractBindingKey("/v2/client-id"))
        assertNull(extractBindingKey("/v2/repositories/myRepo"))
    }
}

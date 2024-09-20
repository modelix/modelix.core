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

package org.modelix.model.client2

import GeneratedConcept
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

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

import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.SimpleConcept
import toJS
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(UnstableModelixFeature::class)
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
        val rootNode = loadModelsFromJson(arrayOf(emptyRoot)) {}
        val jsConcept = SimpleConcept("aConceptUid").toJS()

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
    fun changeDetectionWorksForPropertyUpdate() {
        // Arrange
        var propertyChanged = 0
        val rootNode = loadModelsFromJson(arrayOf(emptyRoot)) {
            when (it) {
                is PropertyChanged -> propertyChanged++
                else -> {}
            }
        }

        // Act
        rootNode.setPropertyValue("aProperty", "aValue")

        // Assert
        assertEquals(1, propertyChanged)
    }

    @Test
    fun changeDetectionWorksForReferenceUpdate() {
        // Arrange
        var referenceChanged = 0
        val rootNode = loadModelsFromJson(arrayOf(emptyRoot)) {
            when (it) {
                is ReferenceChanged -> referenceChanged++
                else -> {}
            }
        }

        // Act
        rootNode.setReferenceTargetNode("aRef", rootNode)

        // Assert
        assertEquals(1, referenceChanged)
    }

    @Test
    fun changeDetectionWorksForAddedChild() {
        // Arrange
        var childrenChanged = 0
        val rootNode = loadModelsFromJson(arrayOf(emptyRoot)) {
            when (it) {
                is ChildrenChanged -> childrenChanged++
                else -> {}
            }
        }

        // Act
        rootNode.addNewChild("aRole", -1, SimpleConcept("aConceptUid").toJS())

        // Assert
        assertEquals(1, childrenChanged)
    }

    @Test
    fun changeDetectionWorksForMovedChild() {
        // Arrange
        var childrenChanged = 0
        var containmentChanged = 0
        val rootNode = loadModelsFromJson(arrayOf(emptyRoot)) {
            when (it) {
                is ChildrenChanged -> childrenChanged++
                is ContainmentChanged -> containmentChanged++
                else -> {}
            }
        }
        val childNode = rootNode.addNewChild("aRole", -1, SimpleConcept("aConceptUid").toJS())
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
        val rootNode = loadModelsFromJson(arrayOf(emptyRoot)) {
            when (it) {
                is ChildrenChanged -> childrenChanged++
                is ContainmentChanged -> containmentChanged++
                else -> {}
            }
        }
        val childNode = rootNode.addNewChild("aRole", -1, SimpleConcept("aConceptUid").toJS())
        childrenChanged = 0

        // Act
        rootNode.removeChild(childNode)

        // Assert
        assertEquals(1, childrenChanged)
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
        val rootNode = loadModelsFromJson(arrayOf(data), {})
        val child0 = rootNode.getAllChildren()[0]
        val child1 = rootNode.getAllChildren()[1]

        // Act
        child0.setReferenceTargetRef("aReference", child1.getReference())

        // Assert
        assertEquals(child0.getReferenceTargetNode("aReference"), child1)
    }
}

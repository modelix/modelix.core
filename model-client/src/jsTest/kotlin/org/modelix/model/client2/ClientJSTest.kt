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
import org.modelix.kotlin.utils.UnstableModelixFeature
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(UnstableModelixFeature::class)
class ClientJSTest {

    @Test
    fun canAddChildrenWithUnregisterConcept() {
        val data = """
        {
            "root": {
            }
        }
        """.trimIndent()
        val rootNode = loadModelsFromJson(arrayOf(data)) {}
        val jsConcept = GeneratedConcept("aConceptUid")

        // This call should work, even if no GeneratedLanguage for this concept
        // was registered in the global ILanguageRepository.
        rootNode.addNewChild("aRole", -1, jsConcept)

        val children = rootNode.getChildren("aRole")
        assertEquals(1, children.size)
        assertEquals("aConceptUid", children.get(0).getConceptUID())
    }

    @Test
    fun changeDetectionWorks() {
        val data = """
        {
            "root": {
            }
        }
        """.trimIndent()
        var propertyChanged = 0
        var referenceChanged = 0
        var childrenChanged = 0
        var containmentChanged = 0

        val rootNode = loadModelsFromJson(arrayOf(data)) {
            when (it) {
                is PropertyChanged -> propertyChanged++
                is ReferenceChanged -> referenceChanged++
                is ChildrenChanged -> childrenChanged++
                is ContainmentChanged -> containmentChanged++
            }
        }

        rootNode.setPropertyValue("aProperty", "aValue")
        assertEquals(1, propertyChanged)

        rootNode.setReferenceTargetNode("aRef", rootNode)
        assertEquals(1, referenceChanged)

        val childNode = rootNode.addNewChild("aRole", -1, GeneratedConcept("aConceptUid"))
        assertEquals(1, childrenChanged)

        rootNode.moveChild("anotherRole", -1, childNode)
        assertEquals(3, childrenChanged)
        assertEquals(1, containmentChanged)

        rootNode.removeChild(childNode)
        assertEquals(4, childrenChanged)
    }

    @Test
    fun canResolveReference() {
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
        child0.setReferenceTargetRef("aReference", child1.getReference())
        assertEquals(child0.getReferenceTargetNode("aReference"), child1)
    }
}

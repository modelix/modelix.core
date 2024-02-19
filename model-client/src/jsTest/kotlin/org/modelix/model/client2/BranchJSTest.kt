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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(UnstableModelixFeature::class)
class BranchJSTest {

    @Test
    fun canResolveNode() {
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
        val branch = loadModelsFromJsonAsBranch(arrayOf(data)) {}
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
        val branch = loadModelsFromJsonAsBranch(arrayOf(data)) {}
        val aNode = branch.rootNode.getAllChildren()[0]
        val aNodeReference = aNode.getReference()
        branch.rootNode.removeChild(aNode)

        // Act
        val resolvedNode = branch.resolveNode(aNodeReference)

        // Assert
        assertNull(resolvedNode)
    }
}

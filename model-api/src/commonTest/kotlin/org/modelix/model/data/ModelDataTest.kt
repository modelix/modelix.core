/*
 * Copyright (c) 2024.
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

package org.modelix.model.data

import org.modelix.model.api.ChildLinkFromName
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.persistent.MapBasedStore
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelDataTest {

    @Test
    fun nodeWithConceptReferenceButWithoutRegisteredConceptCanSerialized() {
        val aChildLink = ChildLinkFromName("aChildLink")
        val aConceptReference = ConceptReference("aConceptReference")
        val rootNode = createEmptyRootNode()
        rootNode.addNewChild(aChildLink, -1, aConceptReference)

        val nodeData = rootNode.asData()

        val child = nodeData.children.single { child -> child.role == aChildLink.getUID() }
        assertEquals(aConceptReference.getUID(), child.concept)
    }
}

internal fun createEmptyRootNode(): INode {
    val tree = CLTree(NonCachingObjectStore(MapBasedStore()))
    val branch = TreePointer(tree, IdGenerator.getInstance(1))
    return branch.getRootNode()
}

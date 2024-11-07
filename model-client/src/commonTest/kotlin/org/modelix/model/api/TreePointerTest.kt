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

package org.modelix.model.api

import org.modelix.model.ModelFacade
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.client.IdGenerator
import org.modelix.streams.getSynchronous
import kotlin.test.Test
import kotlin.test.assertEquals

class TreePointerTest {

    @Test
    fun references_can_be_resolved() {
        val branch = TreePointer(ModelFacade.newLocalTree(useRoleIds = false), IdGenerator.newInstance(1))
        val rootNode = branch.getRootNode()
        val role = IReferenceLinkReference.fromName("refA").toLegacy()
        rootNode.setReferenceTarget(role, rootNode)
        assertEquals(rootNode, rootNode.getReferenceTarget(role))
    }

    @Test
    fun references_can_be_resolved_async() {
        val branch = TreePointer(ModelFacade.newLocalTree(useRoleIds = false), IdGenerator.newInstance(1))
        val rootNode = branch.getRootNode()
        val role = IReferenceLinkReference.fromName("refA")
        rootNode.setReferenceTarget(role.toLegacy(), rootNode)
        assertEquals(rootNode, rootNode.asAsyncNode().getReferenceTarget(role).getSynchronous()?.asRegularNode())
    }
}

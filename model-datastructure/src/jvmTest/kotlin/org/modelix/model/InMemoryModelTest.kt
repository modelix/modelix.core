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

package org.modelix.model

import org.junit.Test
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.MapBasedStore
import kotlin.test.assertEquals

class InMemoryModelTest {

    @Test
    fun `built-in properties can be resolved - use role ids`() = `built-in properties can be resolved`(true)

    @Test
    fun `built-in properties can be resolved - use role names`() = `built-in properties can be resolved`(false)

    private fun `built-in properties can be resolved`(useRoleIds: Boolean) {
        val tree = CLTree(null as CPTree?, null as RepositoryId?, ObjectStoreCache(MapBasedStore()), useRoleIds = useRoleIds)
        val treePointer = TreePointer(tree, IdGenerator.getInstance(1))
        val expected = "MyModel"

        treePointer.runWrite {
            val root = treePointer.getRootNode()
            root.addNewChild(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules, -1, BuiltinLanguages.MPSRepositoryConcepts.Module).apply {
                setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, expected)
            }
        }

        val inMemoryModel = InMemoryModel.load(treePointer.tree as CLTree)
        val inMemoryNode = inMemoryModel.getArea().getRoot().allChildren.first()
        val actual = inMemoryNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        assertEquals(expected, actual)
    }
}

/*
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
package org.modelix.modelql.untyped

import kotlinx.coroutines.test.runTest
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.async.LegacyKeyValueStoreAsAsyncStore
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.persistent.MapBaseStore
import kotlin.test.Test
import kotlin.test.assertEquals

class UntypedModelQLTest {
    private val tree = CLTree.builder(LegacyKeyValueStoreAsAsyncStore(MapBaseStore())).build()
    private val branch = TreePointer(tree, IdGenerator.getInstance(1))
    private val rootNode = branch.getRootNode()

    @Test
    fun nodeCanBeResolvedInQuery() = runTest {
        val resolvedNode = rootNode.query { root ->
            root.nodeReference().resolve()
        }
        assertEquals(rootNode.reference, resolvedNode.reference)
    }
}

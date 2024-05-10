import org.modelix.model.api.INode
import org.modelix.model.api.NullChildLink
import org.modelix.model.api.PBranch
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.PrefetchCache
import org.modelix.model.persistent.MapBasedStore
import kotlin.test.Test

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

class LazyLoadingPrefetchTest {

    @Test
    fun data_is_prefetched() {

        val store = MapBasedStore()
        run {
            val objectStore = ObjectStoreCache(store, 50)
            val initialTree = CLTree.builder(objectStore).build()
            val branch = PBranch(initialTree, IdGenerator.getInstance(100))
            branch.runWrite {
                fun createNodes(parentNode: INode, numberOfNodes: Int) {
                    if (numberOfNodes == 0) return
                    if (numberOfNodes == 1) {
                        parentNode.addNewChild(NullChildLink, 0)
                        return
                    }
                    val subtreeSize1 = numberOfNodes / 2
                    val subtreeSize2 = numberOfNodes - subtreeSize1
                    createNodes(parentNode.addNewChild(NullChildLink, 0), subtreeSize1 - 1)
                    createNodes(parentNode.addNewChild(NullChildLink, 1), subtreeSize2 - 1)
                }

                createNodes(branch.getRootNode(), 5_000)
            }
        }


    }

}
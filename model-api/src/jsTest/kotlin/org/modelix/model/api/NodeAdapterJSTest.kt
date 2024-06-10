/*
 * Copyright (c) 2023-2024.
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
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeAdapterJSTest {
    @Test
    fun nodesCanBeRemoved() {
        val branch = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())
        val node = branch.computeWrite {
            branch.getRootNode().addNewChild("roleOfTheChildThatGetsRemoved")
        }
        val jsNode = NodeAdapterJS(node)

        jsNode.remove()

        branch.computeRead {
            assertEquals(0, branch.getRootNode().allChildren.count())
        }
    }
}

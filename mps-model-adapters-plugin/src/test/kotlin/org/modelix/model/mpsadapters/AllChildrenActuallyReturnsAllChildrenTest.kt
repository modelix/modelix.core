package org.modelix.model.mpsadapters

import org.modelix.model.api.INode

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

class AllChildrenActuallyReturnsAllChildrenTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test repository adapter consistency`() {
        readAction {
            checkAdapterConsistence(MPSRepositoryAsNode(mpsProject.repository))
        }
    }

    fun `test module adapter consistency`() {
        readAction {
            for (module in mpsProject.repository.modules.map { MPSModuleAsNode(it) }) {
                checkAdapterConsistence(module)
            }
        }
    }

    fun `test model adapter consistency`() {
        readAction {
            for (model in mpsProject.repository.modules.flatMap { it.models }.map { MPSModelAsNode(it) }) {
                checkAdapterConsistence(model)
            }
        }
    }

    private fun checkAdapterConsistence(adapter: INode) {
        val concept = checkNotNull(adapter.concept)
        val expected = concept.getAllChildLinks().flatMap { adapter.getChildren(it) }.toSet()
        val actual = adapter.allChildren.toSet()
        assertEquals(expected, actual)
    }
}

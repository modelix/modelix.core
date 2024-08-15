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

package org.modelix.model.mpsadapters

import org.modelix.model.api.INode
import org.modelix.model.api.NodeReference

class MPSModuleAsNodeTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test resolve language dependency from reference`() {
        val repositoryNode: INode = MPSRepositoryAsNode(mpsProject.repository)
        val languageDependencyNodeReference = NodeReference(
            "mps-lang:f3061a53-9226-4cc5-a443-f952ceaf5816#IN#mps-module:6517ba0d-f632-49c5-a166-401587c2c3ca(Solution1)",
        )

        val resolvedLanguageDependency = readAction {
            repositoryNode.getArea().resolveNode(languageDependencyNodeReference)
        }

        assertNotNull(resolvedLanguageDependency)
        assertEquals(languageDependencyNodeReference.serialize(), resolvedLanguageDependency!!.reference.serialize())
    }
}

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

package org.modelix.model.metameta

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import kotlin.test.Test
import kotlin.test.assertEquals

class MetaModelBranchTest {

    @Test
    fun conceptReferenceIsResolvedInMetaModel() {
        // Arrange
        val modelConcept = BuiltinLanguages.MPSRepositoryConcepts.Model
        val store = MapBaseStore()
        val storeCache = ObjectStoreCache(store)
        val idGenerator = IdGenerator.newInstance(1)
        val emptyTree = CLTree(storeCache)

        val rawBranch = PBranch(emptyTree, idGenerator)
        val metaModelBranch = MetaModelBranch(rawBranch)
        metaModelBranch.disabled = false
        metaModelBranch.runWrite {
            metaModelBranch.getRootNode().addNewChild("aChild", -1, modelConcept)
        }

        // Act
        var resolvedConceptReferenceInRead: IConceptReference? = null
        metaModelBranch.runRead {
            val onlyChild = metaModelBranch
                .getRootNode()
                .getChildren("aChild")
                .single()
            resolvedConceptReferenceInRead = onlyChild.getConceptReference()
        }
        var resolvedConceptReferenceInWrite: IConceptReference? = null
        metaModelBranch.runWriteT {
            val onlyChildId = it.getChildren(ITree.ROOT_ID, "aChild").single()
            resolvedConceptReferenceInWrite = it.getConceptReference(onlyChildId)
        }

        // Assert
        assertEquals(modelConcept.getReference(), resolvedConceptReferenceInRead)
        assertEquals(modelConcept.getReference(), resolvedConceptReferenceInWrite)
    }
}

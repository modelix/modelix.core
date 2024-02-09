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

package org.modelix.model.sync.bulk

import org.junit.jupiter.api.Test
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.PBranch
import org.modelix.model.api.SimpleChildLink
import org.modelix.model.api.SimpleConcept
import org.modelix.model.api.SimpleLanguage
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.withAutoTransactions
import kotlin.test.assertEquals

class ModelImporterUnorderedChildrenTest {
    object TestLanguage : SimpleLanguage("TestLanguage", uid = "testLanguage") {

        override var includedConcepts = arrayOf<SimpleConcept>(AConcept)

        object AConcept : SimpleConcept(
            conceptName = "aConcept",
            uid = "uid1",
        ) {
            init {
                addConcept(this)
                SimpleChildLink(
                    simpleName = "aChildLink1",
                    isMultiple = true,
                    isOptional = true,
                    isUnordered = true,
                    targetConcept = AConcept,
                    uid = "uid2",
                ).also(this::addChildLink)
                SimpleChildLink(
                    simpleName = "aChildLink2",
                    isMultiple = true,
                    isOptional = true,
                    isUnordered = true,
                    targetConcept = AConcept,
                    uid = "uid3",
                ).also(this::addChildLink)
            }
        }
    }

    init {
        TestLanguage.register()
    }

    private fun loadModelIntoBranch(modelData: ModelData): IBranch {
        val store = ObjectStoreCache(MapBasedStore())
        val tree = CLTree(
            data = null,
            repositoryId_ = null,
            store_ = store,
            useRoleIds = true,
        )
        val idGenerator = IdGenerator.getInstance(1)
        val pBranch = PBranch(tree, idGenerator)

        pBranch.runWrite {
            modelData.load(pBranch)
        }

        return pBranch.withAutoTransactions()
    }

    @Test
    fun `does not reorder children`() {
        // language=json
        val dataBeforeImportJson = """
        {
          "root": {
            "children": [
              {
                "concept": "uid1",
                "children": [
                  {
                    "id": "child1",
                    "concept": "uid1",
                    "role": "uid2"
                  },
                  {
                    "id": "child2",
                    "concept": "uid1",
                    "role": "uid2"
                  },
                  {
                    "id": "child3",
                    "concept": "uid1",
                    "role": "uid2"
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()
        val dataBeforeImport = ModelData.fromJson(dataBeforeImportJson)
        val branch = loadModelIntoBranch(dataBeforeImport)
        val modelImporter = ModelImporter(branch.getRootNode())

        // language=json
        val dataToImportJson = """
        {
          "root": {
            "children": [
              {
                "concept": "uid1",
                "children": [
                  {
                    "id": "child2",
                    "concept": "uid1",
                    "role": "uid2"
                  },
                  {
                    "id": "child4",
                    "concept": "uid1",
                    "role": "uid2"
                  },
                  {
                    "id": "child1",
                    "concept": "uid1",
                    "role": "uid2"
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()
        val dataToImport = ModelData.fromJson(dataToImportJson)
        modelImporter.import(dataToImport)

        val children = branch.getRootNode().allChildren.first().allChildren.toList()
        // child1 and child 2 should not be reordered
        // child3 is removed
        // child4 is added at the end
        assertEquals(listOf("child1", "child2", "child4"), children.map(INode::originalId))
    }

    @Test
    fun `does not reorder children but change role`() {
        // language=json
        val dataBeforeImportJson = """
        {
          "root": {
            "children": [
              {
                "concept": "uid1",
                "children": [
                  {
                    "id": "child1",
                    "concept": "uid1",
                    "role": "uid2"
                  },
                  {
                    "id": "child2",
                    "concept": "uid1",
                    "role": "uid2"
                  },
                  {
                    "id": "child3",
                    "concept": "uid1",
                    "role": "uid2"
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()
        val dataBeforeImport = ModelData.fromJson(dataBeforeImportJson)
        val branch = loadModelIntoBranch(dataBeforeImport)
        val modelImporter = ModelImporter(branch.getRootNode())

        // language=json
        val dataToImportJson = """
        {
          "root": {
            "children": [
              {
                "concept": "uid1",
                "children": [
                  {
                    "id": "child4",
                    "concept": "uid1",
                    "role": "uid2"
                  },
                  {
                    "id": "child2",
                    "concept": "uid1",
                    "role": "uid2"
                  },
                  {
                    "id": "child1",
                    "concept": "uid1",
                    "role": "uid3"
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()
        val dataToImport = ModelData.fromJson(dataToImportJson)
        modelImporter.import(dataToImport)

        val children = branch.getRootNode().allChildren.first().allChildren.toList()
        // child4 is added after existing child2
        // child1 with the role "uid2" should be removed
        // child1 with the role "uid3" should be added at the end
        assertEquals(listOf("child2", "child4", "child1"), children.map(INode::originalId))
        assertEquals("uid3", children[2].getContainmentLink()!!.getUID())
    }
}

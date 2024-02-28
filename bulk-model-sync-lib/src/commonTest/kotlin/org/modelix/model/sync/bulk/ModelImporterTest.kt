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

package org.modelix.model.sync.bulk

import org.modelix.model.api.IBranch
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.NodeData.Companion.ID_PROPERTY_KEY
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.operations.AddNewChildOp
import org.modelix.model.operations.AddNewChildrenOp
import org.modelix.model.operations.DeleteNodeOp
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.MoveNodeOp
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.SetPropertyOp
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.test.RandomModelChangeGenerator
import kotlin.js.JsName
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelImporterTest {

    private fun createOTBranchFromModel(model: ModelData): OTBranch {
        val pBranch = PBranch(CLTree(ObjectStoreCache(MapBasedStore())), IdGenerator.getInstance(1))
        pBranch.runWrite {
            ModelImporter(pBranch.getRootNode()).import(model)
        }
        return OTBranch(pBranch, IdGenerator.getInstance(1), ObjectStoreCache(MapBasedStore()))
    }

    private fun IBranch.importIncrementally(model: ModelData) {
        runWrite {
            ModelImporter(getRootNode()).import(model)
        }
    }

    private fun OTBranch.getNumOfUsedOperationsByType() = getPendingChanges().first.numOpsByType()

    @Test
    @JsName("can_sync_properties")
    fun `can sync properties`() {
        // language=json
        val initialData = """
            {
              "root": {
                "id": "node:001",
                "properties": {
                  "rootProperty": "rootPropertyValue",
                  "rootPropertyToBeDeleted": "rootPropertyToBeDeletedValue"
                },
                "children": [
                  {
                    "id": "node:002",
                    "properties": {
                      "childProperty" : "childPropertyValue",
                      "toBeDeleted": "toBeDeletedValue",
                      "toBeUpdated": "toBeUpdated"
                    }
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        // language=json
        val expectedData = """
        {
          "root": {
            "id": "node:001",
            "properties": {
              "rootProperty": "rootPropertyValue",
              "newRootProperty": "newRootPropertyValue"
            },
            "children": [
              {
                "id": "node:002",
                "properties": {
                  "childProperty" : "childPropertyValue",
                  "newChildProperty": "newChildPropertyValue",
                  "toBeUpdated": "updated"
                }
              }
            ]
          }
        }
        """.trimIndent().let { ModelData.fromJson(it) }

        val branch = createOTBranchFromModel(initialData)
        branch.importIncrementally(expectedData)

        branch.runRead {
            val root = branch.getRootNode()
            assertNodePropertiesConformToSpec(expectedData.root, root)
            assertNodePropertiesConformToSpec(expectedData.root.children.first(), root.allChildren.first())
        }
        val usedOperations = branch.getPendingChanges().first
        assertEquals(5, usedOperations.numOpsByType()[SetPropertyOp::class])
        assertEquals(5, usedOperations.size)
    }

    @Test
    @JsName("can_sync_references")
    fun `can sync references`() {
        // language=json
        val initialData = """
            {
              "root": {
                "id": "node:001",
                "references": {
                  "childRefToBeUpdated": "node:002"
                },
                "children": [
                  {
                    "id": "node:002",
                    "references": {
                      "siblingToBeDeleted": "node:003"
                    }
                  },
                  {
                    "id": "node:003"
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        // language=json
        val expectedData = """
            {
              "root": {
                "id": "node:001",
                "references": {
                  "childRefToBeUpdated": "node:003",
                  "self": "node:001"
                },
                "children": [
                  {
                    "id": "node:002"
                  },
                  {
                    "id": "node:003",
                    "references": {
                      "sibling": "node:002"
                    }
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        val branch = createOTBranchFromModel(initialData)
        branch.importIncrementally(expectedData)

        branch.runRead {
            val root = branch.getRootNode()
            val node2 = root.allChildren.first()
            val node3 = root.allChildren.toList()[1]

            assertEquals(node3, root.getReferenceTarget(IReferenceLink.fromName("childRefToBeUpdated")))
            assertEquals(root, root.getReferenceTarget(IReferenceLink.fromName("self")))
            assertEquals(node2, node3.getReferenceTarget(IReferenceLink.fromName("sibling")))

            assertNodeReferencesConformToSpec(expectedData.root, root)
            assertNodePropertiesConformToSpec(expectedData.root.children[0], node2)
            assertNodePropertiesConformToSpec(expectedData.root.children[1], node3)
        }

        val expectedOperations: Map<KClass<out IOperation>, Int> = mapOf(
            SetReferenceOp::class to 4,
        )

        assertEquals(expectedOperations, branch.getNumOfUsedOperationsByType())
    }

    @Test
    @JsName("can_sync_children")
    fun `can sync children`() {
        // language=json
        val initialData = """
            {
              "root": {
                "id": "root",
                "children": [
                  {
                    "id": "parent0",
                    "children": [
                      {
                        "id": "child02"
                      },
                      {
                        "id": "child03"
                      },
                      {
                        "id": "childToBeMovedAcrossParents>"
                      },
                      {
                        "id": "child01"
                      },
                      {
                        "id": "child0ToBeRemoved"
                      },
                      {
                        "id": "child00"
                      },
                      {
                        "id": "child04"
                      }
                    ]
                  },
                  {
                    "id": "parent1",
                    "children": [
                      {
                        "id": "child11"
                      },
                      {
                        "id": "child12"
                      },
                      {
                        "id": "child10"
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        // language=json
        val expectedData = """
            {
              "root": {
                "id": "root",
                "children": [
                  {
                    "id": "parent0",
                    "children": [
                      {
                        "id": "child00"
                      },
                      {
                        "id": "newlyAddedChildParent1"
                      },
                      {
                        "id": "child01"
                      },
                      {
                        "id": "child02"
                      },
                      {
                        "id": "child03"
                      },
                      {
                        "id": "child04"
                      }
                    ]
                  },
                  {
                    "id": "parent1",
                    "children": [
                      {
                        "id": "child10"
                      },
                      {
                        "id": "newlyAddedChildParent2"
                      },
                      {
                        "id": "child11"
                      },
                      {
                        "id": "childToBeMovedAcrossParents>"
                      },
                      {
                        "id": "child12"
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        val branch = createOTBranchFromModel(initialData)
        branch.importIncrementally(expectedData)

        branch.runRead {
            val root = branch.getRootNode()
            val parent0 = root.allChildren.first()
            val parent1 = root.allChildren.toList()[1]

            assertNodeChildOrderConformsToSpec(expectedData.root, root)
            assertNodeChildOrderConformsToSpec(expectedData.root.children[0], parent0)
            assertNodeChildOrderConformsToSpec(expectedData.root.children[1], parent1)
        }

        val expectedOperations: Map<KClass<out IOperation>, Int> = mapOf(
            MoveNodeOp::class to 5,
            AddNewChildOp::class to 2,
            SetPropertyOp::class to 2, // original ids for new nodes
            DeleteNodeOp::class to 1,
        )
        assertEquals(expectedOperations, branch.getNumOfUsedOperationsByType())
    }

    @Test
    @JsName("can_import_complex_model")
    fun `can import complex model`() {
        // legacy test case
        // language=json
        val initialData = """
           {
             "root": {
               "id": "node:001",
               "children": [
                 {
                   "id": "node:002",
                   "properties": {
                     "purpose" : "property sync test",
                     "name": "nameValue",
                     "description": "descriptionValue",
                     "optional" : "toBeDeleted"
                   }
                 },
                 {
                   "id": "node:003",
                   "properties": {
                     "purpose": "reference sync test"
                   },
                   "references": {
                     "root": "node:001",
                     "self": "node:003"
                   }
                 },
                 {
                   "id": "node:010",
                   "properties": {
                     "purpose": "child sync test"
                   },
                   "children": [
                     {
                       "id": "node:011"
                     },
                     {
                       "id": "node:012"
                     },
                     {
                       "id": "node:013"
                     },
                     {
                       "id": "node:014"
                     },
                     {
                       "id": "node:015"
                     },
                     {
                       "id": "node:004",
                       "properties": {
                         "purpose": "move across parents"
                       }
                     }
                   ]
                 },
                 {
                   "id": "node:005",
                   "properties": {
                     "purpose": "move across parents"
                   }
                 },
                 {
                   "id": "node:006",
                   "role": "roleBefore",
                   "properties": {
                     "purpose": "change role"
                   }
                 }
               ]
             }
           }

        """.trimIndent().let { ModelData.fromJson(it) }

        // language=json
        val expectedData = """
            {
              "root": {
                "id": "node:001",
                "children": [
                  {
                    "id": "node:002",
                    "properties": {
                      "purpose" : "property sync test",
                      "name": "newNameValue",
                      "description": "newDescriptionValue",
                      "newProperty": "newProperty"
                    },
                    "references": {
                      "sibling": "node:003"
                    }
                  },
                  {
                    "id": "node:003",
                    "properties": {
                      "purpose": "reference sync test"
                    },
                    "references": {
                      "root": "node:001",
                      "sibling": "node:002"
                    }
                  },
                  {
                    "id": "node:010",
                    "properties": {
                      "purpose": "child sync test"
                    },
                    "children": [
                      {
                        "id": "node:011"
                      },
                      {
                        "id": "node:013"
                      },
                      {
                        "id": "node:015"
                      },
                      {
                        "id": "node:014"
                      },
                      {
                        "id": "node:016"
                      },
                      {
                        "id": "node:005",
                        "properties": {
                          "purpose": "move across parents"
                        }
                      }
                    ]
                  },
                  {
                    "id": "node:004",
                    "properties": {
                      "purpose": "move across parents"
                    }
                  },
                  {
                    "id": "node:006",
                    "role": "roleAfter",
                    "properties": {
                      "purpose": "change role"
                    }
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        val branch = createOTBranchFromModel(initialData)
        branch.importIncrementally(expectedData)
        branch.runRead {
            assertAllNodesConformToSpec(expectedData.root, branch.getRootNode())
        }

        val expectedOperations: Map<KClass<out IOperation>, Int> = mapOf(
            SetPropertyOp::class to 5,
            SetReferenceOp::class to 3,
            MoveNodeOp::class to 6, // could be done in 5, but finding that optimization makes the sync algorithm slower
            AddNewChildOp::class to 1,
            AddNewChildrenOp::class to 1,
            DeleteNodeOp::class to 1,
        )

        assertEquals(expectedOperations, branch.getNumOfUsedOperationsByType())
        assertNoOverlappingOperations(branch.getPendingChanges().first)
    }

    @Test
    @JsName("can_handle_random_changes")
    fun `can handle random changes`() {
        for (seed in (1..100)) {
            runRandomTest(seed + 6787456)
        }
    }

    private fun runRandomTest(seed: Int) {
        val tree0 = CLTree(ObjectStoreCache(MapBasedStore()))
        val branch0 = PBranch(tree0, IdGenerator.getInstance(1))

        println("Seed for random change test: $seed")
        val rand = Random(seed)
        lateinit var initialState: NodeData
        lateinit var specification: NodeData
        val numChanges = 50

        branch0.runWrite {
            val rootNode = branch0.getRootNode()
            rootNode.setPropertyValue(IProperty.fromName(ID_PROPERTY_KEY), rootNode.reference.serialize())
            val grower = RandomModelChangeGenerator(rootNode, rand).growingOperationsOnly()
            for (i in 1..100) {
                grower.applyRandomChange()
            }
            initialState = rootNode.asExported()

            val changer = RandomModelChangeGenerator(rootNode, rand)
            for (i in 1..numChanges) {
                changer.applyRandomChange()
            }
            specification = rootNode.asExported()
        }

        val store = ObjectStoreCache(MapBasedStore())
        val tree1 = CLTree(store)
        val idGenerator = IdGenerator.getInstance(1)
        val branch1 = PBranch(tree1, idGenerator)

        branch1.runWrite {
            val importer = ModelImporter(branch1.getRootNode())
            importer.import(ModelData(root = initialState))
        }
        val otBranch = OTBranch(branch1, idGenerator, store)

        otBranch.runWrite {
            val importer = ModelImporter(otBranch.getRootNode())
            importer.import(ModelData(root = specification))

            assertAllNodesConformToSpec(specification, otBranch.getRootNode())
        }
        val operations = otBranch.getPendingChanges().first
        assertNoOverlappingOperations(operations)

        val numSetOriginalIdOps = specification.countNodes()
        val expectedNumOps = numChanges + numSetOriginalIdOps

        assertTrue("expected operations: <= $expectedNumOps, actual: ${operations.size}") {
            operations.size <= expectedNumOps
        }
    }
}

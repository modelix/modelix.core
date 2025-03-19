package org.modelix.model.sync.bulk

import org.modelix.model.api.IBranch
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.operations.AddNewChildOp
import org.modelix.model.operations.DeleteNodeOp
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.MoveNodeOp
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.SetPropertyOp
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.persistent.MapBasedStore
import kotlin.js.JsName
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class AbstractModelSyncTest {

    abstract fun runTest(initialData: ModelData, expectedData: ModelData, assertions: OTBranch.() -> Unit)
    abstract fun runRandomTest(seed: Int)

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

        runTest(initialData, expectedData) {
            val root = getRootNode()
            assertNodePropertiesConformToSpec(expectedData.root, root)
            assertNodePropertiesConformToSpec(expectedData.root.children.first(), root.allChildren.first())

            val usedOperations = getPendingChanges().first
            assertEquals(5, usedOperations.numOpsByType()[SetPropertyOp::class])
            assertEquals(5, usedOperations.size)
        }
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

        runTest(initialData, expectedData) {
            val root = getRootNode()
            val node2 = root.allChildren.first()
            val node3 = root.allChildren.toList()[1]

            assertEquals(node3, root.getReferenceTarget(IReferenceLink.fromName("childRefToBeUpdated")))
            assertEquals(root, root.getReferenceTarget(IReferenceLink.fromName("self")))
            assertEquals(node2, node3.getReferenceTarget(IReferenceLink.fromName("sibling")))

            assertNodeReferencesConformToSpec(expectedData.root, root)
            assertNodePropertiesConformToSpec(expectedData.root.children[0], node2)
            assertNodePropertiesConformToSpec(expectedData.root.children[1], node3)

            val expectedOperations: Map<KClass<out IOperation>, Int> = mapOf(
                SetReferenceOp::class to 4,
            )

            assertEquals(expectedOperations, getNumOfUsedOperationsByType())
        }
    }

    @Test
    @JsName("can_update_previously_unresolvable_references")
    fun `can update previously unresolvable reference`() {
        // language=json
        val initialData = """
            {
              "root": {
                "id": "root",
                "children": [
                  {
                    "id": "node:001",
                    "references": {
                      "myRef": "node:002",
                      "myRef2": "node:unresolvable"
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
                "id": "root",
                "children": [
                  {
                    "id": "node:001",
                    "references": {
                      "myRef": "node:002",
                      "myRef2": "node:unresolvable"
                    }
                  },
                  {
                    "id": "node:002",
                    "properties": {
                      "name": "PreviouslyUnresolvableNode"
                    }
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        val expectedOperations: Map<KClass<out IOperation>, Int> = mapOf(
            SetReferenceOp::class to 1,
            AddNewChildOp::class to 1,
            SetPropertyOp::class to 2, // name and originalRef of new node
        )

        runTest(initialData, expectedData) {
            val root = getRootNode()
            val sourceNode = root.allChildren.first()
            val referenceLink = IReferenceLink.fromName("myRef")

            val targetReference = sourceNode.getReferenceTargetRef(referenceLink)
            val targetNode = sourceNode.getReferenceTarget(referenceLink)

            assertNotNull(targetNode)
            assertTrue { targetReference is PNodeReference }
            assertAllNodesConformToSpec(expectedData.root, root)
            assertEquals(expectedOperations, getNumOfUsedOperationsByType())
        }
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

        runTest(initialData, expectedData) {
            val root = getRootNode()
            val parent0 = root.allChildren.first()
            val parent1 = root.allChildren.toList()[1]

            assertNodeChildOrderConformsToSpec(expectedData.root, root)
            assertNodeChildOrderConformsToSpec(expectedData.root.children[0], parent0)
            assertNodeChildOrderConformsToSpec(expectedData.root.children[1], parent1)

            val expectedOperations: Map<KClass<out IOperation>, Int> = mapOf(
                MoveNodeOp::class to 6,
                AddNewChildOp::class to 2,
                SetPropertyOp::class to 2, // original ids for new nodes
                DeleteNodeOp::class to 1,
            )
            assertEquals(expectedOperations, getNumOfUsedOperationsByType())
        }
    }

    @Test
    @JsName("can_handle_concept_changes")
    fun `can handle concept changes`() {
        // language=json
        val initialData = """
            {
              "root": {
                "id": "node:001",
                "children": [
                  {
                    "id": "node:002",
                    "concept": "ref:MyOldConcept",
                    "children": [
                      {
                        "id": "child02",
                        "references": {
                          "myReference": "node:003"
                        },
                        "properties": {
                          "myProperty": "myPropertyValue"
                        }
                      }
                    ]
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
                "children": [
                  {
                    "id": "node:002",
                    "concept": "ref:MyNewConcept",
                    "children": [
                      {
                        "id": "child02",
                        "references": {
                          "myReference": "node:003"
                        },
                        "properties": {
                          "myProperty": "myPropertyValue"
                        }
                      }
                    ]
                  },
                  {
                    "id": "node:003"
                  }
                ]
              }
            }
        """.trimIndent().let { ModelData.fromJson(it) }

        runTest(initialData, expectedData) {
            val changedNode = getRootNode().allChildren.first()
            assertEquals("ref:MyNewConcept", changedNode.getConceptReference().toString())
        }
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

        runTest(initialData, expectedData) {
            assertAllNodesConformToSpec(expectedData.root, getRootNode())

            val expectedOperations: Map<KClass<out IOperation>, Int> = mapOf(
                SetPropertyOp::class to 5,
                SetReferenceOp::class to 3,
                MoveNodeOp::class to 4,
                AddNewChildOp::class to 1,
                DeleteNodeOp::class to 1,
            )

            val operations = getPendingChanges().first
            assertEquals(expectedOperations, operations.numOpsByType())
            assertNoOverlappingOperations(operations)
        }
    }

    @Test
    @JsName("can_handle_random_changes")
    fun `can handle random changes`() {
        for (seed in (1..100)) {
            runRandomTest(seed + 6787456)
        }
    }
}

internal fun OTBranch.getNumOfUsedOperationsByType() = getPendingChanges().first.numOpsByType()

internal fun createOTBranchFromModel(model: ModelData): OTBranch {
    val pBranch = PBranch(CLTree(createObjectStoreCache(MapBasedStore())), IdGenerator.getInstance(1))
    pBranch.runWrite {
        ModelImporter(pBranch.getRootNode()).import(model)
    }
    return OTBranch(pBranch, IdGenerator.getInstance(1))
}

internal fun IBranch.importIncrementally(model: ModelData) {
    runWrite {
        ModelImporter(getRootNode()).import(model)
    }
}

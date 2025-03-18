package org.modelix.model.sync.bulk

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
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.withAutoTransactions
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the parts of the import algorithm,
 * that checks whether is a child node is in an ordered or unordered role
 * and that handles the importing children with of unordered roles.
 */
class ModelImporterOrderPropertyTest {
    object TestLanguage : SimpleLanguage("TestLanguage", uid = "testLanguage") {

        override var includedConcepts = arrayOf<SimpleConcept>(AConcept)

        object AConcept : SimpleConcept(
            conceptName = "aConcept",
            uid = "uid1",
        ) {
            // The child links in this concept are special, because they are unordered.
            val childLink1 = SimpleChildLink(
                simpleName = "aChildLink1",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = AConcept,
                uid = "uid2",
            ).also(this::addChildLink)

            val childLink2 = SimpleChildLink(
                simpleName = "aChildLink2",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = AConcept,
                uid = "uid3",
            ).also(this::addChildLink)

            init {
                addConcept(this)
            }
        }
    }

    init {
        TestLanguage.register()
    }

    private fun loadModelIntoBranch(modelData: ModelData): IBranch {
        val store = createObjectStoreCache(MapBasedStore())
        val tree = CLTree.builder(store).useRoleIds(true).build()
        val idGenerator = IdGenerator.getInstance(1)
        val pBranch = PBranch(tree, idGenerator)

        pBranch.runWrite {
            modelData.load(pBranch)
        }

        return pBranch.withAutoTransactions()
    }

    @Test
    @JsName("nodes_in_unordered_child_link_are_added_and_removed")
    fun `nodes in unordered child link are added and removed`() {
        // language=json
        val dataBeforeImportJson = """
        {
          "root": {
            "id": "root1",
            "children": [
              {
                "id": "parent1",
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
            "id": "root1",
            "children": [
              {
                "id": "parent1",
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

        val childIds = branch.getRootNode().allChildren.single()
            .getChildren(TestLanguage.AConcept.childLink1).toList()
            .map(INode::getOriginalReference).toSet()
        // child3 is removed
        // child4 is added
        assertEquals(setOf("child1", "child2", "child4"), childIds)
    }

    @Test
    @JsName("nodes_in_unordered_child_link_can_change_child_link_inside_same_parent")
    fun `nodes in unordered child link can change child link inside same parent`() {
        // language=json
        val dataBeforeImportJson = """
        {
          "root": {
            "children": [
              {
                "id": "parent1",
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
                "id": "parent1",
                "concept": "uid1",
                "children": [
                  {
                    "id": "child1",
                    "concept": "uid1",
                    "role": "uid3"
                  },
                  {
                    "id": "child2",
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

        val parent = branch.getRootNode().allChildren.single()
        val childLink1NodeIds = parent.getChildren(TestLanguage.AConcept.childLink1).toList()
            .map(INode::getOriginalReference).toSet()
        val childLink2NodesIds = parent.getChildren(TestLanguage.AConcept.childLink2).toList()
            .map(INode::getOriginalReference).toSet()
        assertEquals(setOf("child2"), childLink1NodeIds)
        // child1 moved from childLink1Nodes to childLink2Nodes
        assertEquals(setOf("child1"), childLink2NodesIds)
    }

    @Test
    @JsName("nodes_in_unordered_child_link_can_change_parent")
    fun `nodes in unordered child link can change parent`() {
        // language=json
        val dataBeforeImportJson = """
        {
          "root": {
            "children": [
              {
                "id": "parent1",
                "concept": "uid1",
                "children": [
                  {
                    "id": "child2",
                    "concept": "uid1",
                    "role": "uid2"
                  }
                ]
              },
              {
                "id": "parent2",
                "concept": "uid1",
                "children": [
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
        val dataBeforeImport = ModelData.fromJson(dataBeforeImportJson)
        val branch = loadModelIntoBranch(dataBeforeImport)
        val modelImporter = ModelImporter(branch.getRootNode())

        // language=json
        val dataToImportJson = """
        {
          "root": {
            "children": [
              {
                "id": "parent1",
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
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()
        val dataToImport = ModelData.fromJson(dataToImportJson)
        modelImporter.import(dataToImport)

        val childNodeIds = branch.getRootNode().allChildren.single()
            .getChildren(TestLanguage.AConcept.childLink1).toList()
            .map(INode::getOriginalReference).toSet()
        // child1 and child2 have now in the same parent
        assertEquals(setOf("child1", "child2"), childNodeIds)
    }

    @Test
    @JsName("children_without_resolvable_role_are_ordered")
    fun `children without resolvable role are ordered`() {
        // language=json
        val dataBeforeImportJson = """
        {
          "root": {
            "children": [
              {
                "id": "child1",
                "role": "unknownRole"
              },
              {
                "id": "child2",
                "role": "unknownRole"
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
                "id": "child2",
                "role": "unknownRole"
              },
              {
                "id": "child1",
                "role": "unknownRole"
              }
            ]
          }
        }
        """.trimIndent()
        val dataToImport = ModelData.fromJson(dataToImportJson)
        modelImporter.import(dataToImport)

        val childIdsOrdered = branch.getRootNode().getChildren("unknownRole").toList()
            .map(INode::getOriginalReference)

        // For child1 and child 2 their role cannot be resolved.
        // Therefore, they are reordered.
        assertEquals(listOf("child2", "child1"), childIdsOrdered)
    }
}

package org.modelix.model.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import java.io.File

class ModelImporterTest {

    companion object {
        private lateinit var model: ModelData
        private lateinit var newModel: ModelData
        private lateinit var branch: IBranch

        @JvmStatic
        @BeforeAll
        fun `load and import model`() {
            model = ModelData.fromJson(File("src/test/resources/model.json").readText())
            val newModelFile = File("src/test/resources/newmodel.json")
            newModel = ModelData.fromJson(newModelFile.readText())

            val tree = CLTree(ObjectStoreCache(MapBaseStore()))
            branch = PBranch(tree, IdGenerator.getInstance(1))

            branch.runWrite {
                model.load(branch)
//                println("PRE-SPEC ${model.toJson()}")
//                println("PRE-LOADED ${branch.getRootNode().toJson()}")
                ModelImporter(branch).import(newModelFile)
//                println("POST-SPEC ${newModel.root.toJson()}")
//                println("POST-LOADED ${branch.getRootNode().toJson()}")
            }
        }
    }

    @Test
    fun `can sync properties`() {
        branch.runRead {
            val expectedNode = newModel.root.children[0]
            val expectedProperties = expectedNode.properties
            val actualProperties = branch.getRootNode().allChildren.first().asData().properties
            assertEquals(expectedProperties, actualProperties.filterKeys { it != NodeData.idPropertyKey })
            assertEquals(expectedNode.id, actualProperties[NodeData.idPropertyKey])
        }
    }

    @Test
    fun `can sync references`() {
        branch.runRead {
            val node0 = branch.getRootNode().allChildren.first()
            val node1 = branch.getRootNode().allChildren.toList()[1]

            assertEquals(node1, node0.getReferenceTarget("sibling"))
            assertEquals(node0, node1.getReferenceTarget("sibling"))
            assertEquals(branch.getRootNode(), node1.getReferenceTarget("root"))
        }
    }

    @Test
    fun `can sync children`() {
        branch.runRead {
            val index = 2
            val node = branch.getRootNode().allChildren.toList()[index]
            val specifiedOrder = newModel.root.children[index].children.map { it.properties[NodeData.idPropertyKey] ?: it.id }
            val actualOrder = node.allChildren.map { it.getPropertyValue(NodeData.idPropertyKey) }
            assertEquals(specifiedOrder, actualOrder)
        }
    }

    @Test
    fun `can bulk import`() {
        val tree = CLTree(ObjectStoreCache(MapBaseStore()))
        val tempBranch = PBranch(tree, IdGenerator.getInstance(1))
        tempBranch.runWrite {
            assertDoesNotThrow {
                ModelImporter(tempBranch).import(newModel)
            }
            val children = tempBranch.getRootNode().allChildren.toList()
            assertEquals(newModel.root.children.size, children.size)
            for ((expected, actual) in newModel.root.children zip children) {
                assertEquals(expected.children.size, actual.allChildren.toList().size)
            }
        }
    }
}
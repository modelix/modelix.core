package org.modelix.model.sync

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.api.serialize
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import org.modelix.model.test.RandomModelChangeGenerator
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.fail

class ModelImporterTest {

    companion object {
        private lateinit var model: ModelData
        private lateinit var newModel: ModelData
        private lateinit var branch: IBranch
        private lateinit var importer: ModelImporter

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
                importer = ModelImporter(branch.getRootNode(), ImportStats()).apply { import(newModelFile) }
//                println("POST-SPEC ${newModel.root.toJson()}")
//                println("POST-LOADED ${branch.getRootNode().toJson()}")
            }
        }
    }

    @Test
    fun `can sync properties`() {
        branch.runRead {
            val expectedNode = newModel.root.children[0]
            val actualNode = branch.getRootNode().allChildren.first()
            assertNodePropertiesConformToSpec(expectedNode, actualNode)
        }
    }

    @Test
    fun `can sync references`() {
        branch.runRead {
            val node0 = branch.getRootNode().allChildren.first()
            val node1 = branch.getRootNode().allChildren.toList()[1]
            val node1Spec = newModel.root.children[1]

            assertEquals(node1, node0.getReferenceTarget("sibling"))
            assertEquals(node0, node1.getReferenceTarget("sibling"))
            assertEquals(branch.getRootNode(), node1.getReferenceTarget("root"))

            assertNodeReferencesConformToSpec(node1Spec, node1)
        }
    }

    @Test
    fun `can sync children`() {
        branch.runRead {
            val index = 2
            val actualNode = branch.getRootNode().allChildren.toList()[index]
            val specifiedNode = newModel.root.children[index]
            assertNodeChildOrderConformsToSpec(specifiedNode, actualNode)
        }
    }

    @Test
    fun `model conforms to spec`() {
        branch.runRead {
            assertAllNodesConformToSpec(newModel.root, branch.getRootNode())
        }
    }

    @Test
    fun `uses minimal amount of operations`() {
        val stats = importer.stats ?: fail("No import stats found.")
        assertEquals(1, stats.additions.size)
        assertEquals(1, stats.deletions.size)
        assertEquals(5, stats.moves.size)
        assertEquals(4, stats.propertyChanges.size)
        assertEquals(3, stats.referenceChanges.size)
    }

    @Test
    fun `operations do not overlap`() {
        assertNoOverlappingOperations(importer.stats)
    }

    @Test
    fun `can bulk import`() {
        val tree = CLTree(ObjectStoreCache(MapBaseStore()))
        val tempBranch = PBranch(tree, IdGenerator.getInstance(1))
        tempBranch.runWrite {
            assertDoesNotThrow {
                ModelImporter(tempBranch.getRootNode()).import(newModel)
            }
            assertAllNodesConformToSpec(newModel.root, tempBranch.getRootNode())
        }
    }

    @Test
    fun `can handle random changes`() {
        val tree0 = CLTree(ObjectStoreCache(MapBaseStore()))
        val branch0 = PBranch(tree0, IdGenerator.getInstance(1))

        val seed = Random.nextInt()
        println("Seed for random change test: $seed")
        lateinit var initialState: NodeData
        lateinit var specification: NodeData
        val numChanges = 50

        branch0.runWrite {
            val rootNode = branch0.getRootNode()
            rootNode.setPropertyValue(NodeData.idPropertyKey, rootNode.reference.serialize())
            val grower = RandomModelChangeGenerator(rootNode, Random(seed)).growingOperationsOnly()
            for (i in 1..20) {
                grower.applyRandomChange()
            }
            initialState = rootNode.asExported()

            val changer = RandomModelChangeGenerator(rootNode, Random(seed))
            for (i in 1..numChanges) {
                changer.applyRandomChange()
            }
            specification = rootNode.asExported()
        }

        val tree1 = CLTree(ObjectStoreCache(MapBaseStore()))
        val branch1 = PBranch(tree1, IdGenerator.getInstance(1))

        branch1.runWrite {
            val importer = ModelImporter(branch1.getRootNode(), ImportStats())
            importer.import(ModelData(root = initialState))
            importer.import(ModelData(root = specification))

//            println("INITIAL")
//            println(initialState.toJson())
//
//            println("SPEC")
//            println(specification.toJson())
//
//            println("ACTUAL")
//            println(branch1.getRootNode().toJson())

            assertAllNodesConformToSpec(specification, branch1.getRootNode())
            assertNoOverlappingOperations(importer.stats)
            assert(importer.stats!!.getTotal() <= numChanges) {
                "expected operations: <= $numChanges, actual: ${importer.stats!!.getTotal()}"
            }
        }
    }
}
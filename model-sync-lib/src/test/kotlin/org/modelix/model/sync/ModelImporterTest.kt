package org.modelix.model.sync

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import java.io.File
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
            assertAllNodeConformToSpec(newModel.root, branch.getRootNode())
        }
    }

    @Test
    fun `uses minimal amount of operations`() {
        val stats = importer.stats ?: fail("No import stats found.")
        assertEquals(1, stats.additions.size)
        assertEquals(1, stats.deletions.size)
        assertEquals(4, stats.moves.size)
        assertEquals(4, stats.propertyChanges.size)
        assertEquals(3, stats.referenceChanges.size)
    }

    @Test
    fun `operations do not overlap`() {
        val stats = importer.stats ?: fail("No import stats found.")
        val additionsSet = stats.additions.toSet()
        val deletionsSet = stats.deletions.toSet()
        val movesSet = stats.moves.toSet()

        assert(additionsSet.intersect(deletionsSet).isEmpty())
        assert(deletionsSet.intersect(movesSet).isEmpty())
        assert(movesSet.intersect(additionsSet).isEmpty())
    }

    @Test
    fun `can bulk import`() {
        val tree = CLTree(ObjectStoreCache(MapBaseStore()))
        val tempBranch = PBranch(tree, IdGenerator.getInstance(1))
        tempBranch.runWrite {
            assertDoesNotThrow {
                ModelImporter(tempBranch.getRootNode()).import(newModel)
            }
            val children = tempBranch.getRootNode().allChildren.toList()
            assertEquals(newModel.root.children.size, children.size)
            for ((expected, actual) in newModel.root.children zip children) {
                assertEquals(expected.children.size, actual.allChildren.toList().size)
            }
        }
    }
}
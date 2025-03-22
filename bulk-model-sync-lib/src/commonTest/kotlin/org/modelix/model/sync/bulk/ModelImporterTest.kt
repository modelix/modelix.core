package org.modelix.model.sync.bulk

import org.modelix.model.api.IProperty
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.NodeData.Companion.ID_PROPERTY_KEY
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.test.RandomModelChangeGenerator
import kotlin.random.Random
import kotlin.test.assertTrue

class ModelImporterTest : AbstractModelSyncTest() {

    override fun runTest(initialData: ModelData, expectedData: ModelData, assertions: OTBranch.() -> Unit) {
        val branch = createOTBranchFromModel(initialData)
        branch.importIncrementally(expectedData)
        branch.runRead { branch.assertions() }
    }

    override fun runRandomTest(seed: Int) {
        val tree0 = CLTree(createObjectStoreCache(MapBasedStore()))
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

        val store = createObjectStoreCache(MapBasedStore())
        val tree1 = CLTree(store)
        val idGenerator = IdGenerator.getInstance(1)
        val branch1 = PBranch(tree1, idGenerator)

        branch1.runWrite {
            val importer = ModelImporter(branch1.getRootNode())
            importer.import(ModelData(root = initialState))
        }
        val otBranch = OTBranch(branch1, idGenerator)

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

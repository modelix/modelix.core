package org.modelix.model.sync.bulk

import org.modelix.model.api.IProperty
import org.modelix.model.api.PBranch
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData.Companion.ID_PROPERTY_KEY
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.test.RandomModelChangeGenerator
import kotlin.random.Random
import kotlin.test.assertTrue

class ModelSynchronizerWithInvalidationTreeTest : ModelSynchronizerTest() {

    override fun runTest(initialData: ModelData, expectedData: ModelData, assertions: OTBranch.() -> Unit) {
        val sourceBranch = createOTBranchFromModel(expectedData)
        val targetBranch = createOTBranchFromModel(initialData)

        val invalidationTree = InvalidationTree(1000)

        sourceBranch.runRead {
            val sourceRoot = sourceBranch.getRootNode()

            targetBranch.runWrite {
                val sourceTree = sourceBranch.transaction.tree
                val targetTree = targetBranch.transaction.tree
                sourceTree.visitChanges(targetTree, InvalidatingVisitor(sourceTree, invalidationTree))

                val targetRoot = targetBranch.getRootNode()
                val synchronizer = ModelSynchronizer(
                    filter = invalidationTree,
                    sourceRoot = sourceRoot.asReadableNode(),
                    targetRoot = targetRoot.asWritableNode(),
                    nodeAssociation = NodeAssociationToModelServer(targetBranch),
                )
                synchronizer.synchronize()
            }

            targetBranch.runRead { targetBranch.assertions() }
        }
    }

    override fun runRandomTest(seed: Int) {
        val tree0 = CLTree(createObjectStoreCache(MapBasedStore()))
        val sourceBranch = PBranch(tree0, IdGenerator.getInstance(1))

        println("Seed for random change test: $seed")
        val rand = Random(seed)
        val numChanges = 50

        sourceBranch.runWrite {
            val rootNode = sourceBranch.getRootNode()
            rootNode.setPropertyValue(IProperty.fromName(ID_PROPERTY_KEY), rootNode.reference.serialize())
            val grower = RandomModelChangeGenerator(rootNode, rand).growingOperationsOnly()
            repeat(100) {
                grower.applyRandomChange()
            }

            val changer = RandomModelChangeGenerator(rootNode, rand)
            repeat(numChanges) {
                changer.applyRandomChange()
            }
        }

        val store = createObjectStoreCache(MapBasedStore())
        val tree1 = CLTree(store)
        val idGenerator = IdGenerator.getInstance(1)
        val targetBranch = PBranch(tree1, idGenerator)
        val invalidationTree = InvalidationTree(1000)

        targetBranch.runWrite {
            sourceBranch.runRead {
                val importer = ModelImporter(targetBranch.getRootNode())
                importer.import(ModelData(root = sourceBranch.getRootNode().asExported()))
                val sourceTree = sourceBranch.transaction.tree
                val targetTree = targetBranch.transaction.tree

                sourceTree.visitChanges(targetTree, InvalidatingVisitor(sourceTree, invalidationTree))
            }
        }
        val otBranch = OTBranch(targetBranch, idGenerator)
        otBranch.runWrite {
            ModelSynchronizer(
                filter = invalidationTree,
                sourceRoot = sourceBranch.getRootNode().asReadableNode(),
                targetRoot = targetBranch.getRootNode().asWritableNode(),
                nodeAssociation = NodeAssociationToModelServer(targetBranch),
            )
        }
        val operations = otBranch.getPendingChanges().first
        assertNoOverlappingOperations(operations)

        val numSetOriginalIdOps = sourceBranch.computeRead { sourceBranch.getRootNode().getDescendants(true).count() }
        val expectedNumOps = numChanges + numSetOriginalIdOps

        assertTrue("expected operations: <= $expectedNumOps, actual: ${operations.size}") {
            operations.size <= expectedNumOps
        }

        otBranch.runRead {
            assertAllNodesConformToSpec(sourceBranch.computeRead { sourceBranch.getRootNode().asExported() }, otBranch.getRootNode())
        }
    }
}

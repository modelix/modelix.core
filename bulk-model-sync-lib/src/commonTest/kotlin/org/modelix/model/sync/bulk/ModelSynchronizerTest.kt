package org.modelix.model.sync.bulk

import org.modelix.model.ModelFacade
import org.modelix.model.api.IBranch
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.PBranch
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.operations.AddNewChildOp
import org.modelix.model.operations.AddNewChildrenOp
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.test.RandomModelChangeGenerator
import kotlin.js.JsName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class ModelSynchronizerTest : AbstractModelSyncTest() {

    @Test
    @JsName("can_handle_added_child_without_original_id_without_existing_sibling")
    fun `can handle added child without original id (without existing sibling)`() {
        val sourceBranch = createLocalBranch().apply {
            runWrite {
                getRootNode().asWritableNode().apply {
                    setPropertyValue(NodeData.ID_PROPERTY_REF, "root")
                    addNewChild(IChildLinkReference.fromName("test"), -1, NullConcept.getReference())
                }
            }
        }.toOTBranch()

        val targetBranch = createLocalBranch().apply {
            runWrite {
                getRootNode().asWritableNode().setPropertyValue(NodeData.ID_PROPERTY_REF, "root")
            }
        }.toOTBranch()

        runTest(sourceBranch, targetBranch) {
            assertEquals(1, targetBranch.getRootNode().allChildren.count())
            assertEquals(1, targetBranch.getNumOfUsedOperationsByType()[AddNewChildrenOp::class])
        }
    }

    @Test
    @JsName("can_handle_added_child_without_original_id_with_existing_sibling")
    fun `can handle added child without original id (with existing sibling)`() {
        val sourceBranch = createLocalBranch().apply {
            runWrite {
                getRootNode().asWritableNode().apply {
                    setPropertyValue(NodeData.ID_PROPERTY_REF, "root")
                    addNewChild(IChildLinkReference.fromName("test"), -1, NullConcept.getReference()).setPropertyValue(NodeData.ID_PROPERTY_REF, "sibling")
                    addNewChild(IChildLinkReference.fromName("test"), -1, NullConcept.getReference())
                }
            }
        }.toOTBranch()

        val targetBranch = createLocalBranch().apply {
            runWrite {
                getRootNode().asWritableNode().apply {
                    setPropertyValue(NodeData.ID_PROPERTY_REF, "root")
                    addNewChild(IChildLinkReference.fromName("test"), -1, NullConcept.getReference())
                        .setPropertyValue(NodeData.ID_PROPERTY_REF, "sibling")
                }
            }
        }.toOTBranch()

        runTest(sourceBranch, targetBranch) {
            assertEquals(2, targetBranch.getRootNode().allChildren.count())
            assertEquals(1, targetBranch.getNumOfUsedOperationsByType()[AddNewChildOp::class])
        }
    }

    private fun createLocalBranch() = ModelFacade.toLocalBranch(ModelFacade.newLocalTree())

    override fun runTest(initialData: ModelData, expectedData: ModelData, assertions: OTBranch.() -> Unit) {
        val sourceBranch = createOTBranchFromModel(expectedData)
        val targetBranch = createOTBranchFromModel(initialData)
        runTest(sourceBranch, targetBranch, assertions)
    }

    private fun runTest(sourceBranch: OTBranch, targetBranch: OTBranch, assertions: OTBranch.() -> Unit) {
        sourceBranch.runRead {
            val sourceRoot = sourceBranch.getRootNode()

            targetBranch.runWrite {
                val targetRoot = targetBranch.getRootNode()
                val synchronizer = ModelSynchronizer(
                    filter = BasicFilter,
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
        val tree0 = CLTree(ObjectStoreCache(MapBasedStore()))
        val sourceBranch = PBranch(tree0, IdGenerator.getInstance(1))

        println("Seed for random change test: $seed")
        val rand = Random(seed)
        val numChanges = 50

        sourceBranch.runWrite {
            val rootNode = sourceBranch.getRootNode()
            rootNode.asWritableNode().setPropertyValue(NodeData.ID_PROPERTY_REF, rootNode.reference.serialize())
            val grower = RandomModelChangeGenerator(rootNode, rand).growingOperationsOnly()
            for (i in 1..100) {
                grower.applyRandomChange()
            }

            val changer = RandomModelChangeGenerator(rootNode, rand)
            for (i in 1..numChanges) {
                changer.applyRandomChange()
            }
        }

        val store = ObjectStoreCache(MapBasedStore())
        val tree1 = CLTree(store)
        val idGenerator = IdGenerator.getInstance(1)
        val targetBranch = PBranch(tree1, idGenerator)

        targetBranch.runWrite {
            sourceBranch.runRead {
                val importer = ModelImporter(targetBranch.getRootNode())
                importer.import(ModelData(root = sourceBranch.getRootNode().asExported()))
            }
        }
        val otBranch = OTBranch(targetBranch, idGenerator, store)

        otBranch.runWrite {
            ModelSynchronizer(
                filter = BasicFilter,
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
    }

    object BasicFilter : ModelSynchronizer.IFilter {
        override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean {
            return true
        }

        override fun needsSynchronization(node: IReadableNode): Boolean {
            return true
        }
    }
}

private fun IBranch.toOTBranch(): OTBranch {
    return OTBranch(this, IdGenerator.getInstance(1), ObjectStoreCache(MapBasedStore()))
}

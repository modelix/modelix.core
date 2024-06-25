/*
 * Copyright (c) 2024.
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
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData.Companion.ID_PROPERTY_KEY
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.test.RandomModelChangeGenerator
import kotlin.random.Random
import kotlin.test.assertTrue

class ModelSynchronizerTest : AbstractModelSyncTest() {

    override fun runTest(initialData: ModelData, expectedData: ModelData, assertions: OTBranch.() -> Unit) {
        val sourceBranch = createOTBranchFromModel(expectedData)
        val targetBranch = createOTBranchFromModel(initialData)

        sourceBranch.runRead {
            val sourceRoot = sourceBranch.getRootNode()

            targetBranch.runWrite {
                val targetRoot = targetBranch.getRootNode()
                val synchronizer = ModelSynchronizer(
                    filter = BasicFilter,
                    sourceRoot = sourceRoot,
                    targetRoot = targetRoot,
                    nodeAssociation = BasicAssociation(targetBranch),
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
            rootNode.setPropertyValue(IProperty.fromName(ID_PROPERTY_KEY), rootNode.reference.serialize())
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
                sourceRoot = sourceBranch.getRootNode(),
                targetRoot = targetBranch.getRootNode(),
                nodeAssociation = BasicAssociation(targetBranch),
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
        override fun needsDescentIntoSubtree(subtreeRoot: INode): Boolean {
            return true
        }

        override fun needsSynchronization(node: INode): Boolean {
            return true
        }
    }

    class BasicAssociation(private val target: IBranch) : INodeAssociation {

        override fun resolveTarget(sourceNode: INode): INode? {
            require(sourceNode is PNodeAdapter)
            return target.computeRead {
                target.getRootNode().getDescendants(true).find { sourceNode.originalId() == it.originalId() }
            }
        }

        override fun associate(sourceNode: INode, targetNode: INode) {
            if (sourceNode.getOriginalReference() != targetNode.getOriginalReference()) {
                targetNode.setPropertyValue(IProperty.fromName(ID_PROPERTY_KEY), sourceNode.reference.serialize())
            }
        }
    }
}

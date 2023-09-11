/*
 * Copyright (c) 2023.
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

package org.modelix.mps.sync.binding

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import gnu.trove.map.hash.TObjectLongHashMap
import jetbrains.mps.smodel.adapter.MetaAdapterByDeclaration
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SInterfaceConcept
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.area.AbstractArea
import org.modelix.model.area.IAreaReference
import org.modelix.model.area.PArea
import org.modelix.model.mpsadapters.MPSNodeReference
import org.modelix.mps.sync.util.mappedMpsNodeID

class NodeMap(private val branchProvider: BranchProvider) : AbstractArea(), IAreaReference {

    // Map from Node to Cloud ID
    private val node2id = TObjectLongHashMap<SNode>()

    // Map from Cloud ID to Node
    private val id2node: TLongObjectMap<SNode> = TLongObjectHashMap()

    @Deprecated("use ILanguageRepository.resolveConcept", ReplaceWith("ILanguageRepository.resolveConcept"))
    override fun resolveConcept(ref: IConceptReference): IConcept? = null

    override fun getReference(): IAreaReference = this

    override fun getRoot(): INode = throw UnsupportedOperationException()

    override fun resolveOriginalNode(ref: INodeReference): INode? {
        if (ref is MPSNodeReference) {
            val targetNodeId = ref.ref.nodeId
            val sNode = node2id.keySet().stream().filter { sNode -> sNode.nodeId == targetNodeId }.findFirst()
            return if (sNode.isPresent) {
                // TODO fix SNode -> INode transformation. Problem SNodeToNodeAdapter.wrap does not exist anymore in modelix...
                // return SNodeToNodeAdapter.wrap(sNode.get());
                null
            } else {
                null
            }
        }
        return null
    }

    fun put(id: Long, node: SNode?) {
        id2node.put(id, node)
        node2id.put(node, id)
    }

    fun removeId(id: Long) {
        val node = id2node.remove(id)
        if (node != null) {
            node2id.remove(node)
        }
    }

    fun removeNode(node: SNode?) {
        val id = node2id.remove(node)
        id2node.remove(id)
    }

    fun getNode(id: Long): SNode? = id2node[id]

    fun getId(node: SNode?): Long = node2id.get(node)

    fun hasMappingForCloudNode(cloudID: Long): Boolean = id2node.containsKey(cloudID)

    fun getOrCreateNode(id: Long, conceptProducer: () -> Any): SNode {
        var node = getNode(id)
        if (node == null) {
            try {
                // The id parameters is the ID of the node in the replicated data structure.
                // We could use any ID for the MPS node, but for the load balancing to work properly,
                // node references should be resolvable independent of the MPS instance.
                val nodeId: SNodeId

                // We need to get the associated MPS Node ID, stored in the INode
                val branch = branchProvider.getBranch()!!
                val iNode = PNodeAdapter(id, branch)
                // We should probably store these values somewhere, to avoid transactions
                val mpsNodeIdAsString = if (branch.canRead()) {
                    iNode.mappedMpsNodeID()
                } else {
                    PArea(branch).executeRead { iNode.mappedMpsNodeID() }
                }

                nodeId = if (mpsNodeIdAsString == null) {
                    // Here, given we have no explicit NodeId in the INode, we use the id of the INode
                    // We may want to create a special SNodeId in this case?
                    jetbrains.mps.smodel.SNodeId.Regular(id)
                } else {
                    PersistenceFacade.getInstance().createNodeId(mpsNodeIdAsString)!!
                }
                val concept: SConcept = when (val rawConcept = conceptProducer.invoke()) {
                    is SInterfaceConcept -> {
                        MetaAdapterByDeclaration.asInstanceConcept((rawConcept as SAbstractConcept))
                    }

                    is SConcept -> {
                        rawConcept
                    }

                    else -> {
                        throw RuntimeException("Concept producer returned something unexpected: $rawConcept")
                    }
                }
                node = jetbrains.mps.smodel.SNode(concept, nodeId)
                put(id, node)
            } catch (ex: RuntimeException) {
                throw RuntimeException("Issue while trying to create node with id $id and concept $conceptProducer", ex)
            }
        }
        return node
    }

    fun interface BranchProvider {
        fun getBranch(): IBranch?
    }
}

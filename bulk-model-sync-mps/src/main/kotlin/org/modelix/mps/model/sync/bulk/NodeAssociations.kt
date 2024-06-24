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

package org.modelix.mps.model.sync.bulk

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import org.modelix.model.ModelIndex
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.data.NodeData
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.model.sync.bulk.INodeAssociation

internal class NodeAssociationToModelServer(val branch: IBranch) : INodeAssociation {

    private val modelIndex
        get() = ModelIndex.get(branch.transaction, NodeData.ID_PROPERTY_KEY)

    override fun resolveTarget(sourceNode: INode): INode? {
        return modelIndex.find(sourceNode.reference.serialize()).map { PNodeAdapter(it, branch) }.firstOrNull()
    }

    override fun associate(sourceNode: INode, targetNode: INode) {
        targetNode.setPropertyValue(IProperty.fromName(NodeData.ID_PROPERTY_KEY), sourceNode.reference.serialize())
    }
}

internal class NodeAssociationToMps(val mpsArea: MPSArea) : INodeAssociation {

    private val serverToMps: TLongObjectMap<INode> = TLongObjectHashMap()

    override fun resolveTarget(sourceNode: INode): INode? {
        require(sourceNode is PNodeAdapter)

        return serverToMps.get(sourceNode.nodeId)
            ?: sourceNode.getOriginalReference()
                ?.let { mpsArea.resolveNode(NodeReference(it)) }
    }

    override fun associate(sourceNode: INode, targetNode: INode) {
        require(sourceNode is PNodeAdapter)
        if (serverToMps.get(sourceNode.nodeId) != targetNode) {
            serverToMps.put(sourceNode.nodeId, targetNode)
        }
    }
}

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

class NodeAssociationFromMPS2Server(val isSynchronizingToMPS: Boolean, val mpsArea: MPSArea, val branch: IBranch) : INodeAssociation {
    private val server2mps: TLongObjectMap<INode> = TLongObjectHashMap()

    fun getIndex(): ModelIndex = ModelIndex.get(branch.transaction, NodeData.ID_PROPERTY_KEY)

    override fun resolveTarget(sourceNode: INode): INode? {
        return if (isSynchronizingToMPS) {
            sourceNode.getOriginalReference()?.let { mpsArea.resolveNode(NodeReference(it)) }
        } else {
            getIndex().find(sourceNode.reference.serialize()).map { PNodeAdapter(it, branch) }.firstOrNull()
        }
    }

    override fun associate(sourceNode: INode, targetNode: INode) {
        if (isSynchronizingToMPS) {
            // TODO This should be persisted, but the model server side is read only during the synchronization.
            server2mps.put((sourceNode as PNodeAdapter).nodeId, targetNode)
        } else {
            targetNode.setPropertyValue(IProperty.fromName(NodeData.ID_PROPERTY_KEY), sourceNode.reference.serialize())
        }
    }

    override fun associationMatches(sourceNode: INode, targetNode: INode): Boolean {
        return if (isSynchronizingToMPS) {
            sourceNode.getOriginalReference() == targetNode.reference.serialize()
        } else {
            sourceNode.reference.serialize() == targetNode.getOriginalReference()
        }
    }
}

private fun INode.originalOrActualReference() = getOriginalReference() ?: reference.serialize()
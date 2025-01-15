package org.modelix.mps.model.sync.bulk

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import org.modelix.model.ModelIndex
import org.modelix.model.api.IBranch
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getOriginalOrCurrentReference
import org.modelix.model.area.PArea
import org.modelix.model.data.NodeData
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.model.sync.bulk.INodeAssociation

internal class NodeAssociationToModelServer(val branch: IBranch) : INodeAssociation {

    private val modelIndex
        get() = ModelIndex.get(branch.transaction, NodeData.ID_PROPERTY_KEY)

    override fun resolveTarget(sourceNode: IReadableNode): IWritableNode? {
        val ref = sourceNode.getOriginalOrCurrentReference()
        return modelIndex.find(ref).map { PNodeAdapter(it, branch) }.firstOrNull()?.asWritableNode()
            ?: PArea(branch).resolveNode(NodeReference(ref))?.asWritableNode()
    }

    override fun associate(sourceNode: IReadableNode, targetNode: IWritableNode) {
        targetNode.setPropertyValue(NodeData.ID_PROPERTY_REF, sourceNode.getNodeReference().serialize())
    }
}

internal class NodeAssociationToMps(val mpsArea: MPSArea) : INodeAssociation {

    private val serverToMps: TLongObjectMap<IWritableNode> = TLongObjectHashMap()

    override fun resolveTarget(sourceNode: IReadableNode): IWritableNode? {
        val sourceNode = sourceNode.asLegacyNode()
        require(sourceNode is PNodeAdapter)

        return (
            serverToMps.get(sourceNode.nodeId)
                ?: sourceNode.getOriginalReference()?.let { mpsArea.resolveNode(NodeReference(it)) }?.asWritableNode()
            )
    }

    override fun associate(sourceNode: IReadableNode, targetNode: IWritableNode) {
        require(sourceNode is PNodeAdapter)
        if (serverToMps.get(sourceNode.nodeId) != targetNode) {
            serverToMps.put(sourceNode.nodeId, targetNode)
        }
    }
}

package org.modelix.model.sync.bulk

import org.modelix.model.ModelIndex
import org.modelix.model.api.IBranch
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getOriginalOrCurrentReference
import org.modelix.model.api.getOriginalReference
import org.modelix.model.area.PArea
import org.modelix.model.data.NodeData

class NodeAssociationToModelServer(val branch: IBranch) : INodeAssociation {

    private val modelIndex
        get() = ModelIndex.get(branch.transaction, NodeData.Companion.ID_PROPERTY_KEY)

    override fun resolveTarget(sourceNode: IReadableNode): IWritableNode? {
        val ref = sourceNode.getOriginalOrCurrentReference()
        return modelIndex.find(ref).map { PNodeAdapter(it, branch) }.firstOrNull()?.asWritableNode()
            ?: PArea(branch).resolveNode(NodeReference(ref))?.asWritableNode()
    }

    override fun associate(sourceNode: IReadableNode, targetNode: IWritableNode) {
        val expected = sourceNode.getOriginalOrCurrentReference()
        if (expected != targetNode.getOriginalOrCurrentReference()) {
            targetNode.setPropertyValue(NodeData.ID_PROPERTY_REF, expected)
        }
    }
}

class NodeAssociationFromModelServer(val branch: IBranch, val targetModel: IMutableModel) : INodeAssociation {
    private val pendingAssociations = HashMap<Long, String>()

    private fun nodeId(sourceNode: IReadableNode): Long {
        val pnode = sourceNode.asLegacyNode() as PNodeAdapter
        require(pnode.branch == branch) {
            "Node is from a different branch. [node = $sourceNode, expected: $branch, actual: ${pnode.branch}]"
        }
        return pnode.nodeId
    }

    override fun resolveTarget(sourceNode: IReadableNode): IWritableNode? {
        return (pendingAssociations[nodeId(sourceNode)] ?: sourceNode.getOriginalReference())
            ?.let { targetModel.resolveNode(NodeReference(it)) }
    }

    override fun associate(sourceNode: IReadableNode, targetNode: IWritableNode) {
        val id = nodeId(sourceNode)
        val expected = sourceNode.getOriginalOrCurrentReference()
        if (expected != targetNode.getOriginalOrCurrentReference()) {
            pendingAssociations[id] = targetNode.getNodeReference().serialize()
        }
    }

    fun writeAssociations() {
        for (entry in pendingAssociations) {
            branch.writeTransaction.setProperty(entry.key, NodeData.ID_PROPERTY_KEY, entry.value)
        }
        pendingAssociations.clear()
    }
}

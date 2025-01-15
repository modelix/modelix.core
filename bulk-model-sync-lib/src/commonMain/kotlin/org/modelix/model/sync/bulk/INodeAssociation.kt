package org.modelix.model.sync.bulk

import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getOriginalOrCurrentReference
import org.modelix.model.area.IArea
import org.modelix.model.data.NodeData
import org.modelix.model.data.NodeDataAsNode

/**
 * A node association is responsible for storing the mapping between a source node and the imported target node.
 * Provides efficient lookup of the mapping from previous synchronization runs.
 */
interface INodeAssociation {
    fun resolveTarget(sourceNode: IReadableNode): IWritableNode?
    fun associate(sourceNode: IReadableNode, targetNode: IWritableNode)
    fun matches(sourceNode: IReadableNode, targetNode: IWritableNode): Boolean {
        return sourceNode.getOriginalOrCurrentReference() == targetNode.getOriginalOrCurrentReference() ||
            resolveTarget(sourceNode) == targetNode
    }
}

class TransientNodeAssociation(val writeOriginalIds: Boolean, val targetModel: IArea) : INodeAssociation {
    private val associations: MutableMap<String, IWritableNode> by lazy {
        HashMap<String, IWritableNode>().also { map ->
            if (true) {
                targetModel.getRoot().getDescendants(true).forEach { node ->
                    node.getOriginalReference()?.let { ref ->
                        map[ref] = node.asWritableNode()
                    }
                }
            }
        }
    }
    override fun resolveTarget(sourceNode: IReadableNode): IWritableNode? {
        val ref = sourceNode.getOriginalOrCurrentReference()
        return associations[ref]
            ?: targetModel.resolveNode(NodeReference(ref))?.asWritableNode()
    }

    override fun associate(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ) {
        associations[sourceNode.getOriginalOrCurrentReference()] = targetNode
        if (writeOriginalIds) {
            if ((sourceNode as NodeDataAsNode).data.id == null) return
            val expected = sourceNode.getOriginalOrCurrentReference()
            if (targetNode.getOriginalOrCurrentReference() != expected) {
                targetNode.setPropertyValue(NodeData.ID_PROPERTY_REF, expected)
            }
        }
    }
}

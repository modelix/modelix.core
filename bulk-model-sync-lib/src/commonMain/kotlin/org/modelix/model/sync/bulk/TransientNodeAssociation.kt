package org.modelix.model.sync.bulk

import org.modelix.model.api.IMutableModel
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getOriginalOrCurrentReference
import org.modelix.model.api.getOriginalReference
import org.modelix.model.data.NodeData
import org.modelix.model.data.NodeDataAsNode

/**
 * Maintains the association in-memory.
 * This is the default implementation if the target model doesn't allow any optimizations.
 */
class TransientNodeAssociation(val writeOriginalIds: Boolean, val targetModel: IMutableModel) : INodeAssociation {
    private val associations: MutableMap<String, IWritableNode> by lazy {
        HashMap<String, IWritableNode>().also { map ->
            if (writeOriginalIds) {
                targetModel.getRootNode().getDescendants(true).forEach { node ->
                    node.getOriginalReference()?.let { ref ->
                        map[ref] = node
                    }
                }
            }
        }
    }

    override fun resolveTarget(sourceNode: IReadableNode): IWritableNode? {
        val ref = sourceNode.getOriginalOrCurrentReference()
        return associations[ref]
            ?: targetModel.tryResolveNode(NodeReference(ref))
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
                targetNode.setPropertyValue(NodeData.Companion.ID_PROPERTY_REF, expected)
            }
        }
    }
}

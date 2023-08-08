package org.modelix.model.sync

import org.modelix.model.api.INode
import org.modelix.model.data.NodeData
import org.modelix.model.data.associateWithNotNull

/**
 * A ModelExporter exports a node and its subtree in bulk.
 */
expect class ModelExporter(root: INode)

/**
 * Returns a [NodeData] representation of the receiver node as it would be exported by a [ModelExporter].
 * This function is recursively called on the node's children.
 */
fun INode.asExported(): NodeData {
    val idKey = NodeData.idPropertyKey
    return NodeData(
        id = getPropertyValue(idKey) ?: reference.serialize(),
        concept = concept?.getUID(),
        role = roleInParent,
        properties = getPropertyRoles().associateWithNotNull { getPropertyValue(it) }.filterKeys { it != idKey },
        references = getReferenceRoles().associateWithNotNull {
            getReferenceTarget(it)?.getPropertyValue(idKey) ?: getReferenceTargetRef(it)?.serialize()
        },
        children = allChildren.map { it.asExported() },
    )
}

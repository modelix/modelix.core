package org.modelix.model.sync.bulk

import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.api.INode
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.getOriginalOrCurrentReference
import org.modelix.model.api.getOriginalReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.data.NodeData

/**
 * A ModelExporter exports a node and its subtree in bulk.
 */
expect class ModelExporter(root: INode)

/**
 * Returns a [NodeData] representation of the receiver node as it would be exported by a [ModelExporter].
 * This function is recursively called on the node's children.
 */
fun INode.asExported(): NodeData {
    return asReadableNode().asExported()
}

fun IReadableNode.asExported(): NodeData {
    @OptIn(DelicateModelixApi::class) // json file should contain role IDs
    return NodeData(
        id = getOriginalOrCurrentReference(),
        concept = getConceptReference().takeIf { it != NullConcept.getReference() }?.getUID(),
        role = getContainmentLink().getIdOrNameOrNull(),
        properties = getAllProperties()
            .filterNot { it.first.matches(NodeData.ID_PROPERTY_REF) }
            .associate { it.first.getIdOrName() to it.second },
        references = getAllReferenceTargetRefs().associate {
            it.first.getIdOrName() to (getReferenceTarget(it.first)?.getOriginalReference() ?: it.second.serialize())
        },
        children = getAllChildren().map { it.asExported() },
    )
}

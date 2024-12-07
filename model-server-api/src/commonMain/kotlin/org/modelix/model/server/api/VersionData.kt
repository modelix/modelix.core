package org.modelix.model.server.api

import kotlinx.serialization.Serializable

@Serializable
data class VersionData(
    val repositoryId: String? = null,
    val versionHash: String? = null,
    val rootNodeId: String? = null,
    val usesRoleIds: Boolean = false,
    val nodes: List<NodeData>,
)

fun VersionData.merge(older: VersionData): VersionData {
    val mergedNodes: Map<NodeId, NodeData> = older.nodes.associateBy { it.nodeId } + nodes.associateBy { it.nodeId }
    val deletedNodes: List<NodeData> = mergedNodes.values.filter { node ->
        val parent = node.parent?.let { mergedNodes[it] } ?: return@filter true
        !parent.children.values.flatten().contains(node.nodeId)
    }
    val deletedAndDescendants = deletedNodes.asSequence()
        .flatMap { mergedNodes.descendants(it.nodeId, true) }.toSet()
    val filteredMergedNodes = mergedNodes - deletedAndDescendants
    return VersionData(
        repositoryId = repositoryId ?: older.repositoryId,
        versionHash = versionHash,
        rootNodeId = rootNodeId ?: older.rootNodeId,
        usesRoleIds = usesRoleIds || older.usesRoleIds,
        nodes = filteredMergedNodes.values.toList(),
    )
}

private fun Map<NodeId, NodeData>.descendants(nodeId: NodeId, includeSelf: Boolean): Sequence<NodeId> {
    return if (includeSelf) {
        sequenceOf(nodeId) + descendants(nodeId, false)
    } else {
        val data = this[nodeId] ?: return emptySequence()
        data.children.values.asSequence().flatten().flatMap { descendants(it, true) }
    }
}

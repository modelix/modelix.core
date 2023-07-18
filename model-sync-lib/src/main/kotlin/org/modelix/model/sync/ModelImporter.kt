package org.modelix.model.sync

import org.modelix.model.api.*
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import java.io.File

class ModelImporter(private val root: INode, val stats: ImportStats? = null) {

    private val originalIdToExisting: MutableMap<String, INode> = mutableMapOf()
    private val originalIdToSpec: MutableMap<String, NodeData> = mutableMapOf()
    private val originalIdToRef: MutableMap<String, INodeReference> = mutableMapOf()

    fun import(jsonFile: File) {
        require(jsonFile.exists())
        require(jsonFile.extension == "json")

        val data = ModelData.fromJson(jsonFile.readText())
        import(data)
    }
    
    fun import(data: ModelData) {
        stats?.reset()
        originalIdToExisting.clear()
        originalIdToSpec.clear()
        originalIdToRef.clear()

        syncProperties(root, data.root) // root original id is required for following operations
        val allExistingNodes = root.getDescendants(true).toList()
        buildSpecIndex(data.root)

        syncAllProperties(allExistingNodes)
        buildExistingIndex(allExistingNodes)

        sortAllExistingChildren(allExistingNodes)
        val addedNodes = addAllMissingChildren(root)
        syncAllProperties(addedNodes)

        val allNodes = allExistingNodes + addedNodes

        handleAllMovesAcrossParents(allNodes)

        buildRefIndex(allNodes)
        syncAllReferences(allNodes)

        deleteAllExtraChildren(root)
    }

    private fun buildExistingIndex(allNodes: List<INode>) {
        allNodes.forEach {node ->
            node.originalId()?.let { originalIdToExisting[it] = node }
        }
    }

    private fun buildRefIndex(allNodes: List<INode>) {
        allNodes.forEach {node ->
            node.originalId()?.let { originalIdToRef[it] = node.reference }
        }
    }

    private fun buildSpecIndex(nodeData: NodeData) {
        nodeData.originalId()?.let { originalIdToSpec[it] = nodeData }
        nodeData.children.forEach { buildSpecIndex(it) }
    }

    private fun syncAllProperties(allNodes: List<INode>) {
        allNodes.forEach {node ->
            originalIdToSpec[node.originalId()]?.let { spec -> syncProperties(node, spec) }
        }
    }

    private fun syncProperties(node: INode, nodeData: NodeData) {
        if (node.getPropertyValue(NodeData.idPropertyKey) == null) {
            node.setPropertyValue(NodeData.idPropertyKey, nodeData.originalId())
        }

        nodeData.properties.forEach {
            if (node.getPropertyValue(it.key) != it.value) {
                node.setPropertyValueWithStats(it.key, it.value)
            }
        }

        val toBeRemoved = node.getPropertyRoles().toSet()
            .subtract(nodeData.properties.keys)
            .filter { it != NodeData.idPropertyKey }
        toBeRemoved.forEach { node.setPropertyValueWithStats(it, null) }
    }

    private fun syncAllReferences(allNodes: List<INode>) {
        allNodes.forEach {node ->
            originalIdToSpec[node.originalId()]?.let { spec -> syncReferences(node, spec, originalIdToRef) }
        }
    }

    private fun syncReferences(node: INode, nodeData: NodeData, originalIdToRef: Map<String, INodeReference>) {
        nodeData.references.forEach {
            if (node.getReferenceTargetRef(it.key) != originalIdToRef[it.value]) {
                node.setReferenceTargetWithStats(it.key, originalIdToRef[it.value])
            }
        }
        val toBeRemoved = node.getReferenceRoles().toSet().subtract(nodeData.references.keys)
        toBeRemoved.forEach { node.setReferenceTargetWithStats(it, null) }
    }

    private fun addAllMissingChildren(node: INode): MutableList<INode> {
        val addedNodes = mutableListOf<INode>()
        originalIdToSpec[node.originalId()]?.let {
            addedNodes.addAll(
                addMissingChildren(node, it)
            )
        }
        node.allChildren.forEach {
            addedNodes.addAll(addAllMissingChildren(it))
        }
        return addedNodes
    }

    private fun addMissingChildren(node: INode, nodeData: NodeData): List<INode> {
        val specifiedChildren = nodeData.children.toList()
        val toBeAdded = specifiedChildren.filter { !originalIdToExisting.contains(it.originalId()) }

        return toBeAdded.map { nodeToBeAdded ->
            val childrenInRole = node.allChildren.filter { it.roleInParent == nodeToBeAdded.role }
            val existingIds = childrenInRole.map { it.originalId() }
            val baseIndex = nodeToBeAdded.getIndexWithinRole(nodeData)
            var offset = 0
            offset += childrenInRole.slice(0..minOf(baseIndex, childrenInRole.lastIndex)).count {
                !originalIdToSpec.containsKey(it.originalId()) // node will be deleted
            }
            offset -= specifiedChildren.filter { it.role == nodeToBeAdded.role }.slice(0 until baseIndex).count {
                !existingIds.contains(it.originalId()) // node will be moved here
            }
            val index = (baseIndex + offset).coerceIn(0..childrenInRole.size)
            node.addNewChildWithStats(nodeToBeAdded, index)
        }
    }

    private fun sortAllExistingChildren(
        allNodes: Iterable<INode>
    ) {
        allNodes.forEach { node ->
            originalIdToSpec[node.originalId()]?.let { sortExistingChildren(node, it) }
        }
    }

    private fun sortExistingChildren(node: INode, nodeData: NodeData) {
        val existingChildren = node.allChildren.toList()
        val existingIds = existingChildren.map { it.originalId() }
        val specifiedChildren = nodeData.children
        val toBeSortedSpec = specifiedChildren.filter { originalIdToExisting.containsKey(it.originalId()) }

        val targetIndices = HashMap<String?, Int>(nodeData.children.size)
        for (child in toBeSortedSpec) {
            val childrenInRole = existingChildren.filter { it.roleInParent == child.role }
            val baseIndex = child.getIndexWithinRole(nodeData)
            var offset = 0
            offset += childrenInRole.slice(0..baseIndex.coerceAtMost(childrenInRole.lastIndex)).count {
                !originalIdToSpec.containsKey(it.originalId()) // node will be deleted
            }
            offset -= specifiedChildren
                .filter { it.role == child.role }
                .slice(0..baseIndex.coerceIn(0..specifiedChildren.lastIndex))
                .count {
                    !existingIds.contains(it.originalId()) // node will be moved here
                }
            val index = (baseIndex + offset).coerceIn(0..childrenInRole.size)
            targetIndices[child.originalId()] = index
        }

        existingChildren.forEach { child ->
            val currentIndex = child.index()
            val targetRole = originalIdToSpec[child.originalId()]?.role
            val targetIndex = targetIndices[child.originalId()]
            if (targetIndex != null && (targetIndex != currentIndex || child.roleInParent != targetRole)) {
                node.moveChildWithStats(targetRole, targetIndex, child)
            }
        }
    }

    private fun handleAllMovesAcrossParents(allNodes: List<INode>) {
        val moves = collectMovesAcrossParents(allNodes)
        while (moves.isNotEmpty()) {
            val nextMove = moves.first { !it.nodeToBeMoved.getDescendants(false).contains(it.targetParent) }
            performMoveAcrossParents(nextMove.targetParent, nextMove.nodeToBeMoved)
            moves.remove(nextMove)
        }
    }

    private fun collectMovesAcrossParents(allNodes: List<INode>): MutableList<MoveAcrossParents> {
        val movesAcrossParents = mutableListOf<MoveAcrossParents>()
        allNodes.forEach {node ->
            val nodeData = originalIdToSpec[node.originalId()] ?: return@forEach

            val missingIds = nodeData.children.map { it.originalId() }.toSet()
                .subtract(node.allChildren.map { it.originalId() }.toSet())
            val toBeMovedHere = missingIds
                .filter { originalIdToSpec.containsKey(it) }
                .mapNotNull { originalIdToExisting[it] }

            toBeMovedHere.forEach {
                movesAcrossParents.add(MoveAcrossParents(node, it))
            }
        }
        return movesAcrossParents
    }

    private data class MoveAcrossParents(val targetParent: INode, val nodeToBeMoved: INode)

    private fun performMoveAcrossParents(node: INode, toBeMovedHere: INode) {
        val nodeData = originalIdToSpec[node.originalId()] ?: return
        val existingChildren = node.allChildren.toList()
        val spec = originalIdToSpec[toBeMovedHere.originalId()]!!
        val childrenInRole = existingChildren.filter { it.roleInParent == spec.role }
        val baseTargetIndex = spec.getIndexWithinRole(nodeData).coerceAtMost(childrenInRole.size)
        val offset = childrenInRole.slice(0 until  baseTargetIndex).count {
            !originalIdToSpec.containsKey(it.originalId()) // node will be deleted
        }
        val targetIndex = (baseTargetIndex + offset).coerceIn(0..childrenInRole.size)

        node.moveChildWithStats(spec.role, targetIndex, toBeMovedHere)

    }

    private fun deleteAllExtraChildren(root: INode) {
        val toBeRemoved = mutableListOf<INode>()
        root.allChildren.forEach {
            if (!originalIdToSpec.containsKey(it.originalId())) {
                toBeRemoved.add(it)
            }
        }
        toBeRemoved.forEach {
            it.parent?.removeChildWithStats(it)
        }
        root.allChildren.forEach {
            deleteAllExtraChildren(it)
        }
    }

    private fun NodeData.getIndexWithinRole(parent: NodeData) : Int =
        parent.children.filter { it.role == this.role }.indexOf(this)


    private fun INode.addNewChildWithStats(spec: NodeData, index: Int) : INode {
        val concept = spec.concept?.let { s -> ConceptReference(s) }

        val createdNode = addNewChild(spec.role, index, concept)
        createdNode.setPropertyValue(NodeData.idPropertyKey, spec.originalId())
        if (this@ModelImporter.stats != null) {
            stats.addAddition(
                createdNode.originalId(),
                this.originalId(),
                createdNode.roleInParent,
                index
            )
        }
        return createdNode
    }

    private fun INode.moveChildWithStats(role: String?, index: Int, child: INode) {
        if (this@ModelImporter.stats != null) {
            stats.addMove(
                child.originalId(),
                child.parent?.originalId(),
                child.roleInParent,
                child.index(),
                this.originalId(),
                role,
                index
            )
        }
        moveChild(role, index, child)
    }

    private fun INode.removeChildWithStats(child: INode) {
        if (this@ModelImporter.stats != null) {
            stats.addDeletion(child.originalId(), parent?.originalId(), child.roleInParent, child.getDescendants(false).mapNotNull { it.originalId() }.toList())
        }
        removeChild(child)
    }

    private fun INode.setPropertyValueWithStats(role: String, value: String?) {
        if (this@ModelImporter.stats != null) {
            this.originalId()?.let { stats.addPropertyChange(it, role) }
        }
        setPropertyValue(role, value)
    }

    private fun INode.setReferenceTargetWithStats(role: String, target: INodeReference?) {
        if (this@ModelImporter.stats != null) {
            this.originalId()?.let { stats.addReferenceChange(it, role) }
        }
        setReferenceTarget(role, target)
    }

}

internal fun INode.originalId(): String? {
    return this.getPropertyValue(NodeData.idPropertyKey)
}

internal fun NodeData.originalId(): String? {
    return properties[NodeData.idPropertyKey] ?: id
}
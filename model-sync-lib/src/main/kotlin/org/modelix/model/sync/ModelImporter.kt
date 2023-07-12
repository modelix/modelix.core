package org.modelix.model.sync

import org.modelix.model.api.*
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import java.io.File

class ModelImporter(private val root: INode, val stats: ImportStats? = null) {

    fun import(jsonFile: File) {
        require(jsonFile.exists())
        require(jsonFile.extension == "json")

        val data = ModelData.fromJson(jsonFile.readText())
        import(data)
    }
    
    fun import(data: ModelData) {
        syncProperties(root, data.root) // root original id is required for following operations
        val allExistingNodes = root.getDescendants(true).toList()
        val originalIdToSpec: MutableMap<String, NodeData> = buildSpecIndex(data.root)

        syncAllProperties(allExistingNodes, originalIdToSpec)
        val originalIdToExisting = buildExistingIndex(allExistingNodes)

        sortAllExistingChildren(allExistingNodes, originalIdToExisting, originalIdToSpec)
        val addedNodes = addAllMissingChildren(root, originalIdToExisting, originalIdToSpec)
        syncAllProperties(addedNodes, originalIdToSpec)

        handleAllMovesAcrossParents(allExistingNodes, originalIdToExisting, originalIdToSpec)

        val allNodes = allExistingNodes + addedNodes
        val originalIdToRef: MutableMap<String, INodeReference> = buildRefIndex(allNodes)
        syncAllReferences(allNodes, originalIdToSpec, originalIdToRef)
        deleteAllExtraChildren(allNodes, originalIdToSpec)

    }

    private fun buildExistingIndex(allNodes: List<INode>): MutableMap<String, INode> {
        val originalIdToExisting: MutableMap<String, INode> = mutableMapOf()
        allNodes.forEach {node ->
            node.originalId()?.let { originalIdToExisting[it] = node }
        }
        return originalIdToExisting
    }

    private fun buildRefIndex(allNodes: List<INode>): MutableMap<String, INodeReference> {
        val originalIdToRef: MutableMap<String, INodeReference> = mutableMapOf()
        allNodes.forEach {node ->
            node.originalId()?.let { originalIdToRef[it] = node.reference }
        }
        return originalIdToRef
    }

    private fun buildSpecIndex(
        nodeData: NodeData,
        originalIdToSpec: MutableMap<String, NodeData> = mutableMapOf()
    ): MutableMap<String, NodeData> {
        nodeData.originalId()?.let { originalIdToSpec[it] = nodeData }
        nodeData.children.forEach { buildSpecIndex(it, originalIdToSpec) }
        return originalIdToSpec
    }

    private fun syncAllProperties(allNodes: List<INode>, originalIdToSpec: MutableMap<String, NodeData>) {
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

    private fun syncAllReferences(allNodes: List<INode>, originalIdToSpec: MutableMap<String, NodeData>, originalIdToRef: Map<String, INodeReference>) {
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

    private fun addAllMissingChildren(
        node: INode,
        originalIdToExisting: MutableMap<String, INode>,
        originalIdToSpec: MutableMap<String, NodeData>
    ): MutableList<INode> {
        val addedNodes = mutableListOf<INode>()
        originalIdToSpec[node.originalId()]?.let {
            addedNodes.addAll(
                addMissingChildren(node, it, originalIdToExisting, originalIdToSpec)
            )
        }
        node.allChildren.forEach {
            addedNodes.addAll(addAllMissingChildren(it, originalIdToExisting, originalIdToSpec))
        }
        return addedNodes
    }

    private fun addMissingChildren(
        node: INode,
        nodeData: NodeData,
        originalIdToExisting: MutableMap<String, INode>,
        originalIdToSpec: MutableMap<String, NodeData>
    ): List<INode> {
        val specifiedChildren = nodeData.children.toList()
        val toBeAdded = specifiedChildren.filter { !originalIdToExisting.contains(it.originalId()) }

        return toBeAdded.map { nodeToBeAdded ->
            val childrenInRole = node.allChildren.filter { it.roleInParent == nodeToBeAdded.role }
            val existingIds = childrenInRole.map { it.originalId() }
            val baseIndex = nodeToBeAdded.getIndexWithinRole(nodeData, childrenInRole.size)
            var offset = 0
            offset += childrenInRole.slice(0..minOf(baseIndex, childrenInRole.lastIndex)).count {
                !originalIdToSpec.containsKey(it.originalId()) // node will be deleted
            }
            offset -= specifiedChildren.filter { it.role == nodeToBeAdded.role }.slice(0 until baseIndex).count {
                !existingIds.contains(it.originalId()) // node will be moved here
            }
            node.addNewChildWithStats(nodeToBeAdded, baseIndex + offset)
        }
    }

    private fun sortAllExistingChildren(
        allNodes: List<INode>,
        originalIdToExisting: MutableMap<String, INode>,
        originalIdToSpec: MutableMap<String, NodeData>
    ) {
        allNodes.forEach { node ->
            originalIdToSpec[node.originalId()]?.let { sortExistingChildren(node, it, originalIdToExisting, originalIdToSpec) }
        }
    }

    private fun sortExistingChildren(
        node: INode,
        nodeData: NodeData,
        originalIdToExisting: MutableMap<String, INode>,
        originalIdToSpec: MutableMap<String, NodeData>
    ) {
        val existingChildren = node.allChildren.toList()
        val existingIds = existingChildren.map { it.originalId() }
        val specifiedChildren = nodeData.children
        val toBeSortedSpec = specifiedChildren.filter { originalIdToExisting.containsKey(it.originalId()) }

        val targetIndices = HashMap<String?, Int>(nodeData.children.size)
        for (child in toBeSortedSpec) {

            val childrenInRole = existingChildren.filter { it.roleInParent == child.role }
            val baseIndex = child.getIndexWithinRole(nodeData, childrenInRole.lastIndex)
            var offset = 0
            offset += childrenInRole.slice(0..baseIndex).count {
                !originalIdToSpec.containsKey(it.originalId()) // node will be deleted
            }
            offset -= childrenInRole.slice(0..baseIndex).count {
                !existingIds.contains(it.originalId()) // node will be moved here
            }
            val index = if (childrenInRole.isEmpty()) 0 else baseIndex + offset
            val upperBound = if (existingChildren.isEmpty()) 0 else existingChildren.lastIndex
            targetIndices[child.originalId()] = minOf(index, upperBound)
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

    private fun handleAllMovesAcrossParents(
        allNodes: List<INode>,
        originalIdToExisting: MutableMap<String, INode>,
        originalIdToSpec: MutableMap<String, NodeData>
    ) {
        allNodes.forEach {
            originalIdToSpec[it.originalId()]?.let { spec -> handleMoveAcrossParents(it, spec, originalIdToExisting, originalIdToSpec) }
        }
    }

    private fun handleMoveAcrossParents(
        node: INode,
        nodeData: NodeData,
        originalIdToExisting: MutableMap<String, INode>,
        originalIdToSpec: MutableMap<String, NodeData>
    ) {

        val existingChildren = node.allChildren.toList()
        val missingIds = nodeData.children.map { it.originalId() }.toSet()
            .subtract(node.allChildren.map { it.originalId() }.toSet())
        val toBeMovedHere = missingIds
            .filter { originalIdToSpec.containsKey(it) }
            .mapNotNull { originalIdToExisting[it] }

        toBeMovedHere.forEach {nodeToBeMoved ->
            val spec = originalIdToSpec[nodeToBeMoved.originalId()]!!

            val baseTargetIndex = spec.getIndexWithinRole(nodeData, existingChildren.size - 1)
            val offset = existingChildren.slice(0..baseTargetIndex).count {
                !originalIdToSpec.containsKey(it.originalId()) // node will be deleted
            }
            val targetIndex = baseTargetIndex + offset

            node.moveChildWithStats(spec.role, targetIndex, nodeToBeMoved)
        }
    }

    private fun deleteAllExtraChildren(allNodes: List<INode>, originalIdToSpec: MutableMap<String, NodeData>) {
        val toBeRemoved = mutableListOf<INode>()
        allNodes.forEach { node ->
            node.allChildren.forEach {
                if (!originalIdToSpec.containsKey(it.originalId())) {
                    toBeRemoved.add(it)
                }
            }
        }
        toBeRemoved.asReversed().forEach {// delete bottom-up
            it.parent?.removeChildWithStats(it)
        }
    }

    private fun NodeData.getIndexWithinRole(parent: NodeData, maxIndex: Int) : Int {
        return minOf(parent.children.filter { it.role == this.role }.indexOf(this), maxIndex)
    }

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
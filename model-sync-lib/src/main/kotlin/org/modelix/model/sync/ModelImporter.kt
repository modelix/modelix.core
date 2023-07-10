package org.modelix.model.sync

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import java.io.File

class ModelImporter(private val root: INode, val stats: ImportStats? = null) {

    private lateinit var originalIdToSpec: MutableMap<String, NodeData>
    private lateinit var originalIdToExisting: MutableMap<String, INode>
    private lateinit var originalIdToRef: MutableMap<String, INodeReference>

    fun import(jsonFile: File) {
        require(jsonFile.exists())
        require(jsonFile.extension == "json")

        val data = ModelData.fromJson(jsonFile.readText())
        import(data)
    }
    
    fun import(data: ModelData) {
        originalIdToExisting = mutableMapOf()
        buildExistingIndex(root)

        originalIdToSpec = mutableMapOf()
        buildSpecIndex(data.root)

        syncNode(root, data.root)

        originalIdToRef = mutableMapOf()
        buildRefIndex(root)

        syncAllReferences(root, data.root)
    }

    private fun buildExistingIndex(root: INode) {
        root.originalId()?.let { originalIdToExisting[it] = root }
        root.allChildren.forEach { buildExistingIndex(it) }
    }

    private fun buildRefIndex(node: INode) {
        node.originalId()?.let { originalIdToRef[it] = node.reference }
        node.allChildren.forEach { buildRefIndex(it) }
    }

    private fun buildSpecIndex(nodeData: NodeData) {
        nodeData.originalId()?.let { originalIdToSpec[it] = nodeData }
        nodeData.children.forEach { buildSpecIndex(it) }
    }

    private fun syncNode(node: INode, nodeData: NodeData) {
        syncProperties(node, nodeData)
        syncChildNodes(node, nodeData)
    }

    private fun syncProperties(node: INode, nodeData: NodeData) {
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

    private fun syncAllReferences(root: INode, rootData: NodeData) {
        syncReferences(root, rootData)
        for ((node, data) in root.allChildren.zip(rootData.children)) {
            syncAllReferences(node, data)
        }
    }

    private fun syncReferences(node: INode, nodeData: NodeData) {
        nodeData.references.forEach {
            if (node.getReferenceTargetRef(it.key) != originalIdToRef[it.value]) {
                node.setReferenceTargetWithStats(it.key, originalIdToRef[it.value])
            }
        }
        val toBeRemoved = node.getReferenceRoles().toSet().subtract(nodeData.references.keys)
        toBeRemoved.forEach { node.setReferenceTargetWithStats(it, null) }
    }

    private fun syncChildNodes(node: INode, nodeData: NodeData) {
        val specifiedNodes = nodeData.children.toSet()
        val existingIds = node.allChildren.map { it.originalId() }.toSet()
        val missingNodes = specifiedNodes.filter { !existingIds.contains(it.originalId()) }

        val toBeRemoved = node.allChildren.filter { !originalIdToSpec.contains(it.originalId()) }
        toBeRemoved.forEach { node.removeChildWithStats(it) }

        val toBeMovedAwayFromHere = existingIds.subtract(specifiedNodes.map { it.originalId() }.toSet())

        syncExistingChildOrder(node, nodeData, existingIds, toBeMovedAwayFromHere)

        val toBeMovedHere = missingNodes.filter { originalIdToExisting.containsKey(it.originalId()) }.toSet()
        toBeMovedHere.forEach {
            val actualNode = originalIdToExisting[it.originalId()]
            val targetIndex = it.getIndexWithinRole(nodeData,existingIds.size - 1)
            if (actualNode != null) {
                node.moveChildWithStats(it.role, targetIndex, actualNode)
            }
        }

        val toBeAdded = missingNodes.subtract(toBeMovedHere)
        toBeAdded.forEach {
            val index = it.getIndexWithinRole(nodeData, existingIds.size - 1)
            node.addNewChildWithStats(it, index)
        }

        node.allChildren.forEach {
            val childData = originalIdToSpec[it.originalId()]
            if (childData != null) {
                syncNode(it, childData)
            }
        }
    }

    private fun syncExistingChildOrder(
        node: INode,
        nodeData: NodeData,
        existingIds: Set<String?>,
        toBeMovedAwayFromHere: Set<String?>
    ) {
        val existingChildren = node.allChildren.toList()
        val specifiedChildren = nodeData.children
        require(existingChildren.size <= specifiedChildren.size)

        val filteredSpecifiedChildren = specifiedChildren.filter { existingIds.contains(it.originalId()) }

        val targetIndices = HashMap<String?, Int>(nodeData.children.size)
        for (specifiedChild in filteredSpecifiedChildren) {
            val index = filteredSpecifiedChildren.filter { it.role == node.roleInParent }.indexOf(specifiedChild)
            targetIndices[specifiedChild.originalId()] = minOf(index, existingChildren.size - 1)
        }

        for ((index, child) in existingChildren.withIndex()) {
            val targetIndex = targetIndices[child.originalId()] ?: -1
            if (targetIndex != index && !toBeMovedAwayFromHere.contains(child.originalId())) {
                node.moveChildWithStats(child.roleInParent, targetIndex, child)
            }
        }
    }

    private fun NodeData.getIndexWithinRole(parent: NodeData, maxIndex: Int) : Int {
        return minOf(parent.children.filter { it.role == this.role }.indexOf(this), maxIndex)
    }

    private fun INode.originalId(): String? {
        return this.getPropertyValue(NodeData.idPropertyKey)
    }

    private fun NodeData.originalId(): String? {
        return properties[NodeData.idPropertyKey] ?: id
    }

    private fun INode.addNewChildWithStats(spec: NodeData, index: Int) : INode {
        val concept = spec.concept?.let { s -> ConceptReference(s) }

        val createdNode = addNewChild(spec.role, index, concept)
        createdNode.setPropertyValue(NodeData.idPropertyKey, spec.originalId())
        if (this@ModelImporter.stats != null) {
            createdNode.originalId()?.let { stats.addAddition(it) }
        }
        return createdNode
    }

    private fun INode.moveChildWithStats(role: String?, index: Int, child: INode) {
        if (this@ModelImporter.stats != null) {
            child.originalId()?.let { stats.addMove(it) }
        }
        return moveChild(role, index, child)
    }

    private fun INode.removeChildWithStats(child: INode) {
        if (this@ModelImporter.stats != null) {
            child.originalId()?.let { stats.addDeletion(it) }
        }
        return removeChild(child)
    }

    private fun INode.setPropertyValueWithStats(role: String, value: String?) {
        if (this@ModelImporter.stats != null) {
            this.originalId()?.let { stats.addPropertyChange(it, role) }
        }
        return setPropertyValue(role, value)
    }

    private fun INode.setReferenceTargetWithStats(role: String, target: INodeReference?) {
        if (this@ModelImporter.stats != null) {
            this.originalId()?.let { stats.addReferenceChange(it, role) }
        }
        return setReferenceTarget(role, target)
    }

}
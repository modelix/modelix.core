package org.modelix.model.sync

import org.modelix.model.api.*
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData

class ModelImporter(private val branch: IBranch) {

    companion object {
        const val idRole = "originalId"
    }

    private lateinit var originalIdToRef: MutableMap<String, INodeReference>

    fun import(data: ModelData) {
        originalIdToRef = mutableMapOf()
        branch.runWrite {
            val rootNode = branch.getRootNode()
            if (rootNode.allChildren.none()) {
                data.load(branch)
                return@runWrite
            }
            buildRefIndex(rootNode)
            syncNode(rootNode, data.root)
        }
    }

    private fun buildRefIndex(node: INode) {
        node.originalId()?.let { originalIdToRef[it] = node.reference }
        node.allChildren.forEach { buildRefIndex(it) }
    }

    private fun syncNode(node: INode, nodeData: NodeData) {
        syncProperties(node, nodeData)
        syncReferences(node, nodeData)
        syncChildNodes(node, nodeData)
    }

    private fun syncProperties(node: INode, nodeData: NodeData) {
        nodeData.properties.forEach { node.setPropertyValue(it.key, it.value) }
    }

    private fun syncReferences(node: INode, nodeData: NodeData) {
        nodeData.references.forEach { node.setReferenceTarget(it.key, originalIdToRef[it.value]) }
    }

    private fun syncChildNodes(node: INode, nodeData: NodeData) {
        val toBeRemoved = mutableSetOf<INode>()
        val existingIds = mutableSetOf<String>()

        for (child in node.allChildren) {
            child.originalId()?.let { existingIds.add(it) }
            val foundChildData: NodeData? = nodeData.children.find { it.id == child.originalId() }
            if (foundChildData != null) {
                syncNode(child, foundChildData)
            } else {
                toBeRemoved.add(child)
            }
        }

        val toBeAdded = nodeData.children.filter { !existingIds.contains(it.id) }

        toBeAdded.forEach {
            val index = nodeData.children.indexOf(it)
            val createdNode = node.addNewChild(it.role, index, it.concept?.let { s -> ConceptReference(s) })
            createdNode.setPropertyValue(idRole, it.properties[idRole] ?: it.id)
        }
        toBeRemoved.forEach { node.removeChild(it) }
            syncChildOrder(node, nodeData)
    }

    private fun syncChildOrder(node: INode, nodeData: NodeData) {
        val existingChildren = node.allChildren.toList()
        val specifiedChildren = nodeData.children
        require(existingChildren.size == specifiedChildren.size)

        for ((actualNode, specifiedNode) in existingChildren zip specifiedChildren) {
            val actualId = actualNode.originalId()
            if (actualId != specifiedNode.originalId() ) {
                val targetIndex = specifiedChildren.indexOfFirst { actualId == it.originalId() }
                node.moveChild(actualNode.roleInParent, targetIndex, actualNode)
            }
        }
    }

    private fun INode.originalId(): String? {
        return this.getPropertyValue(idRole)
    }

    private fun NodeData.originalId(): String? {
        return properties[idRole] ?: id
    }

}
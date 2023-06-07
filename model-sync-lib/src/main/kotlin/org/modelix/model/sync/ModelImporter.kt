package org.modelix.model.sync

import org.modelix.model.api.*
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData

class ModelImporter(private val branch: IBranch, private val data: ModelData) {

    companion object {
        const val idRole = "originalId"
    }

    private lateinit var originalIdToRef: MutableMap<String, INodeReference>

    fun import() {
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
            node.addNewChild(it.role, index, it.concept?.let { s -> ConceptReference(s) })
        }
        toBeRemoved.forEach { node.removeChild(it) }

    }

    private fun INode.originalId(): String? {
        return this.getPropertyValue(idRole)
    }

    private fun NodeData.originalId(): String? {
        return this.properties[idRole]
    }

}
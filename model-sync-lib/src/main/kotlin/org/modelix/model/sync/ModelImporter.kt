package org.modelix.model.sync

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import java.io.File

class ModelImporter(private val root: INode) {

    private lateinit var originalIdToRef: MutableMap<String, INodeReference>
    private lateinit var originalIdToSpec: MutableMap<String, NodeData>

    fun import(jsonFile: File) {
        require(jsonFile.exists())
        require(jsonFile.extension == "json")

        val data = ModelData.fromJson(jsonFile.readText())
        import(data)
    }
    
    fun import(data: ModelData) {
        originalIdToRef = mutableMapOf()
        originalIdToSpec = mutableMapOf()
        buildSpecIndex(data.root)
        syncNode(root, data.root)
        buildRefIndex(root)
        syncAllReferences(root, data.root)
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
        nodeData.properties.forEach { node.setPropertyValue(it.key, it.value) }
        val toBeRemoved = node.getPropertyRoles().toSet()
            .subtract(nodeData.properties.keys)
            .filter { it != NodeData.idPropertyKey }
        toBeRemoved.forEach { node.setPropertyValue(it, null) }
    }

    private fun syncAllReferences(root: INode, rootData: NodeData) {
        syncReferences(root, rootData)
        for ((node, data) in root.allChildren.zip(rootData.children)) {
            syncAllReferences(node, data)
        }
    }

    private fun syncReferences(node: INode, nodeData: NodeData) {
        nodeData.references.forEach { node.setReferenceTarget(it.key, originalIdToRef[it.value]) }
        val toBeRemoved = node.getReferenceRoles().toSet().subtract(nodeData.references.keys)
        toBeRemoved.forEach { node.setPropertyValue(it, null) }
    }

    private fun syncChildNodes(node: INode, nodeData: NodeData) {
        val toBeRemoved = mutableSetOf<INode>()
        val existingIds = node.allChildren.map { it.originalId() }

        val toBeAdded = nodeData.children.filter { !existingIds.contains(it.id) }

        toBeAdded.forEach {
            val index = nodeData.children.indexOf(it)
            val createdNode = node.addNewChild(it.role, index, it.concept?.let { s -> ConceptReference(s) })
            createdNode.setPropertyValue(NodeData.idPropertyKey, it.originalId())
        }

        for (child in node.allChildren) {
            val foundChildData: NodeData? = originalIdToSpec[child.originalId()]
            if (foundChildData != null) {
                syncNode(child, foundChildData)
            } else {
                toBeRemoved.add(child)
            }
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
        return this.getPropertyValue(NodeData.idPropertyKey)
    }

    private fun NodeData.originalId(): String? {
        return properties[NodeData.idPropertyKey] ?: id
    }

}
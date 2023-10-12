/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.sync.bulk

import mu.KotlinLogging
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.getDescendants
import org.modelix.model.api.remove
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import kotlin.jvm.JvmName

/**
 * A ModelImporter updates an existing [INode] and its subtree based on a [ModelData] specification.
 *
 * The import is incremental.
 * Instead of simply overwriting the existing model, only a minimal amount of operations is used.
 *
 * Properties, references, and child links are synchronized for this node and all of its (in-)direct children.
 *
 * @param root the root node to be updated
 */
class ModelImporter(private val root: INode) {

    private val originalIdToExisting: MutableMap<String, INode> = mutableMapOf()
    private val postponedReferences = ArrayList<() -> Unit>()
    private val nodesToRemove = HashSet<INode>()
    private var numExpectedNodes = 0
    private var currentNodeProgress = 0
    private val logger = KotlinLogging.logger {}

    /**
     * Incrementally updates this importers root based on the provided [ModelData] specification.
     *
     * @param data the model specification
     */
    @JvmName("importData")
    fun import(data: ModelData) {
        logImportSize(data.root, logger)
        logger.info { "Building indices for import..." }
        originalIdToExisting.clear()
        postponedReferences.clear()
        nodesToRemove.clear()
        numExpectedNodes = countExpectedNodes(data.root)
        currentNodeProgress = 0
        buildExistingIndex(root)

        logger.info { "Importing nodes..." }
        data.root.originalId()?.let { originalIdToExisting[it] = root }
        syncNode(root, data.root)

        logger.info { "Synchronizing references..." }
        postponedReferences.forEach { it.invoke() }

        logger.info { "Removing extra nodes..." }
        nodesToRemove.forEach { it.remove() }

        logger.info { "Synchronization finished." }
    }

    private fun countExpectedNodes(data: NodeData): Int =
        1 + data.children.sumOf { countExpectedNodes(it) }

    private fun syncNode(node: INode, data: NodeData) {
        currentNodeProgress += 1
        // print instead of log, so that the progress line can be overwritten by the carriage return
        print("\r($currentNodeProgress / $numExpectedNodes) Synchronizing nodes...                    ")
        syncProperties(node, data)
        syncChildren(node, data)
        syncReferences(node, data)
    }

    private fun syncChildren(node: INode, data: NodeData) {
        val allRoles = (data.children.map { it.role } + node.allChildren.map { it.roleInParent }).distinct()
        for (role in allRoles) {
            val expectedNodes = data.children.filter { it.role == role }
            val existingNodes = node.getChildren(role).toList()

            // optimization for when there is no change in the child list
            // size check first to avoid querying the original ID
            if (expectedNodes.size == existingNodes.size && expectedNodes.map { it.originalId() } == existingNodes.map { it.originalId() }) {
                existingNodes.zip(expectedNodes).forEach { syncNode(it.first, it.second) }
                continue
            }

            expectedNodes.forEachIndexed { index, expected ->
                val nodeAtIndex = node.getChildren(role).toList().getOrNull(index)
                val expectedId = checkNotNull(expected.originalId()) { "Specified node '$expected' has no id" }
                val expectedConcept = expected.concept?.let { s -> ConceptReference(s) }
                val childNode = if (nodeAtIndex?.originalId() != expectedId) {
                    val existingNode = originalIdToExisting[expectedId]
                    if (existingNode == null) {
                        val newChild = node.addNewChild(role, index, expectedConcept)
                        newChild.setPropertyValue(NodeData.idPropertyKey, expectedId)
                        originalIdToExisting[expectedId] = newChild
                        newChild
                    } else {
                        node.moveChild(role, index, existingNode)
                        nodesToRemove.remove(existingNode)
                        existingNode
                    }
                } else {
                    nodeAtIndex
                }
                check(childNode.getConceptReference() == expectedConcept) { "Unexpected concept change" }

                syncNode(childNode, expected)
            }

            nodesToRemove += node.getChildren(role).drop(expectedNodes.size)
        }
    }

    private fun buildExistingIndex(root: INode) {
        root.getDescendants(true).forEach { node ->
            node.originalId()?.let { originalIdToExisting[it] = node }
        }
    }

    private fun syncProperties(node: INode, nodeData: NodeData) {
        if (node.getPropertyValue(NodeData.idPropertyKey) == null) {
            node.setPropertyValue(NodeData.idPropertyKey, nodeData.originalId())
        }

        nodeData.properties.forEach {
            if (node.getPropertyValue(it.key) != it.value) {
                node.setPropertyValue(it.key, it.value)
            }
        }

        val toBeRemoved = node.getPropertyRoles().toSet()
            .subtract(nodeData.properties.keys)
            .filter { it != NodeData.idPropertyKey }
        toBeRemoved.forEach { node.setPropertyValue(it, null) }
    }

    private fun syncReferences(node: INode, nodeData: NodeData) {
        nodeData.references.forEach {
            val expectedTargetId = it.value
            val actualTargetId = node.getReferenceTarget(it.key)?.originalId()
            if (actualTargetId != expectedTargetId) {
                val expectedTarget = originalIdToExisting[expectedTargetId]
                if (expectedTarget == null) {
                    postponedReferences += {
                        val expectedRefTarget = originalIdToExisting[expectedTargetId]
                        if (expectedRefTarget == null) {
                            // The target node is not part of the model. Assuming it exists in some other model we can
                            // store the reference and try to resolve it dynamically on access.
                            node.setReferenceTarget(it.key, SerializedNodeReference(expectedTargetId))
                        } else {
                            node.setReferenceTarget(it.key, expectedRefTarget)
                        }
                    }
                } else {
                    node.setReferenceTarget(it.key, expectedTarget)
                }
            }
        }
        val toBeRemoved = node.getReferenceRoles().toSet() - nodeData.references.keys
        toBeRemoved.forEach {
            val nullReference: INodeReference? = null
            node.setReferenceTarget(it, nullReference)
        }
    }
}

internal fun INode.originalId(): String? {
    return this.getPropertyValue(NodeData.idPropertyKey)
}

internal fun NodeData.originalId(): String? {
    return properties[NodeData.idPropertyKey] ?: id
}

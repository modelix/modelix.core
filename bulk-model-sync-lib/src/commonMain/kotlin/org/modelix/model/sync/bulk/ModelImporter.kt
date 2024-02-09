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
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.getConcept
import org.modelix.model.api.getDescendants
import org.modelix.model.api.remove
import org.modelix.model.api.tryResolveChildLink
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
class ModelImporter(private val root: INode, private val continueOnError: Boolean) {

    private val originalIdToExisting: MutableMap<String, INode> = mutableMapOf()
    private val postponedReferences = ArrayList<() -> Unit>()
    private val nodesToRemove = HashSet<INode>()
    private var numExpectedNodes = 0
    private var currentNodeProgress = 0
    private val logger = KotlinLogging.logger {}

    // For MPS / Java compatibility, where a default value does not work. Can be dropped once the MPS solution is
    // updated to the constructor with two arguments.
    constructor(root: INode) : this(root, false)

    private inline fun doAndPotentiallyContinueOnErrors(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (continueOnError) {
                logger.error(e) { "Ignoring this error and continuing as requested" }
            } else {
                throw e
            }
        }
    }

    /**
     * Incrementally updates this importers root based on the provided [ModelData] specification.
     *
     * @param data the model specification
     */
    @JvmName("importData")
    fun import(data: ModelData) {
        INodeResolutionScope.runWithAdditionalScope(root.getArea()) {
            logImportSize(data.root, logger)
            logger.info { "Building indices for import..." }
            originalIdToExisting.clear()
            postponedReferences.clear()
            nodesToRemove.clear()
            numExpectedNodes = countExpectedNodes(data.root)
            val progressReporter = ProgressReporter(numExpectedNodes.toULong(), logger)
            currentNodeProgress = 0
            buildExistingIndex(root)

            logger.info { "Importing nodes..." }
            data.root.originalId()?.let { originalIdToExisting[it] = root }
            syncNode(root, data.root, progressReporter)

            logger.info { "Synchronizing references..." }
            postponedReferences.forEach {
                doAndPotentiallyContinueOnErrors {
                    it.invoke()
                }
            }

            logger.info { "Removing extra nodes..." }
            nodesToRemove.forEach {
                doAndPotentiallyContinueOnErrors {
                    if (it.isValid) { // if it's invalid then it's already removed
                        it.remove()
                    }
                }
            }

            logger.info { "Synchronization finished." }
        }
    }

    private fun countExpectedNodes(data: NodeData): Int =
        1 + data.children.sumOf { countExpectedNodes(it) }

    private fun syncNode(node: INode, data: NodeData, progressReporter: ProgressReporter) {
        currentNodeProgress += 1
        progressReporter.step(currentNodeProgress.toULong())
        doAndPotentiallyContinueOnErrors {
            syncProperties(node, data)
            syncChildren(node, data, progressReporter)
            syncReferences(node, data)
        }
    }

    private fun syncChildren(node: INode, data: NodeData, progressReporter: ProgressReporter) {
        val allRoles = (data.children.map { it.role } + node.allChildren.map { it.roleInParent }).distinct()
        for (role in allRoles) {
            syncChildRole(data, role, node, progressReporter)
        }
    }

    private fun syncChildRole(
        data: NodeData,
        role: String?,
        node: INode,
        progressReporter: ProgressReporter,
    ) {
        val expectedNodes = data.children.filter { it.role == role }
        val existingNodes = node.getChildren(role).toList()

        // optimization that uses the bulk operation .addNewChildren
        if (existingNodes.isEmpty() && expectedNodes.all { originalIdToExisting[it.originalId()] == null }) {
            node.addNewChildren(role, -1, expectedNodes.map { it.concept?.let { ConceptReference(it) } })
                .zip(expectedNodes).forEach { (newChild, expected) ->
                    val expectedId = checkNotNull(expected.originalId()) { "Specified node '$expected' has no id" }
                    newChild.setPropertyValue(NodeData.idPropertyKey, expectedId)
                    originalIdToExisting[expectedId] = newChild
                    syncNode(newChild, expected, progressReporter)
                }
            return
        }

        val isOrdered = if (role == null) {
            // TODO Olekz test code
            true
        } else {
            // TODO Olekz optemize
            val concept = node.getConcept()
            if (concept == null) {
                // TODO Olekz test code
                true
            } else {
                // TODO Olekz check if is multiple
                // TODO Olekz bad
                !node.tryResolveChildLink(role)!!.isUnordered
            }
        }

        if (isOrdered) {
            // optimization for when there is no change in the child list
            // size check first to avoiÏord querying the original ID
            if (expectedNodes.size == existingNodes.size && expectedNodes.map { it.originalId() } == existingNodes.map { it.originalId() }) {
                existingNodes.zip(expectedNodes).forEach { syncNode(it.first, it.second, progressReporter) }
                return
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
                        // TODO Olekz Why this?
                        nodesToRemove.remove(existingNode)
                        existingNode
                    }
                } else {
                    nodeAtIndex
                }
                check(childNode.getConceptReference() == expectedConcept) { "Unexpected concept change" }

                syncNode(childNode, expected, progressReporter)
            }

            nodesToRemove += node.getChildren(role).drop(expectedNodes.size)
        } else {
            val expectedNodeByNodeIds = expectedNodes.groupBy { it.originalId() }
            val existingNodesIds = existingNodes.map { it.originalId() }.toSet()
            val nodeIdsToAdd = expectedNodeByNodeIds.keys - existingNodesIds

            nodeIdsToAdd.forEach { nodeIdToAdd ->
                val nodesToAddUnchecked = expectedNodeByNodeIds.getValue(nodeIdToAdd)
                // nodesToAddUnchecked.size will never be 0, because we used groupBy
                if (nodesToAddUnchecked.size > 1) {
                    // TODO Olekz test
                    throw RuntimeException()
                }
                val nodeToAdd = nodesToAddUnchecked[0]
                // TODO Olekz test
                checkNotNull(nodeIdToAdd) { "Specified node '$nodeToAdd' has no id" }
                val expectedConcept = nodeToAdd.concept?.let { s -> ConceptReference(s) }
                val newChild = node.addNewChild(role, -1, expectedConcept)
                newChild.setPropertyValue(NodeData.idPropertyKey, nodeIdToAdd)
                // TODO What happens on move?
                originalIdToExisting[nodeIdToAdd] = newChild
                // TODO Olekz optimze epxort
                check(newChild.getConceptReference() == expectedConcept) { "Unexpected concept change" }
                syncNode(newChild, nodeToAdd, progressReporter)
            }

            nodesToRemove += existingNodes.filterNot { existingNode ->
                expectedNodeByNodeIds.keys.contains(existingNode.originalId())
            }
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

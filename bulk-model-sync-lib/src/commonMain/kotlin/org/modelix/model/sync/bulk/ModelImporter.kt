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

import io.github.oshai.kotlinlogging.KotlinLogging
import org.modelix.kotlin.utils.createMemoryEfficientMap
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.IReplaceableNode
import org.modelix.model.api.SerializedNodeReference
import org.modelix.model.api.getDescendants
import org.modelix.model.api.isChildRoleOrdered
import org.modelix.model.api.remove
import org.modelix.model.api.resolveInCurrentContext
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import kotlin.jvm.JvmName

private val LOG = io.github.oshai.kotlinlogging.KotlinLogging.logger { }

/**
 * A ModelImporter updates an existing [INode] and its subtree based on a [ModelData] specification.
 *
 * The import is incremental.
 * Instead of simply overwriting the existing model, only a minimal amount of operations is used.
 *
 * Properties, references, and child links are synchronized for this node and all of its (in-)direct children.
 *
 * Changes to the behaviour of this class should also reflected in [ModelImporter].
 *
 * @param root the root node to be updated
 * @param continueOnError if true, ignore exceptions and continue.
 *        Enabling this might lead to inconsistent models.
 * @param childFilter filter that is applied to all children of a parent.
 *        If the filter evaluates to true, the node is included.
 */
class ModelImporter(
    private val root: INode,
    private val continueOnError: Boolean,
    private val childFilter: (INode) -> Boolean = { true },
) {
    // We have seen imports where the `originalIdToExisting` had a dozen ten million entries.
    // Therefore, choose a map with is optimized for memory usage.
    // For the same reason store `INodeReference`s instead of `INode`s.
    // In a few cases, where we need the `INode` we can resolve it.
    private val originalIdToExisting by lazy(::buildExistingIndex)

    // Use`INode` instead of `INodeReference` in `postponedReferences` and `nodesToRemove`
    // because we know that we will always need the `INode`s in those cases.
    // Those cases are deleting nodes and adding references to nodes.
    private val postponedReferences = mutableListOf<PostponedReference>()
    private val nodesToRemove = HashSet<INode>()
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

    private data class PostponedReference(
        val expectedTargetId: String,
        val node: INode,
        val role: String,
    )

    private fun PostponedReference.setPostponedReference() {
        val expectedRefTarget = originalIdToExisting[expectedTargetId]
        if (expectedRefTarget != null) {
            node.setReferenceTarget(role, expectedRefTarget)
            return
        }
        // Ensure unresolvable reference wasn't stored before
        if (node.getReferenceTargetRef(role)?.serialize() != expectedTargetId) {
            // The target node is not part of the model. Assuming it exists in some other model we can
            // store the reference and try to resolve it dynamically on access.
            node.setReferenceTarget(role, SerializedNodeReference(expectedTargetId))
        }
    }

    /**
     * Incrementally updates this importers root based on the provided [ModelData] specification.
     *
     * @param data the model specification
     */
    @JvmName("importData")
    fun import(data: ModelData) {
        importIntoNodes(sequenceOf(ExistingAndExpectedNode(root, data)))
    }

    /**
     * Incrementally updates existing children of the given with specified data.
     *
     * @param nodeCombinationsToImport Combinations of an old existing child and the new expected data.
     * The combinations are consumed lazily.
     * Callers can use this to load expected data on demand.
     */
    fun importIntoNodes(nodeCombinationsToImport: Sequence<ExistingAndExpectedNode>) {
        logger.info { "Building indices for import..." }
        postponedReferences.clear()
        nodesToRemove.clear()

        nodeCombinationsToImport.forEach { nodeCombination ->
            importIntoNode(nodeCombination.expectedNodeData, nodeCombination.existingNode)
        }

        logger.info { "Synchronizing references..." }
        postponedReferences.forEach { it.setPostponedReference() }

        logger.info { "Removing extra nodes..." }
        nodesToRemove.forEach {
            doAndPotentiallyContinueOnErrors {
                if (it.isValid) { // if it's invalid then it's already removed
                    try {
                        it.remove()
                    } catch (ex: UnsupportedOperationException) {
                        // it might be read-only
                        LOG.warn(ex) { "Cannot delete node $it" }
                    }
                }
            }
        }

        logger.info { "Synchronization finished." }
    }

    private fun importIntoNode(expectedNodeData: ModelData, existingNode: INode = root) {
        INodeResolutionScope.runWithAdditionalScope(existingNode.getArea()) {
            logImportSize(expectedNodeData.root, logger)
            logger.info { "Building indices for nodes import..." }
            currentNodeProgress = 0
            val numExpectedNodes = countExpectedNodes(expectedNodeData.root)
            val progressReporter = ProgressReporter(numExpectedNodes.toULong(), logger)

            logger.info { "Importing nodes..." }
            expectedNodeData.root.originalId()?.let { originalIdToExisting[it] = existingNode.reference }
            syncNode(existingNode, expectedNodeData.root, progressReporter)
        }
    }

    private fun countExpectedNodes(data: NodeData): Int =
        1 + data.children.sumOf { countExpectedNodes(it) }

    private fun syncNode(node: INode, data: NodeData, progressReporter: ProgressReporter) {
        currentNodeProgress += 1
        progressReporter.step(currentNodeProgress.toULong())
        doAndPotentiallyContinueOnErrors {
            val conceptCorrectedNode = checkAndHandleConceptChange(node, data)
            syncProperties(conceptCorrectedNode, data)
            syncChildren(conceptCorrectedNode, data, progressReporter)
            syncReferences(conceptCorrectedNode, data)
        }
    }

    private fun syncChildren(existingParent: INode, expectedParent: NodeData, progressReporter: ProgressReporter) {
        val allRoles = (expectedParent.children.map { it.role } + existingParent.allChildren.map { it.roleInParent }).distinct()
        for (role in allRoles) {
            val expectedNodes = expectedParent.children.filter { it.role == role }
            val existingNodes = existingParent.getChildren(role).filter(childFilter).toList()
            val allExpectedNodesDoNotExist by lazy {
                expectedNodes.all { expectedNode ->
                    val originalId = expectedNode.originalId()
                    checkNotNull(originalId) { "Specified node '$expectedNode' has no ID." }
                    originalIdToExisting[originalId] == null
                }
            }

            // optimization that uses the bulk operation .addNewChildren
            if (existingNodes.isEmpty() && allExpectedNodesDoNotExist) {
                existingParent.addNewChildren(role, -1, expectedNodes.map { it.concept?.let { ConceptReference(it) } })
                    .zip(expectedNodes)
                    .forEach { (newChild, expected) ->
                        val expectedId = expected.originalId()
                        checkNotNull(expectedId) { "Specified node '$expected' has no ID." }
                        if (newChild.originalId() == null) {
                            newChild.setPropertyValue(NodeData.ID_PROPERTY_KEY, expectedId)
                        }
                        originalIdToExisting[newChild.originalId() ?: expectedId] = newChild.reference
                        syncNode(newChild, expected, progressReporter)
                    }
                continue
            }

            // optimization for when there is no change in the child list
            // size check first to avoid querying the original ID
            if (expectedNodes.size == existingNodes.size && expectedNodes.map { it.originalId() } == existingNodes.map { it.originalId() }) {
                existingNodes.zip(expectedNodes).forEach {
                    syncNode(it.first, it.second, progressReporter)
                }
                continue
            }

            val isOrdered = existingParent.isChildRoleOrdered(role)

            val newlyCreatedIds = mutableSetOf<String>()

            expectedNodes.forEachIndexed { indexInImport, expected ->
                val existingChildren = existingParent.getChildren(role).toList()
                val expectedId = checkNotNull(expected.originalId()) { "Specified node '$expected' has no id" }
                // newIndex is the index on which to import the expected child.
                // It might be -1 if the child does not exist and should be added at the end.
                val newIndex = if (isOrdered) {
                    indexInImport
                } else {
                    // The `existingChildren` are only searched once for the expected element before changing.
                    // Therefore, indexing existing children will not be more efficient than iterating once.
                    // (For the moment, this is fine because as we expect unordered children to be the exception,
                    // Reusable indexing would be possible if we switch from
                    // a depth-first import to a breadth-first import.)
                    existingChildren
                        .indexOfFirst { existingChild -> existingChild.originalId() == expected.originalId() }
                }
                // existingChildren.getOrNull handles `-1` as needed by returning `null`.
                val nodeAtIndex = existingChildren.getOrNull(newIndex)
                val expectedConcept = expected.concept?.let { s -> ConceptReference(s) }
                val childNode = if (nodeAtIndex?.originalId() != expectedId) {
                    val existingNodeReference = originalIdToExisting[expectedId]
                    if (existingNodeReference == null) {
                        val newChild = existingParent.addNewChild(role, newIndex, expectedConcept)
                        if (newChild.originalId() == null) {
                            newChild.setPropertyValue(NodeData.idPropertyKey, expectedId)
                        }
                        newChild.originalId()?.let { newlyCreatedIds.add(it) }
                        originalIdToExisting[expectedId] = newChild.reference
                        newChild
                    } else {
                        val existingNode = existingNodeReference.resolveInCurrentContext()
                        checkNotNull(existingNode) {
                            // This reference should always be resolvable because the node existed or was created before.
                            "Could not resolve $existingNodeReference."
                        }
                        // The existing child node is not only moved to a new index,
                        // it is potentially moved to a new parent and role.
                        existingParent.moveChild(role, newIndex, existingNode)
                        // If the old parent and old role synchronized before the move operation,
                        // the existing child node would have been marked as to be deleted.
                        // Now that it is used, it should not be deleted.
                        nodesToRemove.remove(existingNode)
                        existingNode
                    }
                } else {
                    nodeAtIndex
                }
                syncNode(childNode, expected, progressReporter)
            }

            val expectedNodesIds = expectedNodes.map(NodeData::originalId).toSet()
            // Do not use existingNodes, but call node.getChildren(role) because
            // the recursive synchronization in the meantime already removed some nodes from node.getChildren(role).
            nodesToRemove += existingParent.getChildren(role).filterNot { existingNode ->
                val id = existingNode.originalId()
                expectedNodesIds.contains(id) || newlyCreatedIds.contains(id)
            }
        }
    }

    private fun checkAndHandleConceptChange(existingNode: INode, expectedNode: NodeData): INode {
        val newConcept = expectedNode.concept?.let { ConceptReference(it) }
        if (existingNode.getConceptReference() == newConcept) {
            return existingNode
        }
        require(existingNode is IReplaceableNode) { "Concept has changed but node is not replaceable. node=$existingNode" }
        return existingNode.replaceNode(newConcept)
    }

    private fun buildExistingIndex(): MutableMap<String, INodeReference> {
        val localOriginalIdToExisting = createMemoryEfficientMap<String, INodeReference>()
        root.getDescendants(true).forEach { node ->
            node.originalId()?.let { localOriginalIdToExisting[it] = node.reference }
        }
        return localOriginalIdToExisting
    }

    private fun syncProperties(node: INode, nodeData: NodeData) {
        if (node.originalId() == null) {
            node.setPropertyValue(NodeData.idPropertyKey, nodeData.originalId())
        }

        nodeData.properties.forEach {
            if (node.getPropertyValue(it.key) != it.value) {
                node.setPropertyValue(it.key, it.value)
            }
        }

        val toBeRemoved = node.getPropertyRoles().toSet()
            .subtract(nodeData.properties.keys)
            .filter { it != NodeData.idPropertyKey && node.getPropertyValue(it) != null }
        toBeRemoved.forEach { node.setPropertyValue(it, null) }
    }

    private fun syncReferences(node: INode, nodeData: NodeData) {
        nodeData.references.forEach { (referenceRole, expectedTargetId) ->
            if (referenceNeedsUpdate(node, referenceRole, expectedTargetId)) {
                val expectedTarget = originalIdToExisting[expectedTargetId]
                if (expectedTarget == null) {
                    postponedReferences += PostponedReference(expectedTargetId, node, referenceRole)
                } else {
                    node.setReferenceTarget(referenceRole, expectedTarget)
                }
            }
        }
        val toBeRemoved = node.getReferenceRoles().toSet() - nodeData.references.keys
        toBeRemoved.forEach {
            node.setReferenceTarget(it, null as INodeReference?)
        }
    }

    private fun referenceNeedsUpdate(node: INode, referenceRole: String, expectedTargetRef: String): Boolean {
        val actualTarget = node.getReferenceTarget(referenceRole)
            ?: return true // previously unresolvable reference might become resolvable e.g., when a referenced node was added
        val actualTargetRef = actualTarget.originalId() ?: node.getReferenceTargetRef(referenceRole)?.serialize()

        return actualTargetRef != expectedTargetRef
    }
}

internal fun INode.originalId(): String? {
    return this.getOriginalReference()
}

internal fun NodeData.originalId(): String? {
    return properties[NodeData.ID_PROPERTY_KEY] ?: id
}

data class ExistingAndExpectedNode(
    val existingNode: INode,
    val expectedNodeData: ModelData,
)

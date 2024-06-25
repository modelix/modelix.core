/*
 * Copyright (c) 2024.
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
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.IReplaceableNode
import org.modelix.model.api.IRole
import org.modelix.model.api.isChildRoleOrdered
import org.modelix.model.api.remove
import org.modelix.model.data.NodeData

/**
 * Similar to [ModelImporter], but the input is two [INode] instances instead of [INode] and [NodeData].
 *
 * Changes to the behaviour of this class should also reflected in [ModelImporter].
 *
 * @param filter determines which nodes need synchronization.
 *          Nodes that do not match the filter are skipped and will remain unchanged.
 * @param sourceRoot root of the tree containing the expected nodes
 * @param targetRoot root of the tree that needs changes
 * @param nodeAssociation mapping between source and target nodes, that is used for internal optimizations
 */
class ModelSynchronizer(
    val filter: IFilter,
    val sourceRoot: INode,
    val targetRoot: INode,
    val nodeAssociation: INodeAssociation,
) {
    private val nodesToRemove: MutableSet<INode> = HashSet()
    private val pendingReferences: MutableList<PendingReference> = ArrayList()
    private val logger = KotlinLogging.logger {}

    fun synchronize() {
        logger.info { "Synchronizing nodes..." }
        synchronizeNode(sourceRoot, targetRoot)
        logger.info { "Synchronizing pending references..." }
        pendingReferences.forEach { it.trySyncReference() }
        logger.info { "Removing extra nodes..." }
        nodesToRemove.filter { it.isValid }.forEach { it.remove() }
        logger.info { "Synchronization finished." }
    }

    private fun synchronizeNode(sourceNode: INode, targetNode: INode) {
        nodeAssociation.associate(sourceNode, targetNode)
        if (filter.needsSynchronization(sourceNode)) {
            logger.info { "Synchronizing changed node. sourceNode = $sourceNode" }
            synchronizeProperties(sourceNode, targetNode)
            synchronizeReferences(sourceNode, targetNode)

            val sourceConcept = sourceNode.getConceptReference()
            val targetConcept = targetNode.getConceptReference()

            val conceptCorrectedTargetNode = if (sourceConcept != targetConcept && targetNode is IReplaceableNode) {
                targetNode.replaceNode(sourceConcept?.getUID()?.let { ConceptReference(it) })
            } else {
                targetNode
            }

            syncChildren(sourceNode, conceptCorrectedTargetNode)
        } else if (filter.needsDescentIntoSubtree(sourceNode)) {
            for (sourceChild in sourceNode.allChildren) {
                val targetChild = nodeAssociation.resolveTarget(sourceChild) ?: error("Expected target node was not found. sourceChild=$sourceChild")
                synchronizeNode(sourceChild, targetChild)
            }
        } else {
            logger.info { "Skipping subtree due to filter. root = $sourceNode" }
        }
    }

    private fun synchronizeReferences(
        sourceNode: INode,
        targetNode: INode,
    ) {
        iterateMergedRoles(sourceNode.getReferenceLinks(), targetNode.getReferenceLinks()) { role ->
            val pendingReference = PendingReference(sourceNode, targetNode, role)

            // If the reference target already exist we can synchronize it immediately and save memory between the
            // two synchronization phases.
            if (!pendingReference.trySyncReference()) {
                pendingReferences += pendingReference
            }
        }
    }

    private fun synchronizeProperties(
        sourceNode: INode,
        targetNode: INode,
    ) {
        iterateMergedRoles(sourceNode.getPropertyLinks(), targetNode.getPropertyLinks()) { role ->
            val oldValue = targetNode.getPropertyValue(role.preferTarget())
            val newValue = sourceNode.getPropertyValue(role.preferSource())
            if (oldValue != newValue) {
                targetNode.setPropertyValue(role.preferTarget(), newValue)
            }
        }
    }

    private fun syncChildren(sourceParent: INode, targetParent: INode) {
        val allRoles = (sourceParent.allChildren.map { it.roleInParent } + targetParent.allChildren.map { it.roleInParent }).distinct()
        for (role in allRoles) {
            val sourceNodes = sourceParent.getChildren(role).toList()
            val targetNodes = targetParent.getChildren(role).toList()

            val allExpectedNodesDoNotExist by lazy {
                sourceNodes.all { sourceNode ->
                    val originalId = sourceNode.originalId()
                    checkNotNull(originalId) { "Specified node '$sourceNode' has no ID." }
                    nodeAssociation.resolveTarget(sourceNode) == null
                }
            }

            // optimization that uses the bulk operation .addNewChildren
            if (targetNodes.isEmpty() && allExpectedNodesDoNotExist) {
                targetParent.addNewChildren(role, -1, sourceNodes.map { it.getConceptReference() })
                    .zip(sourceNodes)
                    .forEach { (newChild, sourceChild) ->
                        val expectedId = sourceChild.originalId()
                        checkNotNull(expectedId) { "Specified node '$sourceChild' has no ID." }
                        nodeAssociation.associate(sourceChild, newChild)
                        synchronizeNode(sourceChild, newChild)
                    }
                continue
            }

            // optimization for when there is no change in the child list
            // size check first to avoid querying the original ID
            if (sourceNodes.size == targetNodes.size && sourceNodes.map { it.originalId() } == targetNodes.map { it.originalId() }) {
                sourceNodes.zip(targetNodes).forEach { synchronizeNode(it.first, it.second) }
                continue
            }

            val isOrdered = targetParent.isChildRoleOrdered(role)

            val newlyCreatedIds = mutableSetOf<String>()

            sourceNodes.forEachIndexed { indexInImport, expected ->
                val existingChildren = targetParent.getChildren(role).toList()
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
                val expectedConcept = expected.getConceptReference()
                val childNode = if (nodeAtIndex?.originalId() != expectedId) {
                    val existingNode = nodeAssociation.resolveTarget(expected)
                    if (existingNode == null) {
                        val newChild = targetParent.addNewChild(role, newIndex, expectedConcept)
                        if (newChild.originalId() == null) {
                            newChild.setPropertyValue(NodeData.idPropertyKey, expectedId)
                        }
                        newChild.originalId()?.let { newlyCreatedIds.add(it) }
                        nodeAssociation.associate(expected, newChild)
                        newChild
                    } else {
                        // The existing child node is not only moved to a new index,
                        // it is potentially moved to a new parent and role.
                        targetParent.moveChild(role, newIndex, existingNode)
                        // If the old parent and old role synchronized before the move operation,
                        // the existing child node would have been marked as to be deleted.
                        // Now that it is used, it should not be deleted.
                        nodesToRemove.remove(existingNode)
                        existingNode
                    }
                } else {
                    nodeAtIndex
                }

                synchronizeNode(expected, childNode)
            }

            val expectedNodesIds = sourceNodes.map { it.originalId() }.toSet()
            // Do not use existingNodes, but call node.getChildren(role) because
            // the recursive synchronization in the meantime already removed some nodes from node.getChildren(role).
            nodesToRemove += targetParent.getChildren(role).filterNot { existingNode ->
                val id = existingNode.originalId()
                expectedNodesIds.contains(id) || newlyCreatedIds.contains(id)
            }
        }
    }

    inner class PendingReference(val sourceNode: INode, val targetNode: INode, val role: MergedRole<IReferenceLink>) {
        fun trySyncReference(): Boolean {
            val expectedRef = sourceNode.getReferenceTargetRef(role.preferSource())
            if (expectedRef == null) {
                targetNode.setReferenceTarget(role.preferTarget(), null as INodeReference?)
                return true
            }
            val actualRef = targetNode.getReferenceTargetRef(role.preferTarget())

            // Some reference targets may be excluded from the sync,
            // in that case a serialized reference is stored and no lookup of the target is required.
            if (actualRef?.serialize() == expectedRef.serialize()) {
                // already up to date
                return true
            }

            val referenceTargetInSource = sourceNode.getReferenceTarget(role.preferSource())
            checkNotNull(referenceTargetInSource) { "Failed to resolve $expectedRef referenced by $sourceNode.${role.preferSource()}" }

            val referenceTargetInTarget = nodeAssociation.resolveTarget(referenceTargetInSource)
                ?: return false // Target cannot be resolved right now but might become resolvable later.

            if (referenceTargetInTarget.reference.serialize() != actualRef?.serialize()) {
                targetNode.setReferenceTarget(role.preferTarget(), referenceTargetInTarget)
            }
            return true
        }
    }

    private fun <T : IRole> iterateMergedRoles(
        sourceRoles: Iterable<T>,
        targetRoles: Iterable<T>,
        body: (role: MergedRole<T>) -> Unit,
    ) = iterateMergedRoles(sourceRoles.asSequence(), targetRoles.asSequence(), body)

    private fun <T : IRole> iterateMergedRoles(
        sourceRoles: Sequence<T>,
        targetRoles: Sequence<T>,
        body: (role: MergedRole<T>) -> Unit,
    ) {
        val sourceRolesMap = sourceRoles.filter { it.getUID() != NodeData.ID_PROPERTY_KEY }.associateBy { it.getUID() }
        val targetRolesMap = targetRoles.associateBy { it.getUID() }
        val roleUIDs = (sourceRolesMap.keys + targetRolesMap.keys).toSet()
        for (roleUID in roleUIDs) {
            val sourceRole = sourceRolesMap[roleUID]
            val targetRole = targetRolesMap[roleUID]
            body(MergedRole(sourceRole, targetRole))
        }
    }

    class MergedRole<E : IRole>(
        private val source: E?,
        private val target: E?,
    ) {
        fun preferTarget(): E = (target ?: source)!!
        fun preferSource() = (source ?: target)!!
    }

    /**
     * Determines, which nodes need synchronization and which can be skipped.
     *
     * It is valid for [needsDescentIntoSubtree] and [needsSynchronization] to return true for the same node.
     */
    interface IFilter {
        /**
         * Checks if a subtree needs synchronization.
         *
         * @param subtreeRoot root of the subtree to be checked
         * @return true iff the subtree must not be skipped
         */
        fun needsDescentIntoSubtree(subtreeRoot: INode): Boolean

        /**
         * Checks if a single node needs synchronization.
         *
         * @param node node to be checked
         * @return true iff the node must not be skipped
         */
        fun needsSynchronization(node: INode): Boolean
    }
}

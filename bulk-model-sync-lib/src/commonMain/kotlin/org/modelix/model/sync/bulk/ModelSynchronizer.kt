package org.modelix.model.sync.bulk

import mu.KotlinLogging
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INode
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getOriginalOrCurrentReference
import org.modelix.model.api.getOriginalReference
import org.modelix.model.api.isChildRoleOrdered
import org.modelix.model.api.matches
import org.modelix.model.api.mergeWith
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
    val sourceRoot: IReadableNode,
    val targetRoot: IWritableNode,
    val nodeAssociation: INodeAssociation,
) {
    private val nodesToRemove: MutableSet<IWritableNode> = HashSet()
    private val pendingReferences: MutableList<PendingReference> = ArrayList()
    private val logger = KotlinLogging.logger {}

    fun synchronize() {
        synchronize(listOf(sourceRoot), listOf(targetRoot))
    }

    fun synchronize(sourceNodes: List<IReadableNode>, targetNodes: List<IWritableNode>) {
        logger.info { "Synchronizing nodes..." }
        for ((sourceNode, targetNode) in sourceNodes.zip(targetNodes)) {
            synchronizeNode(sourceNode, targetNode)
        }
        synchronizeNode(sourceRoot, targetRoot)
        logger.info { "Synchronizing pending references..." }
        pendingReferences.forEach {
            if (!it.trySyncReference()) {
                it.copyTargetRef()
            }
        }
        logger.info { "Removing extra nodes..." }
        nodesToRemove.filter { it.isValid() }.forEach { it.remove() }
        logger.info { "Synchronization finished." }
    }

    private fun synchronizeNode(sourceNode: IReadableNode, targetNode: IWritableNode) {
        nodeAssociation.associate(sourceNode, targetNode)
        if (filter.needsSynchronization(sourceNode)) {
            logger.info { "Synchronizing changed node. sourceNode = $sourceNode" }
            synchronizeProperties(sourceNode, targetNode)
            synchronizeReferences(sourceNode, targetNode)

            val sourceConcept = sourceNode.getConceptReference()
            val targetConcept = targetNode.getConceptReference()

            val conceptCorrectedTargetNode = if (sourceConcept != targetConcept) {
                targetNode.changeConcept(sourceConcept)
            } else {
                targetNode
            }

            syncChildren(sourceNode, conceptCorrectedTargetNode)
        } else if (filter.needsDescentIntoSubtree(sourceNode)) {
            for (sourceChild in sourceNode.getAllChildren()) {
                val targetChild = nodeAssociation.resolveTarget(sourceChild) ?: error("Expected target node was not found. sourceChild=$sourceChild")
                synchronizeNode(sourceChild, targetChild)
            }
        } else {
            logger.info { "Skipping subtree due to filter. root = $sourceNode" }
        }
    }

    private fun synchronizeReferences(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
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
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ) {
        iterateMergedRoles(sourceNode.getPropertyLinks(), targetNode.getPropertyLinks()) { role ->
            val oldValue = targetNode.getPropertyValue(role)
            val newValue = sourceNode.getPropertyValue(role)
            if (oldValue != newValue) {
                targetNode.setPropertyValue(role, newValue)
            }
        }
    }

    private fun getFilteredSourceChildren(parent: IReadableNode, role: IChildLinkReference): List<IReadableNode> {
        return parent.getChildren(role).let { filter.filterSourceChildren(parent, role, it) }
    }

    private fun getFilteredTargetChildren(parent: IWritableNode, role: IChildLinkReference): List<IWritableNode> {
        return parent.getChildren(role).let { filter.filterTargetChildren(parent, role, it) }
    }

    private fun syncChildren(sourceParent: IReadableNode, targetParent: IWritableNode) {
        iterateMergedRoles(
            sourceParent.getAllChildren().map { it.getContainmentLink() }.distinct(),
            targetParent.getAllChildren().map { it.getContainmentLink() }.distinct(),
        ) { role ->
            val sourceNodes = getFilteredSourceChildren(sourceParent, role)
            val targetNodes = getFilteredTargetChildren(targetParent, role)
            val unusedTargetChildren = targetNodes.toMutableSet()

            val allExpectedNodesDoNotExist by lazy {
                sourceNodes.all { sourceNode ->
                    nodeAssociation.resolveTarget(sourceNode) == null
                }
            }

            // optimization that uses the bulk operation .addNewChildren
            if (targetNodes.isEmpty() && allExpectedNodesDoNotExist) {
                targetParent.addNewChildren(role, -1, sourceNodes.map { it.getConceptReference() })
                    .zip(sourceNodes)
                    .forEach { (newChild, sourceChild) ->
                        nodeAssociation.associate(sourceChild, newChild)
                        synchronizeNode(sourceChild, newChild)
                    }
                return@iterateMergedRoles
            }

            // optimization for when there is no change in the child list
            // size check first to avoid querying the original ID
            if (sourceNodes.size == targetNodes.size && sourceNodes.zip(targetNodes).all { nodeAssociation.matches(it.first, it.second) }) {
                sourceNodes.zip(targetNodes).forEach { synchronizeNode(it.first, it.second) }
                return@iterateMergedRoles
            }

            val isOrdered = targetParent.isChildRoleOrdered(role)

            sourceNodes.forEachIndexed { indexInImport, expected ->
                val existingChildren = getFilteredTargetChildren(targetParent, role)
                val expectedId = checkNotNull(expected.originalIdOrFallback()) { "Specified node '$expected' has no id" }
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
                        .indexOfFirst { existingChild -> existingChild.getOriginalOrCurrentReference() == expectedId }
                }
                // existingChildren.getOrNull handles `-1` as needed by returning `null`.
                val nodeAtIndex = existingChildren.getOrNull(newIndex)
                val expectedConcept = expected.getConceptReference()
                val childNode = if (nodeAtIndex?.getOriginalOrCurrentReference() != expectedId) {
                    val existingNode = nodeAssociation.resolveTarget(expected)
                    if (existingNode == null) {
                        val newChild = targetParent.addNewChild(role, newIndex, expectedConcept)
                        nodeAssociation.associate(expected, newChild)
                        newChild
                    } else {
                        // The existing child node is not only moved to a new index,
                        // it is potentially moved to a new parent and role.
                        targetParent.moveChild(role, newIndex, existingNode)
                        // If the old parent and old role synchronized before the move operation,
                        // the existing child node would have been marked as to be deleted.
                        // Now that it is used, it should not be deleted.
                        unusedTargetChildren.remove(existingNode)
                        nodesToRemove.remove(existingNode)
                        existingNode
                    }
                } else {
                    unusedTargetChildren.remove(nodeAtIndex)
                    nodesToRemove.remove(nodeAtIndex)
                    nodeAtIndex
                }

                synchronizeNode(expected, childNode)
            }

            // Do not use existingNodes, but call node.getChildren(role) because
            // the recursive synchronization in the meantime already removed some nodes from node.getChildren(role).
            nodesToRemove += getFilteredTargetChildren(targetParent, role).intersect(unusedTargetChildren)
        }
    }

    inner class PendingReference(val sourceNode: IReadableNode, val targetNode: IWritableNode, val role: IReferenceLinkReference) {
        override fun toString(): String = "${sourceNode.getNodeReference()}[$role] : ${targetNode.getAllReferenceTargetRefs()}"

        fun copyTargetRef() {
            val oldValue = targetNode.getReferenceTargetRef(role)
            val newValue = sourceNode.getReferenceTargetRef(role)
            if (oldValue != newValue) {
                targetNode.setReferenceTargetRef(role, newValue)
            }
        }

        fun trySyncReference(): Boolean {
            val expectedRef = sourceNode.getReferenceTargetRef(role)
            if (expectedRef == null) {
                targetNode.setReferenceTargetRef(role, null)
                return true
            }
            val actualRef = targetNode.getReferenceTargetRef(role)

            val referenceTargetInSource = sourceNode.getReferenceTarget(role) ?: return false

            val referenceTargetInTarget = nodeAssociation.resolveTarget(referenceTargetInSource)
                ?: return false // Target cannot be resolved right now but might become resolvable later.

            if (referenceTargetInTarget.getNodeReference().serialize() != actualRef?.serialize()) {
                targetNode.setReferenceTarget(role, referenceTargetInTarget)
            }
            return true
        }
    }

    private fun <T : IRoleReference> iterateMergedRoles(
        sourceRoles: Iterable<T>,
        targetRoles: Iterable<T>,
        body: (role: T) -> Unit,
    ) = iterateMergedRoles(sourceRoles.asSequence(), targetRoles.asSequence(), body)

    private fun <T : IRoleReference> iterateMergedRoles(
        sourceRoles: Sequence<T>,
        targetRoles: Sequence<T>,
        body: (role: T) -> Unit,
    ) {
        for (role in sourceRoles.mergeWith(targetRoles)) {
            if (role.matches(NodeData.ID_PROPERTY_REF)) continue
            body(role)
        }
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
         * @return true iff for any node in this subtree needsSynchronization returns true
         */
        fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean

        /**
         * Checks if a single node needs synchronization.
         *
         * @param node node to be checked
         * @return true iff the node must not be skipped
         */
        fun needsSynchronization(node: IReadableNode): Boolean

        fun filterSourceChildren(parent: IReadableNode, role: IChildLinkReference, children: List<IReadableNode>): List<IReadableNode> = children

        fun filterTargetChildren(parent: IWritableNode, role: IChildLinkReference, children: List<IWritableNode>): List<IWritableNode> = children
    }
}

private fun INode.originalIdOrFallback(): String? {
    val originalRef = getOriginalReference()
    if (originalRef != null) return originalRef

    if (this is PNodeAdapter) return reference.serialize()
    return null
}

private fun IReadableNode.originalIdOrFallback(): String? {
    val originalRef = getOriginalReference()
    if (originalRef != null) return originalRef

    return getNodeReference().serialize()
}

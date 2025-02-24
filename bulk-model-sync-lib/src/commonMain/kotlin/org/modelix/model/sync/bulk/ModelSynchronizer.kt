package org.modelix.model.sync.bulk

import mu.KLogger
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INode
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NewNodeSpec
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getOriginalOrCurrentReference
import org.modelix.model.api.getOriginalReference
import org.modelix.model.api.isOrdered
import org.modelix.model.api.matches
import org.modelix.model.api.mergeWith
import org.modelix.model.api.remove
import org.modelix.model.api.syncNewChild
import org.modelix.model.api.syncNewChildren
import org.modelix.model.data.NodeData
import org.modelix.model.sync.bulk.ModelSynchronizer.IIncrementalUpdateInformation

private val LOG: KLogger = mu.KotlinLogging.logger { }

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
    val filter: IIncrementalUpdateInformation = FullSyncFilter(),
    val sourceRoot: IReadableNode,
    val targetRoot: IWritableNode,
    val nodeAssociation: INodeAssociation,
    val sourceMask: IModelMask = UnfilteredModelMask(),
    val targetMask: IModelMask = UnfilteredModelMask(),
    private val onException: ((Throwable) -> Unit)? = {},
) {
    private val nodesToRemove: MutableSet<IWritableNode> = HashSet()
    private val pendingReferences: MutableList<PendingReference> = ArrayList()

    private fun <R> runSafe(body: () -> R): Result<R> {
        return if (onException == null) {
            Result.success(body())
        } else {
            runCatching(body)
                .onFailure { LOG.error(it) { "Ignoring exception during synchronization" } }
                .onFailure { onException(it) }
        }
    }

    fun synchronize() {
        synchronize(listOf(sourceRoot), listOf(targetRoot))
    }

    fun synchronize(sourceNodes: List<IReadableNode>, targetNodes: List<IWritableNode>) {
        LOG.debug { "Synchronizing nodes..." }
        for ((sourceNode, targetNode) in sourceNodes.zip(targetNodes)) {
            synchronizeNode(sourceNode, targetNode, false)
        }
        LOG.debug { "Synchronizing pending references..." }
        pendingReferences.forEach {
            runSafe {
                if (!it.trySyncReference()) {
                    it.copyTargetRef()
                }
            }
        }
        LOG.debug { "Removing extra nodes..." }
        nodesToRemove.filter { it.isValid() }.forEach { it.remove() }
        LOG.debug { "Synchronization finished." }
    }

    private fun synchronizeNode(sourceNode: IReadableNode, targetNode: IWritableNode, forceSyncDescendants: Boolean) {
        nodeAssociation.associate(sourceNode, targetNode)
        if (forceSyncDescendants || filter.needsSynchronization(sourceNode)) {
            LOG.trace { "Synchronizing changed node. sourceNode = $sourceNode" }
            runSafe { synchronizeProperties(sourceNode, targetNode) }
            runSafe { synchronizeReferences(sourceNode, targetNode) }

            val conceptCorrectedTargetNode = runSafe {
                val sourceConcept = sourceNode.getConceptReference()
                val targetConcept = targetNode.getConceptReference()

                if (sourceConcept != targetConcept) {
                    targetNode.changeConcept(sourceConcept)
                } else {
                    targetNode
                }
            }.getOrDefault(targetNode)

            runSafe {
                syncChildren(sourceNode, conceptCorrectedTargetNode, forceSyncDescendants)
            }
        } else if (filter.needsDescentIntoSubtree(sourceNode)) {
            for (sourceChild in sourceMask.filterChildren(sourceNode, sourceNode.getAllChildren())) {
                runSafe {
                    val targetChild = nodeAssociation.resolveTarget(sourceChild)
                        ?: error("Expected target node was not found. sourceChild=${sourceChild.getNodeReference()}, originalId=${sourceChild.getOriginalReference()}")
                    synchronizeNode(sourceChild, targetChild, forceSyncDescendants)
                }
            }
        } else {
            LOG.trace { "Skipping subtree due to filter. root = $sourceNode" }
        }
    }

    private fun synchronizeReferences(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ) {
        iterateMergedRoles(sourceNode.getReferenceLinks(), targetNode.getReferenceLinks()) { role ->
            runSafe {
                val pendingReference = PendingReference(sourceNode, targetNode, role)

                // If the reference target already exist we can synchronize it immediately and save memory between the
                // two synchronization phases.
                if (!pendingReference.trySyncReference()) {
                    pendingReferences += pendingReference
                }
            }
        }
    }

    private fun synchronizeProperties(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ) {
        iterateMergedRoles(sourceNode.getPropertyLinks(), targetNode.getPropertyLinks()) { role ->
            runSafe {
                val oldValue = targetNode.getPropertyValue(role)
                val newValue = sourceNode.getPropertyValue(role)
                if (oldValue != newValue) {
                    targetNode.setPropertyValue(role, newValue)
                }
            }
        }
    }

    private fun getFilteredSourceChildren(parent: IReadableNode, role: IChildLinkReference): List<IReadableNode> {
        return parent.getChildren(role).let { sourceMask.filterChildren(parent, role, it) }
    }

    private fun getFilteredTargetChildren(parent: IWritableNode, role: IChildLinkReference): List<IWritableNode> {
        return parent.getChildren(role).let { targetMask.filterChildren(parent, role, it) }
    }

    private fun syncChildren(sourceParent: IReadableNode, targetParent: IWritableNode, forceSyncDescendants: Boolean) {
        iterateMergedRoles(
            sourceParent.getAllChildren().map { it.getContainmentLink() }.distinct(),
            targetParent.getAllChildren().map { it.getContainmentLink() }.distinct(),
        ) { role ->
            runSafe {
                syncChildrenInRole(sourceParent, role, targetParent, forceSyncDescendants)
            }
        }
        // tryFixCrossRoleOrder(sourceParent, targetParent)
    }

    private fun syncChildrenInRole(
        sourceParent: IReadableNode,
        role: IChildLinkReference,
        targetParent: IWritableNode,
        forceSyncDescendants: Boolean,
    ) {
        val sourceNodes = getFilteredSourceChildren(sourceParent, role)
        val targetNodes = getFilteredTargetChildren(targetParent, role)
        val unusedTargetChildren = targetNodes.toMutableSet()

        val allExpectedNodesDoNotExist by lazy {
            sourceNodes.all { sourceNode ->
                nodeAssociation.resolveTarget(sourceNode) == null
            }
        }

        // optimization that uses the bulk operation .syncNewChildren
        if (targetNodes.isEmpty() && allExpectedNodesDoNotExist) {
            targetParent.syncNewChildren(role, -1, sourceNodes.map { NewNodeSpec(it) })
                .zip(sourceNodes)
                .forEach { (newChild, sourceChild) ->
                    nodeAssociation.associate(sourceChild, newChild)
                    synchronizeNode(sourceChild, newChild, forceSyncDescendants = true)
                }
            return
        }

        // optimization for when there is no change in the child list
        // size check first to avoid querying the original ID
        if (sourceNodes.size == targetNodes.size && sourceNodes.zip(targetNodes)
                .all { nodeAssociation.matches(it.first, it.second) }
        ) {
            sourceNodes.zip(targetNodes).forEach { synchronizeNode(it.first, it.second, forceSyncDescendants) }
            return
        }

        val isOrdered = targetParent.isOrdered(role)

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
            var isNewChild = false
            val childNode = if (nodeAtIndex?.getOriginalOrCurrentReference() != expectedId) {
                val existingNode = nodeAssociation.resolveTarget(expected)
                if (existingNode == null) {
                    val newChild = targetParent.syncNewChild(role, newIndex, NewNodeSpec(expected))
                    nodeAssociation.associate(expected, newChild)
                    isNewChild = true
                    newChild
                } else {
                    // The existing child node is not only moved to a new index,
                    // it is potentially moved to a new parent and role.
                    if (existingNode.getParent() != targetParent ||
                        !existingNode.getContainmentLink().matches(role) ||
                        targetParent.isOrdered(role)
                    ) {
                        targetParent.moveChild(role, newIndex, existingNode)
                    }
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

            synchronizeNode(expected, childNode, forceSyncDescendants || isNewChild)
        }

        // Do not use existingNodes, but call node.getChildren(role) because
        // the recursive synchronization in the meantime already removed some nodes from node.getChildren(role).
        nodesToRemove += getFilteredTargetChildren(targetParent, role).intersect(unusedTargetChildren)
    }

    /**
     * In MPS and also in Modelix nodes internally are stored in a single list that is filtered when a specific role is
     * accessed. The information about this internal order is visible when using getAllChildren().
     * MPS uses this order in its "Generic comments" feature
     * (see https://www.jetbrains.com/help/mps/generic-placeholders-and-generic-comments.html).
     * Even though it is not semantically relevant, it will still be visible in the editor if we don't preserve that
     * order.
     */
    private fun tryFixCrossRoleOrder(sourceParent: IReadableNode, targetParent: IWritableNode) {
        val sourceChildren = sourceParent.getAllChildren()
        val actualTargetChildren = targetParent.getAllChildren()
        val expectedTargetChildren = sourceChildren.map { nodeAssociation.resolveTarget(it) ?: return }
        if (actualTargetChildren == expectedTargetChildren) return

        for (targetChild in expectedTargetChildren) {
            try {
                targetParent.moveChild(targetChild.getContainmentLink(), -1, targetChild)
            } catch (ex: UnsupportedOperationException) {
                return
            }
        }
    }

    inner class PendingReference(val sourceNode: IReadableNode, val targetNode: IWritableNode, val role: IReferenceLinkReference) {
        override fun toString(): String = "${sourceNode.getNodeReference()}[$role] : ${targetNode.getAllReferenceTargetRefs()}"

        fun copyTargetRef() {
            val oldValue = targetNode.getReferenceTargetRef(role)
            val newValue = sourceNode.getReferenceTargetRef(role)
            if (oldValue?.serialize() != newValue?.serialize()) {
                targetNode.setReferenceTargetRef(role, newValue)
            }
        }

        fun trySyncReference(): Boolean {
            return runSafe {
                val expectedRef = sourceNode.getReferenceTargetRef(role)
                if (expectedRef == null) {
                    targetNode.setReferenceTargetRef(role, null)
                    return@runSafe true
                }
                val actualRef = targetNode.getReferenceTargetRef(role)

                val referenceTargetInSource = sourceNode.getReferenceTarget(role) ?: return@runSafe false

                val referenceTargetInTarget = nodeAssociation.resolveTarget(referenceTargetInSource)
                    ?: return@runSafe false // Target cannot be resolved right now but might become resolvable later.

                if (referenceTargetInTarget.getNodeReference().serialize() != actualRef?.serialize()) {
                    targetNode.setReferenceTarget(role, referenceTargetInTarget)
                }
                return@runSafe true
            }.getOrDefault(false)
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
    interface IIncrementalUpdateInformation {
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
    }
}

class FullSyncFilter : IIncrementalUpdateInformation {
    override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean = true
    override fun needsSynchronization(node: IReadableNode): Boolean = true
}

fun IIncrementalUpdateInformation.and(other: IIncrementalUpdateInformation): IIncrementalUpdateInformation = AndFilter(this, other)

class AndFilter(val filter1: IIncrementalUpdateInformation, val filter2: IIncrementalUpdateInformation) : IIncrementalUpdateInformation {
    override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean {
        return filter1.needsDescentIntoSubtree(subtreeRoot) && filter2.needsDescentIntoSubtree(subtreeRoot)
    }

    override fun needsSynchronization(node: IReadableNode): Boolean {
        return filter1.needsSynchronization(node) && filter2.needsSynchronization(node)
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

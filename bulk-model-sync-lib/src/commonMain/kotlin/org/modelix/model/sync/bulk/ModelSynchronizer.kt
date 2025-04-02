package org.modelix.model.sync.bulk

import mu.KLogger
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INode
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NewNodeSpec
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
    private val syncStack = ArrayList<IReadableNode>()

    fun getCurrentSyncStack(): List<IReadableNode> = syncStack.toList()

    private inline fun <R> withPushSyncStack(element: IReadableNode, body: () -> R): R {
        syncStack.add(element)
        try {
            return body()
        } finally {
            syncStack.removeLast()
        }
    }

    private fun <R> runSafe(body: () -> R): Result<R> {
        return if (onException == null) {
            Result.success(body())
        } else {
            runCatching(body)
                .onFailure { onException(it) }
                .onFailure { LOG.error(it) { "Ignoring exception during synchronization" } }
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
        nodesToRemove.forEach {
            runSafe {
                if (it.isValid()) {
                    it.remove()
                }
            }
        }
        LOG.debug { "Synchronization finished." }
    }

    private fun synchronizeNode(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
        forceSyncDescendants: Boolean,
    ): Unit = withPushSyncStack(sourceNode) {
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
            for (sourceChild in sourceMask.getFilteredChildren(sourceNode)) {
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
            sourceMask.getFilteredChildren(sourceParent).map { it.getContainmentLink() }.distinct(),
            targetMask.getFilteredChildren(targetParent).map { it.getContainmentLink() }.distinct(),
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
        var forceSyncDescendants = forceSyncDescendants
        var associatedChildren = associateChildren(sourceParent, targetParent, role)

        // optimization that uses the bulk operation .syncNewChildren
        if (associatedChildren.all { it.hasToCreate() }) {
            forceSyncDescendants = true
            runSafe {
                val newChildren = targetParent
                    .syncNewChildren(role, -1, associatedChildren.map { NewNodeSpec(it.getSource()) })
                newChildren
                    .zip(associatedChildren.map { it.getSource() })
                    .forEach { (newChild, sourceChild) ->
                        runSafe {
                            nodeAssociation.associate(sourceChild, newChild)
                            synchronizeNode(sourceChild, newChild, forceSyncDescendants = forceSyncDescendants)
                        }
                    }
            }.onSuccess {
                return
            }.onFailure {
                // Some children may have been created successfully.
                associatedChildren = associateChildren(sourceParent, targetParent, role)
                // Continue with trying to sync the remaining ones individually.
            }
        }

        val isOrdered = targetParent.isOrdered(role)

        // optimization for when there is no change in the child list
        if (associatedChildren.all { it.alreadyMatches(isOrdered) }) {
            associatedChildren.forEach {
                runSafe {
                    synchronizeNode(it.getSource(), it.getTarget(), forceSyncDescendants)
                }
            }
            return
        }

        // Recursive sync is done at the end because they may apply move operations that mutate
        // the currently iterated list.
        val recursiveSyncTasks = ArrayList<RecursiveSyncTask>()

        // Since nodes are removed at the end of the sync, we have to adjust the index for add/move operations.
        // We could just use `sourceIndex`, but that would result in unnecessary move operations.
        val orderBeforeRemove = associatedChildren.sortedWith(
            compareBy({
                when (it.getOperationType(isOrdered)) {
                    AssociatedChild.OperationType.REMOVE -> it.targetIndex!!
                    else -> it.sourceIndex!!
                }
            }, {
                when (it.getOperationType(isOrdered)) {
                    AssociatedChild.OperationType.REMOVE -> 0
                    else -> 1
                }
            }),
        )

        for ((targetIndex, associatedChild) in orderBeforeRemove.withIndex()) {
            when (associatedChild.getOperationType(isOrdered)) {
                AssociatedChild.OperationType.CREATE -> {
                    val newChild = targetParent.syncNewChild(role, targetIndex, NewNodeSpec(associatedChild.getSource()))
                    nodeAssociation.associate(associatedChild.getSource(), newChild)
                    recursiveSyncTasks += RecursiveSyncTask(associatedChild.getSource(), newChild, isNew = true)
                }
                AssociatedChild.OperationType.REMOVE -> {
                    nodesToRemove += associatedChild.getTarget()
                }
                AssociatedChild.OperationType.MOVE_SAME_CONTAINMENT -> {
                    targetParent.moveChild(role, targetIndex, associatedChild.getTarget())
                    recursiveSyncTasks += RecursiveSyncTask(associatedChild.getSource(), associatedChild.getTarget(), isNew = false)
                }
                AssociatedChild.OperationType.MOVE_DIFFERENT_CONTAINMENT -> {
                    targetParent.moveChild(role, targetIndex, associatedChild.getTarget())
                    recursiveSyncTasks += RecursiveSyncTask(associatedChild.getSource(), associatedChild.getTarget(), isNew = false)
                    nodesToRemove.remove(associatedChild.getTarget())
                }
                AssociatedChild.OperationType.ALREADY_MATCHES -> {
                    recursiveSyncTasks += RecursiveSyncTask(associatedChild.getSource(), associatedChild.getTarget(), isNew = false)
                }
            }
        }

        for (task in recursiveSyncTasks) {
            synchronizeNode(task.source, task.target, forceSyncDescendants || task.isNew)
        }
    }

    private class RecursiveSyncTask(val source: IReadableNode, val target: IWritableNode, val isNew: Boolean)

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

    private fun associateChildren(sourceParent: IReadableNode, targetParent: IWritableNode, role: IChildLinkReference): List<AssociatedChild> {
        val sourceNodes = getFilteredSourceChildren(sourceParent, role)
        val targetNodes = getFilteredTargetChildren(targetParent, role)
        return associateChildren(sourceNodes, targetNodes)
    }

    private fun associateChildren(sourceChildren: List<IReadableNode>, targetChildren: List<IWritableNode>): List<AssociatedChild> {
        val unassociatedTargetNodes = targetChildren.withIndex().toMutableList()
        return sourceChildren.mapIndexed { sourceIndex, sourceChild ->
            val foundAt = unassociatedTargetNodes.indexOfFirst { targetChild ->
                nodeAssociation.matches(sourceChild, targetChild.value)
            }
            if (foundAt == -1) {
                AssociatedChild(sourceIndex, null, sourceChild, null, nodeAssociation.resolveTarget(sourceChild))
            } else {
                val foundTarget = unassociatedTargetNodes.removeAt(foundAt)
                AssociatedChild(sourceIndex, foundTarget.index, sourceChild, foundTarget.value, null)
            }
        } + unassociatedTargetNodes.map { AssociatedChild(null, it.index, null, it.value, null) }
    }

    private class AssociatedChild(
        val sourceIndex: Int?,
        val targetIndex: Int?,
        private val source: IReadableNode?,
        private val existingTargetInSameContainment: IWritableNode?,
        private val existingTargetInDifferentContainment: IWritableNode?,
    ) {
        var currentTargetIndex: Int? = targetIndex

        fun hasToCreate() = getOperationType(true) == OperationType.CREATE
        fun hasToMoveFromDifferentContainment() = getOperationType(true) == OperationType.MOVE_DIFFERENT_CONTAINMENT
        fun hasToMoveWithinSameContainment(ordered: Boolean) = getOperationType(ordered) == OperationType.MOVE_SAME_CONTAINMENT
        fun hasToMove(ordered: Boolean) = hasToMoveFromDifferentContainment() || hasToMoveWithinSameContainment(ordered)
        fun hasToDelete() = getOperationType(true) == OperationType.REMOVE
        fun alreadyMatchesOrdered() = alreadyMatchesUnordered() && sourceIndex == targetIndex
        fun alreadyMatchesUnordered() = source != null && existingTargetInSameContainment != null
        fun alreadyMatches(ordered: Boolean) = if (ordered) alreadyMatchesOrdered() else alreadyMatchesUnordered()

        fun getTarget() = existingTargetInSameContainment ?: existingTargetInDifferentContainment!!
        fun getSource() = source!!

        fun getOperationType(ordered: Boolean): OperationType {
            return if (source == null) {
                OperationType.REMOVE
            } else {
                if (existingTargetInSameContainment != null) {
                    if (ordered) {
                        OperationType.MOVE_SAME_CONTAINMENT
                    } else {
                        OperationType.ALREADY_MATCHES
                    }
                } else if (existingTargetInDifferentContainment != null) {
                    OperationType.MOVE_DIFFERENT_CONTAINMENT
                } else {
                    OperationType.CREATE
                }
            }
        }

        enum class OperationType {
            CREATE,
            REMOVE,
            MOVE_SAME_CONTAINMENT,
            MOVE_DIFFERENT_CONTAINMENT,
            ALREADY_MATCHES,
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

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

import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.IRole
import org.modelix.model.api.NullChildLink

class ModelSynchronizer(
    val filter: IFilter,
    val sourceRoot: INode,
    val targetRoot: INode,
    val nodeAssociation: INodeAssociation
) {
    private val nodesToRemove: MutableSet<INode> = HashSet()
    private val pendingReferences: MutableList<PendingReference> = ArrayList()

    fun synchronize() {
        synchronizeNode(sourceRoot, targetRoot)
        pendingReferences.forEach { it.trySyncReference() }
    }

    fun synchronizeNode(sourceNode: INode, targetNode: INode) {
        if (filter.synchronizeNode(sourceNode)) {
            iterateMergedRoles(sourceNode.getPropertyLinks(), targetNode.getPropertyLinks()) { role ->
                targetNode.setPropertyValue(role.preferTarget(), sourceNode.getPropertyValue(role.preferSource()))
            }

            iterateMergedRoles(sourceNode.getReferenceLinks(), targetNode.getReferenceLinks()) { role ->
                val pendingReference = PendingReference(sourceNode, targetNode, role)

                // If the reference target already exist we can synchronize it immediately and save memory between the
                // two synchronization phases.
                if (!pendingReference.trySyncReference()) {
                    pendingReferences += pendingReference
                }
            }

            synchronizeAllChildren(sourceNode, targetNode)

            targetNode.allChildren.toList()
        }
    }

    fun synchronizeAllChildren(sourceNode: INode, targetNode: INode) {
        iterateMergedRoles(
            sourceNode.allChildren.asSequence().map { it.getContainmentLink() ?: NullChildLink },
            targetNode.allChildren.asSequence().map { it.getContainmentLink() ?: NullChildLink }
        ) { role ->
            synchronizeChildren(sourceNode, targetNode, role)
        }
    }

    fun synchronizeChildren(sourceParent: INode, targetParent: INode, role: MergedRole<IChildLink>) {
        val existingNodes: () -> List<INode> = { targetParent.getChildren(role.preferTarget()).toList() }
        val ordered = (role.preferTarget()).isOrdered
        val toRemove = existingNodes().toHashSet()
        val childrenSyncQueue: MutableList<Pair<INode, INode>> = ArrayList()

        val expectedNodes = sourceParent.getChildren(role.preferSource()).toList()
        expectedNodes.forEachIndexed { index, sourceChild ->
            if (ordered) {
                val existingTargetChild = existingNodes()[index]
                if (nodeAssociation.associationMatches(sourceChild, existingTargetChild)) return@forEachIndexed
            }
            val resolvedTargetChild = nodeAssociation.resolveTarget(sourceChild)
            if (resolvedTargetChild == null) {
                // TODO pass sourceChild to addNewChild after the PR containing that new method is merged
                val newTargetChild = targetParent.addNewChild(
                    role.preferTarget(),
                    if (ordered) index else -1,
                    sourceChild.getConceptReference()
                )
                nodeAssociation.associate(sourceChild, newTargetChild)
                childrenSyncQueue += sourceChild to newTargetChild
            } else {
                toRemove.remove(resolvedTargetChild)

                // if it's ordered, and we reach this line we already know that the index doesn't match
                if (ordered || resolvedTargetChild.parent != targetParent || resolvedTargetChild.getContainmentLink()?.getUID() != role.preferTarget().getUID()) {
                    targetParent.moveChild(
                        role.preferTarget(),
                        if (ordered) index else -1,
                        resolvedTargetChild
                    )
                }
                childrenSyncQueue += sourceChild to resolvedTargetChild
            }
        }

        nodesToRemove += toRemove
        childrenSyncQueue.forEach { synchronizeNode(it.first, it.second) }
    }

    inner class PendingReference(val sourceNode: INode, val targetNode: INode, val role: MergedRole<IReferenceLink>) {
        fun trySyncReference(): Boolean {
            val expectedRef = sourceNode.getReferenceTargetRef(role.preferSource())
            if (expectedRef == null) {
                targetNode.setReferenceTarget(role.preferTarget(), null as INodeReference?)
                return true
            }
            val actualRef = targetNode.getReferenceTargetRef(role.preferTarget())

            // Some reference targets may be excluded from the sync, but the reference itself is synchronized as a
            // global reference. In that case no lookup of the target is required.
            if (actualRef?.serialize() == expectedRef.serialize()) {
                // already up to date
                return true
            }

            val referenceTargetInSource = sourceNode.getReferenceTarget(role.preferSource())
            checkNotNull(referenceTargetInSource) { "Failed to resolve $expectedRef referenced by ${sourceNode}.${role.preferSource()}" }

            val referenceTargetInTarget = nodeAssociation.resolveTarget(referenceTargetInSource)
            if (referenceTargetInTarget == null) return false // Target probably not created yet. Try again later.

            targetNode.setReferenceTarget(role.preferTarget(), referenceTargetInTarget)
            return true
        }
    }

    fun <T : IRole> iterateMergedRoles(
        sourceRoles: Iterable<T>,
        targetRoles: Iterable<T>,
        body: (role: MergedRole<T>) -> Unit
    ) = iterateMergedRoles(sourceRoles.asSequence(), targetRoles.asSequence(), body)

    fun <T : IRole> iterateMergedRoles(
        sourceRoles: Sequence<T>,
        targetRoles: Sequence<T>,
        body: (role: MergedRole<T>) -> Unit
    ) {
        val sourceRolesMap = sourceRoles.associateBy { it.getUID() }
        val targetRolesMap = targetRoles.associateBy { it.getUID() }
        val roleUIDs = (sourceRolesMap.keys + targetRolesMap.keys).toSet()
        for (roleUID in roleUIDs) {
            val sourceRole = sourceRolesMap[roleUID]
            val targetRole = targetRolesMap[roleUID]
            body(MergedRole(sourceRole, targetRole))
        }
    }

    class MergedRole<E : IRole>(
        val source: E?,
        val target: E?,
    ) {
        fun preferTarget(): E = (target ?: source)!!
        fun preferSource() = (source ?: target)!!
    }

    interface IFilter {
        fun descendIntoSubtree(node: INode): Boolean
        fun synchronizeNode(node: INode): Boolean
    }

}

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

package org.modelix.model.async

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.IKVValue

class AsyncTree(private val treeData: CPTree, private val bulkQuery: IBulkQuery) : IAsyncTree {

    private val nodesMap: KVEntryReference<CPHamtNode> = treeData.idToHash

    private fun <T : IKVValue> KVEntryReference<T>.query(): IAsyncValue<T> = bulkQuery.queryNotNull(this)
    private fun <T : IKVValue> KVEntryReference<T>.tryQuery(): IAsyncValue<T?> = bulkQuery.query(this)

    private fun getNode(id: Long): IAsyncValue<CPNode> = tryGetNodeRef(id)
        .checkNotNull { "Node ${id.toString(16)} not found in $nodesMap" }
        .flatMap { it.query() }

    private fun tryGetNodeRef(id: Long): IAsyncValue<KVEntryReference<CPNode>?> = nodesMap.query().flatMap { it.get(id, bulkQuery) }

    override fun containsNode(nodeId: Long): IAsyncValue<Boolean> {
        return tryGetNodeRef(nodeId).map { it != null }
    }

    override fun visitChanges(oldVersion: IAsyncTree, visitor: IAsyncTreeChangeVisitor): IAsyncValue<Unit> {
        require(oldVersion is AsyncTree)
        require(oldVersion.bulkQuery == bulkQuery) { "Both trees should operate with the same IBulkQuery" }
        if (nodesMap == oldVersion.nodesMap) return IAsyncValue.UNIT
        return (nodesMap.query() to oldVersion.nodesMap.query()).flatMapBoth { newMap, oldMap ->
            visitChanges(newMap, oldMap, visitor)
        }
    }

    private fun visitChanges(newNodesMap: CPHamtNode, oldNodesMap: CPHamtNode, visitor: IAsyncTreeChangeVisitor): IAsyncValue<Unit> {
        val changesOnly = !visitor.interestedInNodeRemoveOrAdd()
        return newNodesMap.visitChanges(
            oldNodesMap,
            object : CPHamtNode.IChangeVisitor {
                private val childrenChangeEvents = HashSet<Pair<Long, String?>>()

                private fun notifyChildrenChange(parent: Long, role: String?): IAsyncValue<Unit> {
                    return if (childrenChangeEvents.add(parent to role)) visitor.childrenChanged(parent, role) else IAsyncValue.UNIT
                }

                override fun visitChangesOnly(): Boolean {
                    return changesOnly
                }

                override fun entryAdded(key: Long, value: KVEntryReference<CPNode>): IAsyncValue<Unit> {
                    return visitor.nodeAdded(key)
                }

                override fun entryRemoved(key: Long, value: KVEntryReference<CPNode>): IAsyncValue<Unit> {
                    return visitor.nodeRemoved(key)
                }

                override fun entryChanged(key: Long, oldValue: KVEntryReference<CPNode>, newValue: KVEntryReference<CPNode>): IAsyncValue<Unit> {
                    return (newValue.query() to oldValue.query()).mapBoth { newNode, oldNode ->
                        val notifiedChanges = ArrayList<IAsyncValue<Unit>>()
                        if (oldNode.parentId != newNode.parentId) {
                            notifiedChanges += visitor.containmentChanged(key)
                        } else if (oldNode.roleInParent != newNode.roleInParent) {
                            notifiedChanges += visitor.containmentChanged(key)
                            notifiedChanges += notifyChildrenChange(oldNode.parentId, oldNode.roleInParent)
                            notifiedChanges += notifyChildrenChange(newNode.parentId, newNode.roleInParent)
                        }
                        if (oldNode.concept != newNode.concept) {
                            notifiedChanges += visitor.conceptChanged(key)
                        }
                        notifiedChanges += oldNode.propertyRoles.asSequence()
                            .plus(newNode.propertyRoles.asSequence())
                            .distinct()
                            .mapNotNull { role: String ->
                                if (oldNode.getPropertyValue(role) != newNode.getPropertyValue(role)) {
                                    visitor.propertyChanged(newNode.id, role)
                                } else {
                                    null
                                }
                            }
                        notifiedChanges += oldNode.referenceRoles.asSequence()
                            .plus(newNode.referenceRoles.asSequence())
                            .distinct()
                            .mapNotNull { role: String ->
                                if (oldNode.getReferenceTarget(role) != newNode.getReferenceTarget(role)) {
                                    visitor.referenceChanged(newNode.id, role)
                                } else {
                                    null
                                }
                            }

                        notifiedChanges += (newNode.childrenIdArray.map { getNode(it) }.mapList() to oldNode.childrenIdArray.map { getNode(it) }.mapList()).flatMapBoth { newChildrenList, oldChildrenList ->
                            val oldChildren: MutableMap<String?, MutableList<CPNode>> = HashMap()
                            val newChildren: MutableMap<String?, MutableList<CPNode>> = HashMap()
                            oldChildrenList.forEach { oldChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
                            newChildrenList.forEach { newChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }

                            val roles: MutableSet<String?> = HashSet()
                            roles.addAll(oldChildren.keys)
                            roles.addAll(newChildren.keys)
                            roles.mapNotNull { role ->
                                val oldChildrenInRole = oldChildren[role]
                                val newChildrenInRole = newChildren[role]
                                val oldValues = oldChildrenInRole?.map { it.id }
                                val newValues = newChildrenInRole?.map { it.id }
                                if (oldValues != newValues) {
                                    notifyChildrenChange(newNode.id, role)
                                } else {
                                    null
                                }
                            }.mapList()
                        }.map { }

                        notifiedChanges.mapList()
                    }
                }
            },
            bulkQuery,
        )
    }

    override fun getProperty(nodeId: Long, role: String): IAsyncValue<String?> {
        return getNode(nodeId).map { it.getPropertyValue(role) }
    }

    override fun getChildren(parentId: Long, role: String?): IAsyncValue<List<Long>> {
        return getAllChildren(parentId).flatMap {
            it.map { getNode(it) }.mapList {
                it.filter { it.roleInParent == role }.map { it.id }
            }
        }
    }

    override fun getConceptReference(nodeId: Long): IAsyncValue<ConceptReference?> {
        return getNode(nodeId).map { it.concept?.let { ConceptReference(it) } }
    }

    override fun getParent(nodeId: Long): IAsyncValue<Long> {
        return getNode(nodeId).map { it.parentId }
    }

    override fun getRole(nodeId: Long): IAsyncValue<String?> {
        return getNode(nodeId).map { it.roleInParent }
    }

    override fun getReferenceTarget(sourceId: Long, role: String): IAsyncValue<INodeReference?> {
        return getNode(sourceId).map { node ->
            val targetRef = node.getReferenceTarget(role)
            when {
                targetRef == null -> null
                targetRef.isLocal -> PNodeReference(targetRef.elementId, treeData.id)
                targetRef is CPNodeRef.ForeignRef -> org.modelix.model.api.INodeReferenceSerializer.deserialize(targetRef.serializedRef)
                else -> throw UnsupportedOperationException("Unsupported reference: $targetRef")
            }
        }
    }

    override fun getReferenceRoles(sourceId: Long): IAsyncValue<List<String>> {
        return getNode(sourceId).map { it.referenceRoles.toList() }
    }

    override fun getPropertyRoles(sourceId: Long): IAsyncValue<List<String>> {
        return getNode(sourceId).map { it.propertyRoles.toList() }
    }

    override fun getChildRoles(sourceId: Long): IAsyncValue<List<String?>> {
        return getAllChildren(sourceId).flatMap {
            it.map { getNode(it) }.mapList {
                it.map { it.roleInParent }.distinct()
            }
        }
    }

    override fun getAllChildren(parentId: Long): IAsyncValue<List<Long>> {
        return getNode(parentId).map { it.childrenIdArray.toList() }
    }
}

private fun <T : IKVValue> IBulkQuery.queryNotNull(ref: KVEntryReference<T>) = query(ref).checkNotNull { "$ref not found" }

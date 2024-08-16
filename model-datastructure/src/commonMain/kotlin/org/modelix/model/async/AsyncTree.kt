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

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.asSingleOrError
import com.badoo.reaktive.maybe.defaultIfEmpty
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.distinctUntilChanged
import com.badoo.reaktive.observable.filter
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.filter
import com.badoo.reaktive.single.flatMap
import com.badoo.reaktive.single.flatMapIterable
import com.badoo.reaktive.single.flatMapMaybe
import com.badoo.reaktive.single.map
import com.badoo.reaktive.single.notNull
import com.badoo.reaktive.single.singleOf
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRole
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.IRoleReferenceByName
import org.modelix.model.api.IRoleReferenceByUID
import org.modelix.model.api.IUnclassifiedRoleReference
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.async.IAsyncTree
import org.modelix.model.api.async.IAsyncTreeChangeVisitor
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.IKVValue

class AsyncTree(private val treeData: () -> CPTree, private val store: IAsyncObjectStore) : IAsyncTree {

    private val nodesMap: KVEntryReference<CPHamtNode> = treeData().idToHash

    private fun <T : IKVValue> KVEntryReference<T>.query(): Single<T> = tryQuery().asSingleOrError { throw IllegalStateException("Entry not found: $this") }
    private fun <T : IKVValue> KVEntryReference<T>.tryQuery(): Maybe<T> = store.get(this)

    private fun getNode(id: Long): Single<CPNode> = tryGetNodeRef(id)
        .asSingleOrError { IllegalStateException("Node ${id.toString(16)} not found in $nodesMap") }
        .flatMap { it.query() }

    private fun tryGetNodeRef(id: Long): Maybe<KVEntryReference<CPNode>> = nodesMap.query().flatMapMaybe { it.get(id, store) }

    override fun asStream(): Single<IAsyncTree> {
        return singleOf(this)
    }

    override fun containsNode(nodeId: Long): Single<Boolean> {
        return tryGetNodeRef(nodeId).map { true }.defaultIfEmpty(false)
    }

    override fun visitChanges(oldVersion: IAsyncTree, visitor: IAsyncTreeChangeVisitor): Maybe<Unit> {
        TODO()
//        require(oldVersion is AsyncTree)
//        require(oldVersion.store == store) { "Both trees should operate with the same IAsyncObjectStore" }
//        if (nodesMap == oldVersion.nodesMap) return store.getStreamFactory().constant(Unit)
//        return (nodesMap.query() to oldVersion.nodesMap.query()).mapBothMono { newMap, oldMap ->
//            visitChanges(newMap, oldMap, visitor).asStream()
//        }
    }

    private fun visitChanges(newNodesMap: CPHamtNode, oldNodesMap: CPHamtNode, visitor: IAsyncTreeChangeVisitor): Single<Unit> {
        TODO()
//        val changesOnly = !visitor.interestedInNodeRemoveOrAdd()
//        return newNodesMap.visitChanges(
//            oldNodesMap,
//            object : CPHamtNode.IChangeVisitor {
//                private val childrenChangeEvents = HashSet<Pair<Long, String?>>()
//
//                private fun notifyChildrenChange(parent: Long, role: String?): IAsyncValue<Unit> {
//                    return if (childrenChangeEvents.add(parent to role)) visitor.childrenChanged(parent, role) else IAsyncValue.UNIT
//                }
//
//                override fun visitChangesOnly(): Boolean {
//                    return changesOnly
//                }
//
//                override fun entryAdded(key: Long, value: KVEntryReference<CPNode>): IAsyncValue<Unit> {
//                    return visitor.nodeAdded(key)
//                }
//
//                override fun entryRemoved(key: Long, value: KVEntryReference<CPNode>): IAsyncValue<Unit> {
//                    return visitor.nodeRemoved(key)
//                }
//
//                override fun entryChanged(key: Long, oldValue: KVEntryReference<CPNode>, newValue: KVEntryReference<CPNode>): IAsyncValue<Unit> {
//                    return (newValue.query() to oldValue.query()).mapBothMono { newNode, oldNode ->
//                        val notifiedChanges = ArrayList<IAsyncValue<Unit>>()
//                        if (oldNode.parentId != newNode.parentId) {
//                            notifiedChanges += visitor.containmentChanged(key)
//                        } else if (oldNode.roleInParent != newNode.roleInParent) {
//                            notifiedChanges += visitor.containmentChanged(key)
//                            notifiedChanges += notifyChildrenChange(oldNode.parentId, oldNode.roleInParent)
//                            notifiedChanges += notifyChildrenChange(newNode.parentId, newNode.roleInParent)
//                        }
//                        if (oldNode.concept != newNode.concept) {
//                            notifiedChanges += visitor.conceptChanged(key)
//                        }
//                        notifiedChanges += oldNode.propertyRoles.asSequence()
//                            .plus(newNode.propertyRoles.asSequence())
//                            .distinct()
//                            .mapNotNull { role: String ->
//                                if (oldNode.getPropertyValue(role) != newNode.getPropertyValue(role)) {
//                                    visitor.propertyChanged(newNode.id, role)
//                                } else {
//                                    null
//                                }
//                            }
//                        notifiedChanges += oldNode.referenceRoles.asSequence()
//                            .plus(newNode.referenceRoles.asSequence())
//                            .distinct()
//                            .mapNotNull { role: String ->
//                                if (oldNode.getReferenceTarget(role) != newNode.getReferenceTarget(role)) {
//                                    visitor.referenceChanged(newNode.id, role)
//                                } else {
//                                    null
//                                }
//                            }
//
//                        notifiedChanges += (newNode.childrenIdArray.map { getNode(it) }.requestAll() to oldNode.childrenIdArray.map { getNode(it) }.requestAll()).flatMapBoth { newChildrenList, oldChildrenList ->
//                            val oldChildren: MutableMap<String?, MutableList<CPNode>> = HashMap()
//                            val newChildren: MutableMap<String?, MutableList<CPNode>> = HashMap()
//                            oldChildrenList.forEach { oldChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
//                            newChildrenList.forEach { newChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
//
//                            val roles: MutableSet<String?> = HashSet()
//                            roles.addAll(oldChildren.keys)
//                            roles.addAll(newChildren.keys)
//                            roles.mapNotNull { role ->
//                                val oldChildrenInRole = oldChildren[role]
//                                val newChildrenInRole = newChildren[role]
//                                val oldValues = oldChildrenInRole?.map { it.id }
//                                val newValues = newChildrenInRole?.map { it.id }
//                                if (oldValues != newValues) {
//                                    notifyChildrenChange(newNode.id, role)
//                                } else {
//                                    null
//                                }
//                            }.requestAll()
//                        }.map { }
//
//                        notifiedChanges.requestAll()
//                    }
//                }
//            },
//            store,
//        ).asStream()
    }

    override fun getConceptReference(nodeId: Long): Single<ConceptReference> {
        return getNode(nodeId).map { ConceptReference(it.concept ?: NullConcept.getUID()) }
    }

    override fun getParent(nodeId: Long): Maybe<Long> {
        return getNode(nodeId).map { it.parentId }.filter { it != 0L }
    }

    override fun getRole(nodeId: Long): Single<IChildLinkReference> {
        return getNode(nodeId).map { it.roleInParent }.map {
            when {
                it == null -> NullChildLinkReference
                treeData().usesRoleIds -> IChildLinkReference.fromId(it)
                else -> IChildLinkReference.fromName(it)
            }
        }
    }

    override fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): Maybe<INodeReference> {
        return getNode(sourceId).map { node ->
            node.getReferenceTarget(role.key())?.convertReference()
        }.notNull()
    }

    private fun CPNodeRef.convertReference(): INodeReference {
        val targetRef = this
        return when {
            targetRef.isLocal -> PNodeReference(targetRef.elementId, treeData().id)
            targetRef is CPNodeRef.ForeignRef -> org.modelix.model.api.INodeReferenceSerializer.deserialize(targetRef.serializedRef)
            else -> throw UnsupportedOperationException("Unsupported reference: $targetRef")
        }
    }

    override fun getReferenceRoles(sourceId: Long): Observable<String> {
        return getNode(sourceId).flatMapIterable { it.referenceRoles.toList() }
    }

    override fun getPropertyRoles(sourceId: Long): Observable<String> {
        return getNode(sourceId).flatMapIterable { it.propertyRoles.toList() }
    }

    override fun getChildRoles(sourceId: Long): Observable<String?> {
        return getAllChildren(sourceId).flatMapSingle {
            getNode(it).map { it.roleInParent }
        }.distinctUntilChanged()
    }

    override fun getAllChildren(parentId: Long): Observable<Long> {
        return getNode(parentId).flatMapIterable { it.childrenIdArray.toList() }
    }

    override fun getAllReferenceTargetRefs(sourceId: Long): Observable<Pair<IReferenceLinkReference, INodeReference>> {
        return getNode(sourceId).flatMapIterable { data ->
            data.referenceRoles.mapIndexed { index, role ->
                val link = if (treeData().usesRoleIds) IReferenceLinkReference.fromId(role) else IReferenceLinkReference.fromId(role)
                link to data.referenceTargets[index].convertReference()
            }
        }
    }

    override fun getProperty(nodeId: Long, role: IPropertyReference): Maybe<String> {
        return getNode(nodeId).map { node ->
            node.getPropertyValue(role.key())
        }.notNull()
    }

    override fun getChildren(parentId: Long, role: IChildLinkReference): Observable<Long> {
        val roleString = role.key()
        return getAllChildren(parentId).flatMapSingle { getNode(it) }.filter { it.roleInParent == roleString }.map { it.id }
    }

    private fun IRoleReference.key() = getRoleKey(this)
    private fun IChildLinkReference.key() = if (this is NullChildLinkReference) null else getRoleKey(this)

    fun getRoleKey(role: IRoleReference): String {
        if (treeData().usesRoleIds) {
            return when (role) {
                is IRoleReferenceByName -> throw IllegalArgumentException("ID needed, but was $role")
                is IRoleReferenceByUID -> role.getUID()
                is IUnclassifiedRoleReference -> role.getStringValue()
                is IRole -> role.getUID()
                else -> throw IllegalArgumentException("Unknown IRoleReference type: $role")
            }
        } else {
            return when (role) {
                is IRoleReferenceByName -> role.getSimpleName()
                is IRoleReferenceByUID -> throw IllegalArgumentException("Name needed, but was $role")
                is IUnclassifiedRoleReference -> role.getStringValue()
                is IRole -> role.getSimpleName()
                else -> throw IllegalArgumentException("Unknown IRoleReference type: $role")
            }
        }
    }
}

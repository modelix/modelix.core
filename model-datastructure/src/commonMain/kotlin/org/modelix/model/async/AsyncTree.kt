package org.modelix.model.async

import com.badoo.reaktive.completable.andThen
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.asSingleOrError
import com.badoo.reaktive.maybe.defaultIfEmpty
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.concatWith
import com.badoo.reaktive.observable.filter
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.observableOfEmpty
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asCompletable
import com.badoo.reaktive.single.filter
import com.badoo.reaktive.single.flatMap
import com.badoo.reaktive.single.flatMapIterable
import com.badoo.reaktive.single.flatMapMaybe
import com.badoo.reaktive.single.flatten
import com.badoo.reaktive.single.map
import com.badoo.reaktive.single.notNull
import com.badoo.reaktive.single.singleOf
import com.badoo.reaktive.single.zipWith
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.ITree
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.async.ChildrenChangedEvent
import org.modelix.model.api.async.ConceptChangedEvent
import org.modelix.model.api.async.ContainmentChangedEvent
import org.modelix.model.api.async.IAsyncMutableTree
import org.modelix.model.api.async.IAsyncTree
import org.modelix.model.api.async.NodeAddedEvent
import org.modelix.model.api.async.NodeRemovedEvent
import org.modelix.model.api.async.PropertyChangedEvent
import org.modelix.model.api.async.ReferenceChangedEvent
import org.modelix.model.api.async.TreeChangeEvent
import org.modelix.model.api.async.getAncestors
import org.modelix.model.api.async.getDescendantsAndSelf
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.COWArrays.insert
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IPrefetchGoal
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.NodeNotFoundException
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import org.modelix.model.persistent.CPNodeRef.Companion.foreign
import org.modelix.model.persistent.CPNodeRef.Companion.global
import org.modelix.model.persistent.CPNodeRef.Companion.local
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.EntryAddedEvent
import org.modelix.model.persistent.EntryChangedEvent
import org.modelix.model.persistent.EntryRemovedEvent
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.asObservable
import org.modelix.streams.assertEmpty
import org.modelix.streams.distinct
import org.modelix.streams.flatten
import org.modelix.streams.fold

open class AsyncTree(val treeData: CPTree, val store: IAsyncObjectStore) : IAsyncMutableTree {

    private val nodesMap: KVEntryReference<CPHamtNode> = treeData.idToHash

    private fun <T : IKVValue> KVEntryReference<T>.query(): Single<T> = this.getValue(store)

    fun getNode(id: Long): Single<CPNode> = tryGetNodeRef(id)
        .asSingleOrError { NodeNotFoundException(id) }
        .flatMap { it.query() }

    private fun tryGetNodeRef(id: Long): Maybe<KVEntryReference<CPNode>> = nodesMap.query().flatMapMaybe { it.get(id, store) }

    @Deprecated("Prefetching will be replaced by usages of IAsyncNode")
    private fun loadPrefetch(node: CPNode) {
        val bulkQuery = (store as? BulkQueryAsAsyncStore)?.bulkQuery
        if (bulkQuery != null) {
            val children: LongArray = node.childrenIdArray
            if (children.isNotEmpty()) {
                children.reversedArray().forEach {
                    bulkQuery.offerPrefetch(PrefetchNodeGoal(this, it))
                }
            }
            if (node.parentId != 0L) {
                bulkQuery.offerPrefetch(PrefetchNodeGoal(this, node.parentId))
            }
            node.referenceTargets.asSequence().filter { it.isLocal }.forEach { target ->
                bulkQuery.offerPrefetch(PrefetchNodeGoal(this, target.elementId))
            }
        }
    }

    @Deprecated("Prefetching will be replaced by usages of IAsyncNode")
    private fun Observable<Long>.loadPrefetch(): Observable<Long> {
        val bulkQuery = (store as? BulkQueryAsAsyncStore)?.bulkQuery ?: return this
        return map {
            bulkQuery.offerPrefetch(PrefetchNodeGoal(this@AsyncTree, it))
            it
        }
    }

    override fun asSynchronousTree(): ITree {
        return CLTree(treeData, store)
        // return AsyncAsSynchronousTree(this)
    }

    override fun containsNode(nodeId: Long): Single<Boolean> {
        return tryGetNodeRef(nodeId).map { true }.defaultIfEmpty(false)
    }

    override fun getChanges(oldVersion: IAsyncTree, changesOnly: Boolean): Observable<TreeChangeEvent> {
        require(oldVersion is AsyncTree)
        if (nodesMap == oldVersion.nodesMap) return observableOfEmpty()
        return nodesMap.query().zipWith(oldVersion.nodesMap.query()) { newMap, oldMap ->
            getChanges(oldVersion, newMap, oldMap, changesOnly)
        }.flatten()
    }

    private fun getChanges(oldTree: AsyncTree, newNodesMap: CPHamtNode, oldNodesMap: CPHamtNode, changesOnly: Boolean): Observable<TreeChangeEvent> {
        return newNodesMap.getChanges(oldNodesMap, 0, store, changesOnly).flatMap { mapEvent ->
            when (mapEvent) {
                is EntryAddedEvent -> {
                    if (changesOnly) {
                        observableOfEmpty<TreeChangeEvent>()
                    } else {
                        observableOf(NodeAddedEvent(mapEvent.key))
                    }
                }
                is EntryRemovedEvent -> {
                    if (changesOnly) {
                        observableOfEmpty<TreeChangeEvent>()
                    } else {
                        observableOf(NodeRemovedEvent(mapEvent.key))
                    }
                }
                is EntryChangedEvent -> {
                    mapEvent.newValue.query().zipWith(mapEvent.oldValue.query()) { newNode, oldNode ->
                        getChanges(oldTree, oldNode, newNode, mapEvent)
                    }.flatten()
                }
            }
        }.distinct()
    }

    private fun getChanges(oldTree: AsyncTree, oldNode: CPNode, newNode: CPNode, mapEvent: EntryChangedEvent): Observable<TreeChangeEvent> {
        val changes = ArrayList<TreeChangeEvent>()

        if (oldNode.parentId != newNode.parentId) {
            changes += ContainmentChangedEvent(mapEvent.key)
        } else if (oldNode.roleInParent != newNode.roleInParent) {
            changes += ContainmentChangedEvent(mapEvent.key)
            changes += ChildrenChangedEvent(oldNode.parentId, getChildLinkFromString(oldNode.roleInParent))
            changes += ChildrenChangedEvent(newNode.parentId, getChildLinkFromString(newNode.roleInParent))
        }

        if (oldNode.concept != newNode.concept) {
            changes += ConceptChangedEvent(mapEvent.key)
        }

        oldNode.propertyRoles.asSequence()
            .plus(newNode.propertyRoles.asSequence())
            .distinct()
            .forEach { role: String ->
                if (oldNode.getPropertyValue(role) != newNode.getPropertyValue(role)) {
                    changes += PropertyChangedEvent(newNode.id, getPropertyFromString(role))
                }
            }

        oldNode.referenceRoles.asSequence()
            .plus(newNode.referenceRoles.asSequence())
            .distinct()
            .forEach { role: String ->
                if (oldNode.getReferenceTarget(role) != newNode.getReferenceTarget(role)) {
                    changes += ReferenceChangedEvent(newNode.id, getReferenceLinkFromString(role))
                }
            }

        val newChildren = newNode.childrenIdArray.asObservable().flatMapSingle { getNode(it) }.toList()
        val oldChildren = oldNode.childrenIdArray.asObservable().flatMapSingle { oldTree.getNode(it) }.toList()
        val childrenChanges = newChildren.zipWith(oldChildren) { newChildrenList, oldChildrenList ->
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
                    ChildrenChangedEvent(newNode.id, getChildLinkFromString(role))
                } else {
                    null
                }
            }.asObservable()
        }.flatten()

        return changes.asObservable().concatWith(childrenChanges)
    }

    override fun getConceptReference(nodeId: Long): Single<ConceptReference> {
        return getNode(nodeId).map { ConceptReference(it.concept ?: NullConcept.getUID()) }
    }

    override fun getParent(nodeId: Long): Maybe<Long> {
        return getNode(nodeId).map { it.parentId }.filter { it != 0L }
    }

    override fun getRole(nodeId: Long): Single<IChildLinkReference> {
        return getNode(nodeId).map { getChildLinkFromString(it.roleInParent) }
    }

    override fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): Maybe<INodeReference> {
        return getNode(sourceId).map { node ->
            node.getReferenceTarget(role.key())?.convertReference()
        }.notNull()
    }

    private fun CPNodeRef.convertReference(): INodeReference {
        val targetRef = this
        return when {
            targetRef.isLocal -> PNodeReference(targetRef.elementId, treeData.id)
            targetRef is CPNodeRef.ForeignRef -> org.modelix.model.api.INodeReferenceSerializer.deserialize(targetRef.serializedRef)
            else -> throw UnsupportedOperationException("Unsupported reference: $targetRef")
        }
    }

    override fun getReferenceRoles(sourceId: Long): Observable<IReferenceLinkReference> {
        return getNode(sourceId).flatMapIterable { it.referenceRoles.map { getReferenceLinkFromString(it) } }
    }

    override fun getPropertyRoles(sourceId: Long): Observable<IPropertyReference> {
        return getNode(sourceId).flatMapIterable { it.propertyRoles.map { getPropertyFromString(it) } }
    }

    override fun getAllPropertyValues(sourceId: Long): Observable<Pair<IPropertyReference, String>> {
        return getNode(sourceId).flatMapIterable { data ->
            data.propertyRoles.mapIndexed { index, role ->
                getPropertyFromString(role) to data.propertyValues[index]
            }
        }
    }

    override fun getChildRoles(sourceId: Long): Observable<IChildLinkReference> {
        return getAllChildren(sourceId).flatMapSingle {
            getNode(it).map { getChildLinkFromString(it.roleInParent) }
        }.distinct()
    }

    override fun getAllChildren(parentId: Long): Observable<Long> {
        return getNode(parentId).flatMapIterable { it.childrenIdArray.toList() }.loadPrefetch()
    }

    override fun getAllReferenceTargetRefs(sourceId: Long): Observable<Pair<IReferenceLinkReference, INodeReference>> {
        return getNode(sourceId).flatMapIterable { data ->
            data.referenceRoles.mapIndexed { index, role ->
                getReferenceLinkFromString(role) to data.referenceTargets[index].convertReference()
            }
        }
    }

    override fun getPropertyValue(nodeId: Long, role: IPropertyReference): Maybe<String> {
        return getNode(nodeId).map { node ->
            node.getPropertyValue(role.key())
        }.notNull()
    }

    override fun getChildren(parentId: Long, role: IChildLinkReference): Observable<Long> {
        val roleString = role.key()
        return getAllChildren(parentId).flatMapSingle { getNode(it) }.filter { it.roleInParent == roleString }.map { it.id }.loadPrefetch()
    }

    private fun IRoleReference.key() = getRoleKey(this)
    private fun IChildLinkReference.key() = if (this is NullChildLinkReference) null else getRoleKey(this)

    fun getRoleKey(role: IChildLinkReference): String? = if (role is NullChildLinkReference) null else getRoleKey(role as IRoleReference)
    fun getRoleKey(role: IRoleReference): String {
        return if (treeData.usesRoleIds) {
            role.getIdOrName()
        } else {
            role.getNameOrId()
        }
    }
    private fun getPropertyFromString(value: String): IPropertyReference {
        return if (treeData.usesRoleIds) IPropertyReference.fromId(value) else IPropertyReference.fromName(value)
    }
    private fun getReferenceLinkFromString(value: String): IReferenceLinkReference {
        return if (treeData.usesRoleIds) IReferenceLinkReference.fromId(value) else IReferenceLinkReference.fromName(value)
    }
    private fun getChildLinkFromString(value: String?): IChildLinkReference {
        return when {
            value == null -> NullChildLinkReference
            treeData.usesRoleIds -> IChildLinkReference.fromId(value)
            else -> IChildLinkReference.fromName(value)
        }
    }

    private fun Maybe<CPHamtNode>.assertNotEmpty(): Single<CPHamtNode> = asSingleOrError { IllegalStateException("Tree is empty. It should contain at least the root node.") }
    private fun Maybe<CPHamtNode>.newTree() = withNewNodesMap(assertNotEmpty())
    private fun Single<CPHamtNode>.newTree() = withNewNodesMap(this)

    private fun withNewNodesMap(newMap: Single<CPHamtNode>): Single<AsyncTree> {
        return newMap.map {
            val newIdToHash = KVEntryReference(it)
            if (newIdToHash == treeData.idToHash) return@map this
            AsyncTree(CPTree(treeData.id, newIdToHash, treeData.usesRoleIds), store)
        }
    }

    private fun updateNode(nodeId: Long, transform: (CPNode) -> Single<CPNode>): Single<AsyncTree> {
        return updateNodeInMap(nodesMap.query(), nodeId, transform).newTree()
    }

    private fun updateNodeInMap(nodesMap: Single<CPHamtNode>, nodeId: Long, transform: (CPNode) -> Single<CPNode>): Single<CPHamtNode> {
        return nodesMap.flatMap { oldMap ->
            oldMap.get(nodeId, store)
                .asSingleOrError { throw IllegalArgumentException("Node not found: ${nodeId.toString(16)}") }
                .flatMap { it.query() }
                .map { oldMap to it }
                .flatMap { (oldMap, nodeData) -> transform(nodeData).flatMap { newData -> oldMap.put(newData, store) } }
        }
    }

    override fun addNewChildren(
        parentId: Long,
        role: IChildLinkReference,
        index: Int,
        newIds: LongArray,
        concepts: Array<ConceptReference>,
    ): Single<IAsyncMutableTree> {
        val mapIncludingNewNodes = newIds.zip(concepts).asObservable().fold(nodesMap.query()) { nodesMap, (childId, concept) ->
            val childData = CPNode.create(
                childId,
                concept.getUID().takeIf { it != NullConcept.getUID() },
                parentId,
                getRoleKey(role),
                LongArray(0),
                arrayOf(),
                arrayOf(),
                arrayOf(),
                arrayOf(),
            )
            nodesMap.flatMap {
                it.get(childId, store)
                    .assertEmpty { "Node with ID ${childId.toString(16)} already exists" }
                    .andThen(it.put(childData, store))
            }
        }.flatten()

        val newParentData = insertChildrenIntoParentData(parentId, index, newIds, role)

        return mapIncludingNewNodes.zipWith(newParentData) { map, parentData ->
            map.put(parentData, store)
        }.flatten().newTree()
    }

    private fun insertChildrenIntoParentData(parentId: Long, index: Int, newIds: LongArray, role: IChildLinkReference): Single<CPNode> {
        return getNode(parentId).flatMap { parentData ->
            insertChildrenIntoParentData(parentData, index, newIds, role)
        }
    }

    private fun insertChildrenIntoParentData(parentData: CPNode, index: Int, newIds: LongArray, role: IChildLinkReference): Single<CPNode> {
        return if (index == -1) {
            singleOf(parentData.childrenIdArray + newIds)
        } else {
            getChildren(parentData.id, role).toList().map { childrenInRole ->
                if (index > childrenInRole.size) throw RuntimeException("Invalid index $index. There are only ${childrenInRole.size} nodes in ${parentData.id.toString(16)}.$role")
                if (index == childrenInRole.size) {
                    parentData.childrenIdArray + newIds
                } else {
                    val indexInAll = parentData.childrenIdArray.indexOf(childrenInRole[index])
                    insert(
                        parentData.childrenIdArray,
                        indexInAll,
                        newIds,
                    )
                }
            }
        }.map { newChildrenArray ->
            CPNode.create(
                parentData.id,
                parentData.concept,
                parentData.parentId,
                parentData.roleInParent,
                newChildrenArray,
                parentData.propertyRoles,
                parentData.propertyValues,
                parentData.referenceRoles,
                parentData.referenceTargets,
            )
        }
    }

    override fun moveChild(
        newParentId: Long,
        newRole: IChildLinkReference,
        newIndex: Int,
        childId: Long,
    ): Single<IAsyncMutableTree> {
        require(childId != ITree.ROOT_ID) { "Moving the root node is not allowed" }
        val checkCycle = getAncestors(newParentId, true).toList().map { ancestors ->
            if (ancestors.contains(childId)) {
                throw ContainmentCycleException(newParentId, childId)
            }
        }.asCompletable()

        val oldParent = getParent(childId).asSingleOrError {
            IllegalArgumentException("Cannot move node without parent: ${childId.toString(16)}")
        }
        val adjustedIndex = oldParent.flatMap { oldParentId ->
            if (oldParentId != newParentId) {
                singleOf(newIndex)
            } else {
                getRole(childId).flatMap { oldRole ->
                    if (getRoleKey(oldRole) != getRoleKey(newRole)) {
                        singleOf(newIndex)
                    } else {
                        getChildren(oldParentId, oldRole).toList().map { oldSiblings ->
                            val oldIndex = oldSiblings.indexOf(childId)
                            if (oldIndex < newIndex) newIndex - 1 else newIndex
                        }
                    }
                }
            }
        }

        val newTree = oldParent.zipWith(adjustedIndex) { oldParentId, adjustedIndex ->
            val withChildRemoved = updateNode(oldParentId) {
                singleOf(it.withChildRemoved(childId))
            }
            val withChildAdded = withChildRemoved.flatMap { tree ->
                tree.updateNode(newParentId) {
                    tree.insertChildrenIntoParentData(it, adjustedIndex, longArrayOf(childId), newRole)
                }
            }
            val withUpdatedRole = withChildAdded.flatMap { tree ->
                tree.updateNode(childId) {
                    singleOf(it.withContainment(newParentId, getRoleKey(newRole)))
                }
            }
            withUpdatedRole
        }.flatten()
        return checkCycle.andThen(newTree)
    }

    override fun setConcept(nodeId: Long, concept: ConceptReference): Single<IAsyncMutableTree> {
        return updateNode(nodeId) { singleOf(it.withConcept(concept.getUID().takeIf { it != NullConcept.getUID() })) }
    }

    override fun setPropertyValue(nodeId: Long, role: IPropertyReference, value: String?): Single<IAsyncMutableTree> {
        return updateNode(nodeId) { singleOf(it.withPropertyValue(getRoleKey(role), value)) }
    }

    override fun setReferenceTarget(sourceId: Long, role: IReferenceLinkReference, target: INodeReference?): Single<IAsyncMutableTree> {
        val refData: CPNodeRef? = when (target) {
            null -> null
            is LocalPNodeReference -> {
                local(target.id)
            }
            is PNodeReference -> {
                if (target.branchId.isEmpty() || target.branchId == treeData.id) {
                    local(target.id)
                } else {
                    global(target.branchId, target.id)
                }
            }
            else -> foreign(INodeReferenceSerializer.serialize(target))
        }
        return updateNode(sourceId) { singleOf(it.withReferenceTarget(getRoleKey(role), refData)) }
    }

    override fun setReferenceTarget(sourceId: Long, role: IReferenceLinkReference, targetId: Long): Single<IAsyncMutableTree> {
        return updateNode(sourceId) { singleOf(it.withReferenceTarget(getRoleKey(role), local(targetId))) }
    }

    override fun deleteNodes(nodeIds: LongArray): Single<IAsyncMutableTree> {
        if (nodeIds.size == 1) return deleteNodeRecursive(nodeIds[0])
        return nodeIds.asObservable().fold(singleOf(this)) { acc, nodeId -> acc.flatMap { it.deleteNodeRecursive(nodeId) } }.flatten()
    }

    private fun deleteNodeRecursive(nodeId: Long): Single<AsyncTree> {
        val mapWithoutRemovedNodes: Single<CPHamtNode> = getDescendantsAndSelf(nodeId).fold(nodesMap.query()) { map, node -> map.flatMap { it.remove(node, store).assertNotEmpty() } }.flatten()
        val parent = getParent(nodeId).asSingleOrError { IllegalArgumentException("Cannot delete node without parent: ${nodeId.toString(16)}") }

        return parent.flatMap { parentId ->
            updateNodeInMap(mapWithoutRemovedNodes, parentId) { singleOf(it.withChildRemoved(nodeId)) }
        }.newTree()
    }
}

@Deprecated("Prefetching will be replaced by usages of IAsyncNode")
private data class PrefetchNodeGoal(val tree: AsyncTree, val nodeId: Long) : IPrefetchGoal {
    override fun loadRequest(bulkQuery: IBulkQuery): Observable<Any?> {
        return tree.getAllChildren(nodeId).flatMapSingle { tree.getNode(it) }
    }

    override fun toString(): String {
        return nodeId.toString(16)
    }
}

class ContainmentCycleException(val newParentId: Long, val childId: Long) :
    RuntimeException(
        "${newParentId.toString(16)} is a descendant of ${childId.toString(16)}." +
            " Moving the node would create a cycle in the containment hierarchy.",
    )

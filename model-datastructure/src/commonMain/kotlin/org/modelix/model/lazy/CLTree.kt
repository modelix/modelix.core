/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.lazy

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.async.IAsyncTree
import org.modelix.model.api.async.IAsyncValue
import org.modelix.model.async.AsyncObjectStoreAdapter
import org.modelix.model.async.AsyncTree
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.asStore
import org.modelix.model.lazy.COWArrays.insert
import org.modelix.model.lazy.COWArrays.remove
import org.modelix.model.persistent.CPHamtInternal
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import org.modelix.model.persistent.CPNodeRef.Companion.foreign
import org.modelix.model.persistent.CPNodeRef.Companion.global
import org.modelix.model.persistent.CPNodeRef.Companion.local
import org.modelix.model.persistent.CPTree

fun createNewTreeData(
    store: IDeserializingKeyValueStore,
    repositoryId: RepositoryId = RepositoryId.random(),
    useRoleIds: Boolean = true,
): CPTree {
    val root = CPNode.create(
        1,
        null,
        0,
        null,
        LongArray(0),
        arrayOf(),
        arrayOf(),
        arrayOf(),
        arrayOf(),
    )
    return CPTree(
        repositoryId.id,
        KVEntryReference<CPHamtNode>(CPHamtInternal.createEmpty().put(root.id, KVEntryReference<CPNode>(root), store)!!),
        useRoleIds,
    )
}

class CLTree(val data: CPTree, val store: IDeserializingKeyValueStore) : ITree, IBulkTree {

    constructor(store: IDeserializingKeyValueStore, useRoleIds: Boolean = true) : this(createNewTreeData(store, useRoleIds = useRoleIds), store)

    constructor(data: CPTree?, repositoryId: RepositoryId?, store: IDeserializingKeyValueStore, useRoleIds: Boolean = true) : this(
        data ?: createNewTreeData(store, repositoryId ?: RepositoryId.random(), useRoleIds),
        store,
    )

    fun withNewNodesMap(newMap: CPHamtNode) = CLTree(CPTree(data.id, KVEntryReference(newMap), data.usesRoleIds), store)

    private val asyncTree: IAsyncTree by lazy { AsyncTree({ data }, AsyncObjectStoreAdapter(store.newBulkQuery())) }
    override fun asAsyncTree() = asyncTree

    override fun usesRoleIds(): Boolean {
        return data.usesRoleIds
    }

    override fun getId(): String {
        return data.id
    }

    fun getSize(): Long {
        return (nodesMap ?: return 0L).calculateSize(store.newBulkQuery()).awaitBlocking()
    }

    @Deprecated("BulkQuery is now responsible for prefetching")
    fun prefetchAll() {
        store.prefetch(hash)
    }

    val hash: String
        get() = data.hash

    val nodesMap: CPHamtNode?
        get() = data.idToHash.getValue(store)

    protected fun storeElement(node: CPNode, id2hash: CPHamtNode): CPHamtNode {
        val data = node
        var newMap = id2hash.put(node.id, KVEntryReference(data), store)
        if (newMap == null) {
            newMap = CPHamtInternal.createEmpty()
        }
        return newMap
    }

    val root: CPNode?
        get() = resolveElement(ITree.ROOT_ID)

    override fun setProperty(nodeId: Long, role: String, value: String?): ITree {
        var newIdToHash = nodesMap
        val newNodeData = resolveElement(nodeId)!!.withPropertyValue(role, value)
        newIdToHash = newIdToHash!!.put(newNodeData, store)
        return withNewNodesMap(newIdToHash!!)
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConceptReference?): ITree {
        return addNewChildren(parentId, role, index, longArrayOf(childId), arrayOf(concept))
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?): ITree {
        return addNewChild(parentId, role, index, childId, concept?.getReference())
    }

    override fun addNewChildren(parentId: Long, role: String?, index: Int, newIds: LongArray, concepts: Array<IConcept?>): ITree {
        return addNewChildren(parentId, role, index, newIds, concepts.map { it?.getReference() }.toTypedArray())
    }

    override fun addNewChildren(parentId: Long, role: String?, index: Int, newIds: LongArray, concepts: Array<IConceptReference?>): ITree {
        for (childId in newIds) {
            if (containsNode(childId)) {
                throw DuplicateNodeId("Node ID already exists: ${childId.toString(16)}")
            }
        }
        return createNewNodes(newIds, concepts).addChildren(parentId, role, index, newIds)
    }

    override fun deleteNodes(nodeIds: LongArray): ITree {
        throw UnsupportedOperationException("Not implemented yet")
    }

    /**
     * Incomplete operation. The node is added to the map, but not attached anywhere in the tree.
     */
    protected fun createNewNodes(nodeId: LongArray, concept: Array<IConceptReference?>): CLTree {
        var newIdToHash: CPHamtNode? = nodesMap
        val newChildData = Array<CPNode>(nodeId.size) { index ->
            CPNode.create(
                nodeId[index],
                concept[index]?.getUID(),
                0,
                null,
                LongArray(0),
                arrayOf(),
                arrayOf(),
                arrayOf(),
                arrayOf(),
            )
        }
        for (newChild in newChildData) {
            // TODO .putAll method for bulk operations?
            newIdToHash = newIdToHash!!.put(newChild, store)
        }
        return withNewNodesMap(newIdToHash!!)
    }

    /**
     * Incomplete operation. The child has to exist in the map, but not be part of the tree.
     */
    protected fun addChildren(parentId: Long, role: String?, index: Int, childIds: LongArray): ITree {
        val parent = resolveElement(parentId)
        var newIdToHash = nodesMap
        val childData = childIds.map { resolveElement(it)!! }
        val newChildData = childData.map {
            CPNode.create(
                it.id,
                it.concept,
                parentId,
                role,
                it.childrenIdArray,
                it.propertyRoles,
                it.propertyValues,
                it.referenceRoles,
                it.referenceTargets,
            )
        }
        for (newChild in newChildData) {
            // TODO .putAll method for bulk operations?
            newIdToHash = newIdToHash!!.put(newChild, store)
        }
        var newChildrenArray = parent!!.childrenIdArray
        newChildrenArray = if (index == -1) {
            newChildrenArray + childData.map { it.id }
        } else {
            val childrenInRole = getChildren(parentId, role).toList()
            if (index > childrenInRole.size) throw RuntimeException("Invalid index $index. There are only ${childrenInRole.size} nodes in ${parentId.toString(16)}.$role")
            if (index == childrenInRole.size) {
                newChildrenArray + childData.map { it.id }
            } else {
                val indexInAll = newChildrenArray.indexOf(childrenInRole[index])
                insert(
                    newChildrenArray,
                    indexInAll,
                    childData.map { it.id }.toLongArray(),
                )
            }
        }
        val newParentData = CPNode.create(
            parent.id,
            parent.concept,
            parent.parentId,
            parent.roleInParent,
            newChildrenArray,
            parent.propertyRoles,
            parent.propertyValues,
            parent.referenceRoles,
            parent.referenceTargets,
        )
        newIdToHash = newIdToHash!!.put(newParentData, store)
        return withNewNodesMap(newIdToHash!!)
    }

    override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?): ITree {
        val source = resolveElement(sourceId)!!
        val refData: CPNodeRef? = when (target) {
            null -> null
            is LocalPNodeReference -> {
                local(target.id)
            }
            is PNodeReference -> {
                if (target.branchId.isEmpty() || target.branchId == getId()) {
                    local(target.id)
                } else {
                    global(target.branchId, target.id)
                }
            }
            else -> foreign(INodeReferenceSerializer.serialize(target))
        }
        var newIdToHash = nodesMap
        val newNodeData = source.withReferenceTarget(role, refData)
        newIdToHash = newIdToHash!!.put(newNodeData, store)
        return withNewNodesMap(newIdToHash!!)
    }

    override fun setConcept(nodeId: Long, concept: IConceptReference?): ITree {
        // manually throw NullPointerException for consistency, should be replaced for all methods in the future.
        val node = resolveElement(nodeId) ?: throw NullPointerException("nodeId could not be resolved. id=$nodeId")
        val newData = CPNode.create(
            node.id,
            concept?.getUID(),
            node.parentId,
            node.roleInParent,
            node.childrenIdArray,
            node.propertyRoles,
            node.propertyValues,
            node.referenceRoles,
            node.referenceTargets,
        )
        val nodesMap = checkNotNull(nodesMap) { "nodesMap not found" }

        val newIdToHash = checkNotNull(nodesMap.put(newData, store)) { "could not put new data" }
        return withNewNodesMap(newIdToHash)
    }

    override fun deleteNode(nodeId: Long): ITree {
        return deleteNode(nodeId, true)
    }

    /**
     * Incomplete operation.
     * If recursive==false, the result is an inconsistent tree.
     * Make sure to delete the descendants or add them to the tree at a new location.
     */
    protected fun deleteNode(nodeId: Long, recursive: Boolean): CLTree {
        val node = resolveElement(nodeId)
        val parent = resolveElement(node!!.parentId)
        var newIdToHash: CPHamtNode = nodesMap
            ?: throw RuntimeException("nodesMap not found for hash: " + this.data.idToHash)
        val newParentData = CPNode.create(
            parent!!.id,
            parent.concept,
            parent.parentId,
            parent.roleInParent,
            remove(parent.childrenIdArray, node.id),
            parent.propertyRoles,
            parent.propertyValues,
            parent.referenceRoles,
            parent.referenceTargets,
        )
        newIdToHash = newIdToHash.put(newParentData, store)
            ?: throw RuntimeException("Unexpected empty nodes map. There should be at least the root node.")
        if (recursive) {
            newIdToHash = deleteElements(node, newIdToHash)
                ?: throw RuntimeException("Unexpected empty nodes map. There should be at least the root node.")
        }
        return withNewNodesMap(newIdToHash)
    }

    override fun containsNode(nodeId: Long): Boolean {
        return nodesMap!!.get(nodeId, store) != null
    }

    override fun getAllChildren(parentId: Long): Iterable<Long> {
        return getAllChildren(parentId, store.newBulkQuery()).awaitBlocking()
    }

    fun getAllChildren(parentId: Long, bulkQuery: IBulkQuery): IAsyncValue<Iterable<Long>> {
        return resolveElement(parentId, bulkQuery).map {
            it?.childrenIdArray?.asIterable() ?: emptyList()
        }
    }

    private data class PrefetchNodeGoal(val tree: CLTree, val nodeId: Long) : IPrefetchGoal {
        override fun loadRequest(bulkQuery: IBulkQuery) {
            tree.getAllChildren(nodeId, bulkQuery).map { it.forEach { tree.resolveElement(it, bulkQuery) } }
        }

        override fun toString(): String {
            return nodeId.toString(16)
        }
    }

    override fun getDescendants(root: Long, includeSelf: Boolean): Iterable<CLNode> {
        val parent = resolveElement(root)
        return getDescendants(parent!!, store.newBulkQuery(), includeSelf).awaitBlocking().map { CLNode(this, it) }
    }

    override fun getDescendants(rootIds: Iterable<Long>, includeSelf: Boolean): Iterable<CLNode> {
        val bulkQuery = store.newBulkQuery()
        val roots: IAsyncValue<List<CPNode>> = resolveElements(rootIds.toList(), bulkQuery)
        val descendants = roots.thenRequest { bulkQuery.flatMap(it) { getDescendants(it, bulkQuery, includeSelf) } }
        return descendants.awaitBlocking().flatten().map { CLNode(this, it) }
    }

    override fun getAncestors(nodeIds: Iterable<Long>, includeSelf: Boolean): Set<Long> {
        val bulkQuery = store.newBulkQuery()
        val nodes: IAsyncValue<List<CPNode>> = resolveElements(nodeIds, bulkQuery)
        val ancestors = nodes.thenRequest { bulkQuery.flatMap(it) { getAncestors(it, bulkQuery, includeSelf) } }
        val result = HashSet<Long>()
        ancestors.awaitBlocking().forEach { result.addAll(it.map { it.id }) }
        return result
    }

    override fun getChildren(parentId: Long, role: String?): Iterable<Long> {
        val parent = resolveElement(parentId)
        val children = getChildren(parent!!, store.newBulkQuery()).awaitBlocking()
        return children
            .filter { it.roleInParent == role }
            .map { it.id }
    }

    override fun getChildRoles(sourceId: Long): Iterable<String?> {
        val parent = resolveElement(sourceId)
        val children: Iterable<CPNode> = getChildren(parent!!, store.newBulkQuery()).awaitBlocking()
        return children.map { it.roleInParent }.distinct()
    }

    override fun getConcept(nodeId: Long): IConcept? {
        try {
            return getConceptReference(nodeId)?.let { ILanguageRepository.resolveConcept(it) }
        } catch (e: Exception) {
            throw RuntimeException("Unable to find concept for node $nodeId", e)
        }
    }

    override fun getConceptReference(nodeId: Long): IConceptReference? {
        try {
            val node = resolveElement(nodeId)
            return node!!.concept?.let { ConceptReference(it) }
        } catch (e: Exception) {
            throw RuntimeException("Unable to find concept for node $nodeId", e)
        }
    }

    override fun getParent(nodeId: Long): Long {
        val node = resolveElement(nodeId)
        return node!!.parentId
    }

    override fun getProperty(nodeId: Long, role: String): String? {
        val node = resolveElement(nodeId)
        return node!!.getPropertyValue(role)
    }

    override fun getPropertyRoles(sourceId: Long): Iterable<String> {
        val node = resolveElement(sourceId)
        return node!!.propertyRoles.toList()
    }

    override fun getReferenceRoles(sourceId: Long): Iterable<String> {
        val node = resolveElement(sourceId)
        return node!!.referenceRoles.toList()
    }

    override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
        val node = resolveElement(sourceId)!!
        val targetRef = node.getReferenceTarget(role)
        return when {
            targetRef == null -> null
            targetRef.isLocal -> PNodeReference(targetRef.elementId, this.getId())
            targetRef is CPNodeRef.ForeignRef -> org.modelix.model.api.INodeReferenceSerializer.deserialize(targetRef.serializedRef)
            else -> throw UnsupportedOperationException("Unsupported reference: $targetRef")
        }
    }

    override fun getRole(nodeId: Long): String? {
        val node = resolveElement(nodeId)
        return node!!.roleInParent
    }

    override fun moveChild(targetParentId: Long, targetRole: String?, targetIndex_: Int, childId: Long): ITree {
        if (childId == ITree.ROOT_ID) throw RuntimeException("Moving the root node is not allowed")
        var ancestor = targetParentId
        while (ancestor != ITree.ROOT_ID) {
            if (ancestor == childId) {
                throw RuntimeException("${targetParentId.toString(16)} is a descendant of ${childId.toString(16)}")
            }
            ancestor = getParent(ancestor)
        }

        var targetIndex = targetIndex_
        if (targetIndex != -1) {
            val oldParent = getParent(childId)
            if (oldParent == targetParentId) {
                val oldRole = getRole(childId)
                if (oldRole == targetRole) {
                    val oldIndex = getChildren(oldParent, oldRole).indexOf(childId)
                    if (oldIndex == targetIndex) {
                        return this
                    }
                    if (oldIndex < targetIndex) {
                        targetIndex--
                    }
                }
            }
        }
        return deleteNode(childId, false).addChildren(targetParentId, targetRole, targetIndex, longArrayOf(childId))
    }

    override fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor) {
        val bulkQuery = store.newBulkQuery()
        visitChanges(oldVersion, visitor, bulkQuery)
        bulkQuery.executeQuery()
    }

    fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor, bulkQuery: IBulkQuery) {
        require(oldVersion is CLTree) { "Diff is only supported between two instances of CLTree" }
        if (data.idToHash == oldVersion.data.idToHash) return
        val changesOnly = visitor !is ITreeChangeVisitorEx
        nodesMap!!.visitChanges(
            oldVersion.nodesMap,
            object : CPHamtNode.IChangeVisitor {
                private val childrenChangeEvents = HashSet<Pair<Long, String?>>()

                private fun notifyChildrenChange(parent: Long, role: String?) {
                    if (childrenChangeEvents.add(parent to role)) visitor.childrenChanged(parent, role)
                }

                override fun visitChangesOnly(): Boolean {
                    return changesOnly
                }

                override fun entryAdded(key: Long, value: KVEntryReference<CPNode>): IAsyncValue<Unit> {
                    return if (visitor is ITreeChangeVisitorEx) {
                        createElement(value, bulkQuery).map { element ->
                            visitor.nodeAdded(element!!.id)
                        }
                    } else {
                        IAsyncValue.UNIT
                    }
                }

                override fun entryRemoved(key: Long, value: KVEntryReference<CPNode>): IAsyncValue<Unit> {
                    return if (visitor is ITreeChangeVisitorEx) {
                        oldVersion.createElement(value, bulkQuery).map { element ->
                            visitor.nodeRemoved(element!!.id)
                        }
                    } else {
                        IAsyncValue.UNIT
                    }
                }

                override fun entryChanged(key: Long, oldValue: KVEntryReference<CPNode>, newValue: KVEntryReference<CPNode>): IAsyncValue<Unit> {
                    return oldVersion.createElement(oldValue, bulkQuery).map { oldElement ->
                        createElement(newValue, bulkQuery).map { newElement ->
                            if (oldElement!!::class != newElement!!::class) {
                                throw RuntimeException("Unsupported type change of node " + key + "from " + oldElement::class.simpleName + " to " + newElement::class.simpleName)
                            }
                            if (oldElement.parentId != newElement.parentId) {
                                visitor.containmentChanged(key)
                            } else if (oldElement.roleInParent != newElement.roleInParent) {
                                visitor.containmentChanged(key)
                                notifyChildrenChange(oldElement.parentId, oldElement.roleInParent)
                                notifyChildrenChange(newElement.parentId, newElement.roleInParent)
                            }
                            if (oldElement.concept != newElement.concept) {
                                visitor.conceptChanged(key)
                            }
                            oldElement.propertyRoles.asSequence()
                                .plus(newElement.propertyRoles.asSequence())
                                .distinct()
                                .forEach { role: String ->
                                    if (oldElement.getPropertyValue(role) != newElement.getPropertyValue(role)) {
                                        visitor.propertyChanged(newElement.id, role)
                                    }
                                }
                            oldElement.referenceRoles.asSequence()
                                .plus(newElement.referenceRoles.asSequence())
                                .distinct()
                                .forEach { role: String ->
                                    if (oldElement.getReferenceTarget(role) != newElement.getReferenceTarget(role)) {
                                        visitor.referenceChanged(newElement.id, role)
                                    }
                                }

                            bulkQuery.flatMap(listOf(oldVersion.getChildren(oldElement, bulkQuery), getChildren(newElement, bulkQuery))) { it }.onReceive { childrenLists ->
                                val (oldChildrenList, newChildrenList) = childrenLists
                                val oldChildren: MutableMap<String?, MutableList<CPNode>> = HashMap()
                                val newChildren: MutableMap<String?, MutableList<CPNode>> = HashMap()
                                oldChildrenList.forEach { oldChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
                                newChildrenList.forEach { newChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }

                                val roles: MutableSet<String?> = HashSet()
                                roles.addAll(oldChildren.keys)
                                roles.addAll(newChildren.keys)
                                for (role in roles) {
                                    val oldChildrenInRole = oldChildren[role]
                                    val newChildrenInRole = newChildren[role]
                                    val oldValues = oldChildrenInRole?.map { it.id }
                                    val newValues = newChildrenInRole?.map { it.id }
                                    if (oldValues != newValues) {
                                        notifyChildrenChange(newElement.id, role)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bulkQuery,
        )
    }

    protected fun deleteElements(node: CPNode, idToHash: CPHamtNode): CPHamtNode? {
        var newIdToHash: CPHamtNode? = idToHash
        for (childId in node.getChildrenIds()) {
            if (newIdToHash == null) throw RuntimeException("node $childId not found")
            val childHash: KVEntryReference<CPNode> = newIdToHash.get(childId, store) ?: throw RuntimeException("node $childId not found")
            val child = childHash.getValue(store)
            newIdToHash = deleteElements(child, newIdToHash)
        }
        if (newIdToHash == null) throw RuntimeException("node ${node.id} not found")
        newIdToHash = newIdToHash.remove(node.id, store)
        return newIdToHash
    }

    fun resolveElement(ref: CPNodeRef?): CPNode? {
        if (ref == null) {
            return null
        }
        if (ref.isGlobal && ref.treeId != data.id) {
            throw RuntimeException("Cannot resolve " + ref + " in tree " + data.id)
        }
        if (ref.isLocal) {
            return resolveElement(ref.elementId)
        }
        throw RuntimeException("Unsupported reference type: $ref")
    }

    fun resolveElement(id: Long): CPNode? {
        return resolveElement(id, NonBulkQuery(store)).awaitBlocking()
    }

    fun resolveElement(id: Long, bulkQuery: IBulkQuery): IAsyncValue<CPNode?> {
        if (id == 0L) {
            return bulkQuery.constant(null)
        }
        val hash = nodesMap!!.get(id, bulkQuery)
        return hash.thenRequest {
            if (it == null) throw NodeNotFoundException(id)
            createElement(it, bulkQuery)
        }
    }

    fun resolveElements(ids_: Iterable<Long>, bulkQuery: IBulkQuery): IAsyncValue<List<CPNode>> {
        val ids = ids_.toList()
        val a: IAsyncValue<List<KVEntryReference<CPNode>?>> = nodesMap!!.getAll(ids, bulkQuery)
        val b: IAsyncValue<List<KVEntryReference<CPNode>>> = a.map { hashes: List<KVEntryReference<CPNode>?> ->
            hashes.mapIndexed { index, s -> s ?: throw NodeNotFoundException(ids[index]) }
        }
        return b.thenRequest { hashes -> createElements(hashes, bulkQuery) }
    }

    fun createElement(hash: KVEntryReference<CPNode>?, query: IBulkQuery): IAsyncValue<CPNode?> {
        return if (hash == null) {
            query.constant(null)
        } else {
            query.query(hash).also {
                it.onReceive { node ->
                    if (node == null) return@onReceive
                    val children: LongArray = node.childrenIdArray
                    if (children.isNotEmpty()) {
                        children.reversedArray().forEach {
                            query.offerPrefetch(PrefetchNodeGoal(this, it))
                        }
                    }
                    if (node.parentId != 0L) {
                        query.offerPrefetch(PrefetchNodeGoal(this, node.parentId))
                    }
                    node.referenceTargets.asSequence().filter { it.isLocal }.forEach { target ->
                        query.offerPrefetch(PrefetchNodeGoal(this, target.elementId))
                    }
                }
            }
        }
    }

    fun createElement(hash: KVEntryReference<CPNode>?): CPNode? {
        return createElement(hash, NonBulkQuery(store)).awaitBlocking()
    }

    fun createElements(hashes: List<KVEntryReference<CPNode>>, bulkQuery: IBulkQuery): IAsyncValue<List<CPNode>> {
        return bulkQuery.flatMap(hashes) { hash: KVEntryReference<CPNode> ->
            bulkQuery.query(hash).map { n -> n!! }
        }
    }

    override fun toString(): String {
        return "CLTree[$hash]"
    }

    private fun getChildren(node: CPNode, bulkQuery: IBulkQuery): IAsyncValue<List<CPNode>> {
        return resolveElements(node.getChildrenIds().toList(), bulkQuery).map { elements -> elements }
    }

    private fun getDescendants(node: CPNode, bulkQuery: IBulkQuery, includeSelf: Boolean): IAsyncValue<Iterable<CPNode>> {
        return if (includeSelf) {
            getDescendants(node, bulkQuery, false)
                .map { descendants -> (sequenceOf(node) + descendants).asIterable() }
        } else {
            getChildren(node, bulkQuery).thenRequest { children: Iterable<CPNode> ->
                val d: IAsyncValue<Iterable<CPNode>> = bulkQuery
                    .flatMap(children) { child: CPNode -> getDescendants(child, bulkQuery, true) }
                    .map { it.flatten() }
                d
            }
        }
    }

    private fun getAncestors(node: CPNode, bulkQuery: IBulkQuery, includeSelf: Boolean): IAsyncValue<List<CPNode>> {
        return if (includeSelf) {
            getAncestors(node, bulkQuery, false).map { ancestors -> (listOf(node) + ancestors) }
        } else {
            val parentNode = resolveElement(node.parentId)
            if (parentNode == null) {
                bulkQuery.constant(listOf())
            } else {
                getAncestors(parentNode, bulkQuery, true)
            }
        }
    }

    companion object {
        fun builder(store: IDeserializingKeyValueStore) = Builder(store)
    }

    class Builder(var store: IDeserializingKeyValueStore) {
        private var repositoryId: RepositoryId? = null
        private var useRoleIds: Boolean = false

        fun useRoleIds(value: Boolean = true): Builder {
            this.useRoleIds = value
            return this
        }

        fun repositoryId(id: RepositoryId): Builder {
            this.repositoryId = id
            return this
        }

        fun repositoryId(id: String): Builder = repositoryId(RepositoryId(id))

        fun build(): CLTree {
            return CLTree(
                data = null as CPTree?,
                repositoryId = repositoryId ?: RepositoryId.random(),
                store = store,
                useRoleIds = useRoleIds,
            )
        }
    }
}

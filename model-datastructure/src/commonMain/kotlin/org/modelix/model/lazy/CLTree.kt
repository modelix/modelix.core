/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.lazy

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.IRole
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.tryResolve
import org.modelix.model.lazy.COWArrays.add
import org.modelix.model.lazy.COWArrays.insert
import org.modelix.model.lazy.COWArrays.remove
import org.modelix.model.lazy.RepositoryId.Companion.random
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNode.Companion.create
import org.modelix.model.persistent.CPNodeRef
import org.modelix.model.persistent.CPNodeRef.Companion.foreign
import org.modelix.model.persistent.CPNodeRef.Companion.global
import org.modelix.model.persistent.CPNodeRef.Companion.local
import org.modelix.model.persistent.CPTree

class CLTree : ITree, IBulkTree {
    val store: IDeserializingKeyValueStore
    val data: CPTree

    constructor(id: RepositoryId?, store: IDeserializingKeyValueStore) : this(null, id, store)
    constructor(hash: String?, store: IDeserializingKeyValueStore) : this(if (hash == null) null else store.get<CPTree>(hash) { CPTree.deserialize(it) }, store)
    constructor(store: IDeserializingKeyValueStore) : this(null as CPTree?, store)
    constructor(data: CPTree?, store_: IDeserializingKeyValueStore) : this(data, null as RepositoryId?, store_)
    constructor(data: CPTree?, repositoryId_: RepositoryId?, store_: IDeserializingKeyValueStore, useRoleIds: Boolean = false) {
        var repositoryId = repositoryId_
        var store = store_
        if (data == null) {
            if (repositoryId == null) {
                repositoryId = random()
            }
            val root = CLNode(
                this,
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
            val idToHash = storeElement(root, CLHamtInternal.createEmpty(store))
            this.data = CPTree(repositoryId.id, KVEntryReference(idToHash.getData()), useRoleIds)
        } else {
            this.data = data
        }

        this.store = store

        // TODO remove
        this.nodesMap!![ITree.ROOT_ID]
    }

    private constructor(treeId_: String, idToHash: CLHamtNode, store: IDeserializingKeyValueStore, usesRoleIds: Boolean) {
        var treeId: String? = treeId_
        if (treeId == null) {
            treeId = random().id
        }
        data = CPTree(treeId, KVEntryReference(idToHash.getData()), usesRoleIds)
        this.store = store

        // TODO remove
        this.nodesMap!![ITree.ROOT_ID]
    }

    override fun usesRoleIds(): Boolean {
        return data.usesRoleIds
    }

    override fun getId(): String {
        return data.id
    }

    fun getSize(): Long {
        return (nodesMap ?: return 0L).calculateSize(BulkQuery(store)).execute()
    }

    fun prefetchAll() {
        store.prefetch(hash)
    }

    val hash: String
        get() = data.hash

    val nodesMap: CLHamtNode?
        get() = CLHamtNode.create(data.idToHash.getValue(store), store)

    protected fun storeElement(node: CLNode, id2hash: CLHamtNode): CLHamtNode {
        val data = node.getData()
        var newMap = id2hash.put(node.id, KVEntryReference(data))
        if (newMap == null) {
            newMap = CLHamtInternal.createEmpty(store)
        }
        return newMap
    }

    val root: CLNode?
        get() = resolveElement(ITree.ROOT_ID)

    override fun setProperty(nodeId: Long, role: String, value: String?): ITree {
        checkPropertyRoleId(nodeId, role)
        var newIdToHash = nodesMap
        val newNodeData = resolveElement(nodeId)!!.getData().withPropertyValue(role, value)
        newIdToHash = newIdToHash!!.put(newNodeData)
        return CLTree(data.id, newIdToHash!!, store, data.usesRoleIds)
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConceptReference?): ITree {
        checkChildRoleId(parentId, role)
        if (containsNode(childId)) {
            throw DuplicateNodeId("Node ID already exists: ${childId.toString(16)}")
        }
        return createNewNode(childId, concept).addChild(parentId, role, index, childId)
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?): ITree {
        checkChildRoleId(parentId, role)
        return addNewChild(parentId, role, index, childId, concept?.getReference())
    }

    override fun addNewChildren(parentId: Long, role: String?, index: Int, newIds: LongArray, concepts: Array<IConcept?>): ITree {
        checkChildRoleId(parentId, role)
        throw UnsupportedOperationException("Not implemented yet")
    }

    override fun addNewChildren(parentId: Long, role: String?, index: Int, newIds: LongArray, concepts: Array<IConceptReference?>): ITree {
        checkChildRoleId(parentId, role)
        TODO("Not yet implemented")
    }

    override fun deleteNodes(nodeIds: LongArray): ITree {
        throw UnsupportedOperationException("Not implemented yet")
    }

    /**
     * Incomplete operation. The node is added to the map, but not attached anywhere in the tree.
     */
    protected fun createNewNode(nodeId: Long, concept: IConceptReference?): CLTree {
        var newIdToHash = nodesMap
        val newChildData = create(
            nodeId,
            concept?.getUID(),
            0,
            null,
            LongArray(0),
            arrayOf(),
            arrayOf(),
            arrayOf(),
            arrayOf(),
        )
        newIdToHash = newIdToHash!!.put(newChildData)!!
        return CLTree(data.id, newIdToHash, store, data.usesRoleIds)
    }

    /**
     * Incomplete operation. The child has to exist in the map, but not be part of the tree.
     */
    protected fun addChild(parentId: Long, role: String?, index: Int, childId: Long): ITree {
        val parent = resolveElement(parentId)
        var newIdToHash = nodesMap
        val childData = resolveElement(childId)!!.getData()
        val newChildData = create(
            childData.id,
            childData.concept,
            parentId,
            role,
            childData.childrenIdArray,
            childData.propertyRoles,
            childData.propertyValues,
            childData.referenceRoles,
            childData.referenceTargets,
        )
        newIdToHash = newIdToHash!!.put(newChildData)
        var newChildrenArray = parent!!.getData().childrenIdArray
        newChildrenArray = if (index == -1) {
            add(newChildrenArray, childData.id)
        } else {
            val childrenInRole = getChildren(parentId, role).toList()
            if (index > childrenInRole.size) throw RuntimeException("Invalid index $index. There are only ${childrenInRole.size} nodes in ${parentId.toString(16)}.$role")
            if (index == childrenInRole.size) {
                add(newChildrenArray, childData.id)
            } else {
                val indexInAll = newChildrenArray.indexOf(childrenInRole[index])
                insert(
                    newChildrenArray,
                    indexInAll,
                    childData.id,
                )
            }
        }
        val newParentData = create(
            parent.id,
            parent.concept,
            parent.getData().parentId,
            parent.roleInParent,
            newChildrenArray,
            parent.getData().propertyRoles,
            parent.getData().propertyValues,
            parent.getData().referenceRoles,
            parent.getData().referenceTargets,
        )
        newIdToHash = newIdToHash!!.put(newParentData)
        return CLTree(data.id, newIdToHash!!, store, data.usesRoleIds)
    }

    override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?): ITree {
        checkReferenceRoleId(sourceId, role)
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
        val newNodeData = source.getData().withReferenceTarget(role, refData)
        newIdToHash = newIdToHash!!.put(newNodeData)
        return CLTree(data.id, newIdToHash!!, store, data.usesRoleIds)
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
        val parent = resolveElement(node!!.getData().parentId)
        var newIdToHash: CLHamtNode = nodesMap
            ?: throw RuntimeException("nodesMap not found for hash: " + this.data.idToHash)
        val newParentData = create(
            parent!!.id,
            parent.concept,
            parent.getData().parentId,
            parent.getData().roleInParent,
            remove(parent.getData().childrenIdArray, node.id),
            parent.getData().propertyRoles,
            parent.getData().propertyValues,
            parent.getData().referenceRoles,
            parent.getData().referenceTargets,
        )
        newIdToHash = newIdToHash.put(newParentData)
            ?: throw RuntimeException("Unexpected empty nodes map. There should be at least the root node.")
        if (recursive) {
            newIdToHash = deleteElements(node.getData(), newIdToHash)
                ?: throw RuntimeException("Unexpected empty nodes map. There should be at least the root node.")
        }
        return CLTree(data.id, newIdToHash, store, data.usesRoleIds)
    }

    override fun containsNode(nodeId: Long): Boolean {
        return nodesMap!![nodeId] != null
    }

    override fun getAllChildren(parentId: Long): Iterable<Long> {
        val children = resolveElement(parentId)!!.getChildren(BulkQuery(store)).execute()
        return children.map { it.id }
    }

    override fun getDescendants(root: Long, includeSelf: Boolean): Iterable<CLNode> {
        val parent = resolveElement(root)
        return parent!!.getDescendants(BulkQuery(store), includeSelf).execute()
    }

    override fun getDescendants(rootIds: Iterable<Long>, includeSelf: Boolean): Iterable<CLNode> {
        val bulkQuery = BulkQuery(store)
        val roots: IBulkQuery.Value<List<CLNode>> = resolveElements(rootIds.toList(), bulkQuery)
        val descendants = roots.mapBulk { bulkQuery.map(it) { it.getDescendants(bulkQuery, includeSelf) } }
        return descendants.execute().flatten()
    }

    override fun getAncestors(nodeIds: Iterable<Long>, includeSelf: Boolean): Set<Long> {
        val bulkQuery = BulkQuery(store)
        val nodes: IBulkQuery.Value<List<CLNode>> = resolveElements(nodeIds, bulkQuery)
        val ancestors = nodes.mapBulk { bulkQuery.map(it) { it.getAncestors(bulkQuery, includeSelf) } }
        val result = HashSet<Long>()
        ancestors.execute().forEach { result.addAll(it.map { it.id }) }
        return result
    }

    override fun getChildren(parentId: Long, role: String?): Iterable<Long> {
        checkChildRoleId(parentId, role)
        val parent = resolveElement(parentId)
        val children = parent!!.getChildren(BulkQuery(store)).execute()
        return children
            .filter { it.roleInParent == role }
            .map { it.id }
    }

    override fun getChildRoles(sourceId: Long): Iterable<String?> {
        val parent = resolveElement(sourceId)
        val children: Iterable<CLNode> = parent!!.getChildren(BulkQuery(store)).execute()
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
        return node!!.getData().parentId
    }

    override fun getProperty(nodeId: Long, role: String): String? {
        checkPropertyRoleId(nodeId, role)
        val node = resolveElement(nodeId)
        return node!!.getData().getPropertyValue(role)
    }

    override fun getPropertyRoles(sourceId: Long): Iterable<String> {
        val node = resolveElement(sourceId)
        return node!!.getData().propertyRoles.toList()
    }

    override fun getReferenceRoles(sourceId: Long): Iterable<String> {
        val node = resolveElement(sourceId)
        return node!!.getData().referenceRoles.toList()
    }

    override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
        checkReferenceRoleId(sourceId, role)
        val node = resolveElement(sourceId)!!
        val targetRef = node.getData().getReferenceTarget(role)
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
        checkChildRoleId(targetParentId, targetRole)
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
        return deleteNode(childId, false).addChild(targetParentId, targetRole, targetIndex, childId)
    }

    override fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor) {
        val bulkQuery = BulkQuery(store)
        visitChanges(oldVersion, visitor, bulkQuery)
        bulkQuery.process()
    }

    fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor, bulkQuery: IBulkQuery) {
        require(oldVersion is CLTree) { "Diff is only supported between two instances of CLTree" }
        if (data.idToHash == oldVersion.data.idToHash) return
        NonBulkQuery.runWithDisabled {
            val changesOnly = visitor !is ITreeChangeVisitorEx
            nodesMap!!.visitChanges(
                oldVersion.nodesMap,
                object : CLHamtNode.IChangeVisitor {
                    override fun visitChangesOnly(): Boolean {
                        return changesOnly
                    }

                    override fun entryAdded(key: Long, value: KVEntryReference<CPNode>?) {
                        if (visitor is ITreeChangeVisitorEx) {
                            createElement(value, bulkQuery).map { element ->
                                visitor.nodeAdded(element!!.id)
                            }
                        }
                    }

                    override fun entryRemoved(key: Long, value: KVEntryReference<CPNode>?) {
                        if (visitor is ITreeChangeVisitorEx) {
                            oldVersion.createElement(value, bulkQuery).map { element ->
                                visitor.nodeRemoved(element!!.id)
                            }
                        }
                    }

                    override fun entryChanged(key: Long, oldValue: KVEntryReference<CPNode>?, newValue: KVEntryReference<CPNode>?) {
                        oldVersion.createElement(oldValue, bulkQuery).map { oldElement ->
                            createElement(newValue, bulkQuery).map { newElement ->
                                if (oldElement!!::class != newElement!!::class) {
                                    throw RuntimeException("Unsupported type change of node " + key + "from " + oldElement::class.simpleName + " to " + newElement::class.simpleName)
                                }
                                if (oldElement.parentId != newElement.parentId || oldElement.roleInParent != newElement.roleInParent) {
                                    visitor.containmentChanged(key)
                                }
                                oldElement.getData().propertyRoles.asSequence()
                                    .plus(newElement.getData().propertyRoles.asSequence())
                                    .distinct()
                                    .forEach { role: String ->
                                        if (oldElement.getData().getPropertyValue(role) != newElement.getData().getPropertyValue(role)) {
                                            visitor.propertyChanged(newElement.id, role)
                                        }
                                    }
                                oldElement.getData().referenceRoles.asSequence()
                                    .plus(newElement.getData().referenceRoles.asSequence())
                                    .distinct()
                                    .forEach { role: String ->
                                        if (oldElement.getData().getReferenceTarget(role) != newElement.getData().getReferenceTarget(role)) {
                                            visitor.referenceChanged(newElement.id, role)
                                        }
                                    }
                                val oldChildren: MutableMap<String?, MutableList<CLNode>> = HashMap()
                                val newChildren: MutableMap<String?, MutableList<CLNode>> = HashMap()
                                oldElement.getChildren(BulkQuery(store)).execute().forEach { oldChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
                                newElement.getChildren(BulkQuery(store)).execute().forEach { newChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
                                val roles: MutableSet<String?> = HashSet()
                                roles.addAll(oldChildren.keys)
                                roles.addAll(newChildren.keys)
                                for (role in roles) {
                                    val oldChildrenInRole = oldChildren[role]
                                    val newChildrenInRole = newChildren[role]
                                    val oldValues = oldChildrenInRole?.map { it.id }
                                    val newValues = newChildrenInRole?.map { it.id }
                                    if (oldValues != newValues) {
                                        visitor.childrenChanged(newElement.id, role)
                                    }
                                }
                            }
                        }
                    }
                },
                bulkQuery,
            )
        }
    }

    protected fun deleteElements(node: CPNode, idToHash: CLHamtNode): CLHamtNode? {
        var newIdToHash: CLHamtNode? = idToHash
        for (childId in node.getChildrenIds()) {
            if (newIdToHash == null) throw RuntimeException("node $childId not found")
            val childHash: KVEntryReference<CPNode> = newIdToHash[childId] ?: throw RuntimeException("node $childId not found")
            val child = childHash.getValue(store)
            newIdToHash = deleteElements(child, newIdToHash)
        }
        if (newIdToHash == null) throw RuntimeException("node ${node.id} not found")
        newIdToHash = newIdToHash.remove(node.id)
        return newIdToHash
    }

    fun resolveElement(ref: CLNodeRef?): CLNode? {
        if (ref == null) {
            return null
        }
        val id = ref.id
        return resolveElement(id)
    }

    fun resolveElement(ref: CPNodeRef?): CLNode? {
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

    fun resolveElement(id: Long): CLNode? {
        if (id == 0L) {
            return null
        }
        val hash = nodesMap!![id] ?: throw NodeNotFoundException(id)
        return createElement(hash, NonBulkQuery(store)).execute()
    }

    fun resolveElements(ids_: Iterable<Long>, bulkQuery: IBulkQuery): IBulkQuery.Value<List<CLNode>> {
        val ids = ids_.toList()
        val a: IBulkQuery.Value<List<KVEntryReference<CPNode>?>> = nodesMap!!.getAll(ids, bulkQuery)
        val b: IBulkQuery.Value<List<KVEntryReference<CPNode>>> = a.map { hashes: List<KVEntryReference<CPNode>?> ->
            hashes.mapIndexed { index, s -> s ?: throw NodeNotFoundException(ids[index]) }
        }
        return b.mapBulk { hashes -> createElements(hashes, bulkQuery) }
    }

    fun createElement(hash: KVEntryReference<CPNode>?, query: IBulkQuery): IBulkQuery.Value<CLNode?> {
        return if (hash == null) {
            query.constant(null)
        } else {
            (query[hash].map { n: CPNode? -> CLNode.create(this@CLTree, n) })
        }
    }

    fun createElement(hash: KVEntryReference<CPNode>?): CLNode? {
        return createElement(hash, NonBulkQuery(store)).execute()
    }

    fun createElements(hashes: List<KVEntryReference<CPNode>>, bulkQuery: IBulkQuery): IBulkQuery.Value<List<CLNode>> {
        return bulkQuery.map(hashes) { hash: KVEntryReference<CPNode> ->
            bulkQuery[hash].map { n -> CLNode.create(this@CLTree, n)!! }
        }
    }

    override fun toString(): String {
        return "CLTree[$hash]"
    }

    private fun checkChildRoleId(nodeId: Long, role: String?) = checkRoleId(nodeId, role) { it.getAllChildLinks() }
    private fun checkReferenceRoleId(nodeId: Long, role: String?) = checkRoleId(nodeId, role) { it.getAllReferenceLinks() }
    private fun checkPropertyRoleId(nodeId: Long, role: String?) = checkRoleId(nodeId, role) { it.getAllProperties() }
    private fun checkRoleId(nodeId: Long, role: String?, rolesGetter: (IConcept) -> Iterable<IRole>) {
        if (role != null && usesRoleIds()) {
            val isKnownRoleName = getConceptReference(nodeId)?.tryResolve()?.let { concept ->
                runCatching { rolesGetter(concept).any { it.getSimpleName() == role } }.getOrNull()
            } ?: false
            if (isKnownRoleName) {
                throw IllegalArgumentException("A role UID is expected, but a name was provided: $role")
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
                repositoryId_ = repositoryId ?: RepositoryId.random(),
                store_ = store,
                useRoleIds = useRoleIds,
            )
        }
    }
}

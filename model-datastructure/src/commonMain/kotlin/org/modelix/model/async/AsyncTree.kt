package org.modelix.model.async

import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.ITree
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.NodeReference
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
import org.modelix.model.lazy.NodeNotFoundException
import org.modelix.model.objects.IObjectGraph
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.asObject
import org.modelix.model.objects.requestBoth
import org.modelix.model.persistent.CPHamtInternal
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
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.flatten
import org.modelix.streams.ifEmpty
import org.modelix.streams.notNull
import org.modelix.streams.plus

open class AsyncTree(val resolvedTreeData: Object<CPTree>) : IAsyncMutableTree, IStreamExecutorProvider {

    val store: IAsyncObjectStore get() = resolvedTreeData.graph.getAsyncStore()

    val treeData: CPTree get() = resolvedTreeData.data
    private val objectGraph: IObjectGraph get() = resolvedTreeData.graph
    private val nodesMap: ObjectReference<CPHamtNode> = treeData.idToHash

    override fun getStreamExecutor(): IStreamExecutor {
        return resolvedTreeData.graph.getStreamExecutor()
    }

    fun getNode(id: Long): IStream.One<CPNode> = tryGetNodeRef(id)
        .exceptionIfEmpty { NodeNotFoundException(id) }
        .flatMapOne { it.resolveData() }

    fun getNodes(ids: LongArray): IStream.Many<CPNode> {
        return nodesMap.resolveData().flatMap {
            it.getAll(ids, 0).toList().map {
                val entries = it.associateBy { it.first }
                ids.map { id ->
                    val value = entries[id]?.second
                    if (value == null) throw NodeNotFoundException(id)
                    value
                }
            }
        }.flatMap {
            IStream.many(it).flatMap { it.resolveData() }
        }
    }

    private fun tryGetNodeRef(id: Long): IStream.ZeroOrOne<ObjectReference<CPNode>> =
        nodesMap.resolveData().flatMapZeroOrOne { it.get(id) }

    override fun asSynchronousTree(): ITree {
        return CLTree(resolvedTreeData)
        // return AsyncAsSynchronousTree(this)
    }

    override fun containsNode(nodeId: Long): IStream.One<Boolean> {
        return tryGetNodeRef(nodeId).map { true }.ifEmpty { false }
    }

    override fun getChanges(oldVersion: IAsyncTree, changesOnly: Boolean): IStream.Many<TreeChangeEvent> {
        require(oldVersion is AsyncTree)
        if (nodesMap.getHash() == oldVersion.nodesMap.getHash()) return IStream.empty()
        return nodesMap.resolveData().zipWith(oldVersion.nodesMap.resolveData()) { newMap, oldMap ->
            getChanges(oldVersion, newMap, oldMap, changesOnly)
        }.flatten()
    }

    private fun getChanges(oldTree: AsyncTree, newNodesMap: CPHamtNode, oldNodesMap: CPHamtNode, changesOnly: Boolean): IStream.Many<TreeChangeEvent> {
        return newNodesMap.getChanges(oldNodesMap, 0, changesOnly).flatMap { mapEvent ->
            when (mapEvent) {
                is EntryAddedEvent -> {
                    if (changesOnly) {
                        IStream.empty()
                    } else {
                        IStream.of(NodeAddedEvent(mapEvent.key))
                    }
                }
                is EntryRemovedEvent -> {
                    if (changesOnly) {
                        IStream.empty()
                    } else {
                        IStream.of(NodeRemovedEvent(mapEvent.key))
                    }
                }
                is EntryChangedEvent -> {
                    mapEvent.newValue.requestBoth(mapEvent.oldValue) { newNode, oldNode ->
                        getChanges(oldTree, oldNode.data, newNode.data, mapEvent)
                    }.flatten()
                }
            }
        }.distinct()
    }

    private fun getChanges(oldTree: AsyncTree, oldNode: CPNode, newNode: CPNode, mapEvent: EntryChangedEvent): IStream.Many<TreeChangeEvent> {
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

        val newChildren = IStream.many(newNode.childrenIdArray).flatMap { getNode(it) }.toList()
        val oldChildren = IStream.many(oldNode.childrenIdArray).flatMap { oldTree.getNode(it) }.toList()
        val childrenChanges = newChildren.zipWith(oldChildren) { newChildrenList, oldChildrenList ->
            val oldChildren: MutableMap<String?, MutableList<CPNode>> = HashMap()
            val newChildren: MutableMap<String?, MutableList<CPNode>> = HashMap()
            oldChildrenList.forEach { oldChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
            newChildrenList.forEach { newChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }

            val roles: MutableSet<String?> = HashSet()
            roles.addAll(oldChildren.keys)
            roles.addAll(newChildren.keys)
            IStream.many(
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
                },
            )
        }.flatten()

        return IStream.many(changes) + childrenChanges
    }

    override fun getConceptReference(nodeId: Long): IStream.One<ConceptReference> {
        return getNode(nodeId).map { ConceptReference(it.concept ?: NullConcept.getUID()) }
    }

    override fun getParent(nodeId: Long): IStream.ZeroOrOne<Long> {
        return getNode(nodeId).map { it.parentId }.filter { it != 0L }
    }

    override fun getRole(nodeId: Long): IStream.One<IChildLinkReference> {
        return getNode(nodeId).map { getChildLinkFromString(it.roleInParent) }
    }

    override fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference> {
        return getNode(sourceId).map { node ->
            node.getReferenceTarget(role.key())?.convertReference()
        }.notNull()
    }

    private fun CPNodeRef.convertReference(): INodeReference {
        val targetRef = this
        return when (targetRef) {
            is CPNodeRef.LocalRef -> PNodeReference(targetRef.elementId, treeData.id)
            is CPNodeRef.GlobalRef -> PNodeReference(targetRef.elementId, targetRef.treeId)
            is CPNodeRef.ForeignRef -> NodeReference(targetRef.serializedRef)
            else -> throw UnsupportedOperationException("Unsupported reference: $targetRef")
        }
    }

    override fun getReferenceRoles(sourceId: Long): IStream.Many<IReferenceLinkReference> {
        return getNode(sourceId).flatMapIterable { it.referenceRoles.map { getReferenceLinkFromString(it) } }
    }

    override fun getPropertyRoles(sourceId: Long): IStream.Many<IPropertyReference> {
        return getNode(sourceId).flatMapIterable { it.propertyRoles.map { getPropertyFromString(it) } }
    }

    override fun getAllPropertyValues(sourceId: Long): IStream.Many<Pair<IPropertyReference, String>> {
        return getNode(sourceId).flatMapIterable { data ->
            data.propertyRoles.mapIndexed { index, role ->
                getPropertyFromString(role) to data.propertyValues[index]
            }
        }
    }

    override fun getChildRoles(sourceId: Long): IStream.Many<IChildLinkReference> {
        return getAllChildren(sourceId).flatMap {
            getNode(it).map { getChildLinkFromString(it.roleInParent) }
        }.distinct()
    }

    override fun getAllChildren(parentId: Long): IStream.Many<Long> {
        return getNode(parentId).flatMapIterable { it.childrenIdArray.toList() }
    }

    override fun getAllReferenceTargetRefs(sourceId: Long): IStream.Many<Pair<IReferenceLinkReference, INodeReference>> {
        return getNode(sourceId).flatMapIterable { data ->
            data.referenceRoles.mapIndexed { index, role ->
                getReferenceLinkFromString(role) to data.referenceTargets[index].convertReference()
            }
        }
    }

    override fun getPropertyValue(nodeId: Long, role: IPropertyReference): IStream.ZeroOrOne<String> {
        return getNode(nodeId).map { node ->
            node.getPropertyValue(role.key())
        }.notNull()
    }

    override fun getChildren(parentId: Long, role: IChildLinkReference): IStream.Many<Long> {
        val roleString = role.key()
        return getNode(parentId)
            .flatMap { getNodes(it.childrenIdArray) }
            .filter { it.roleInParent == roleString }
            .map { it.id }
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

    private fun IStream.ZeroOrOne<CPHamtNode>.assertNotEmpty(): IStream.One<CPHamtNode> = exceptionIfEmpty { IllegalStateException("Tree is empty. It should contain at least the root node.") }
    private fun IStream.ZeroOrOne<CPHamtNode>.newTree() = withNewNodesMap(assertNotEmpty())
    private fun IStream.One<CPHamtNode>.newTree() = withNewNodesMap(this)

    private fun withNewNodesMap(newMap: IStream.One<CPHamtNode>): IStream.One<AsyncTree> {
        return newMap.map {
            val newIdToHash = resolvedTreeData.referenceFactory(it)
            @OptIn(DelicateModelixApi::class) // this is a new object
            AsyncTree(CPTree(treeData.id, newIdToHash, treeData.usesRoleIds).asObject(objectGraph))
        }
    }

    private fun updateNode(nodeId: Long, transform: (CPNode) -> IStream.One<CPNode>): IStream.One<AsyncTree> {
        return updateNodeInMap(nodesMap.resolveData(), nodeId, transform).newTree()
    }

    private fun updateNodeInMap(nodesMap: IStream.One<CPHamtNode>, nodeId: Long, transform: (CPNode) -> IStream.One<CPNode>): IStream.One<CPHamtNode> {
        return nodesMap.flatMapOne { oldMap ->
            oldMap.get(nodeId)
                .exceptionIfEmpty { throw IllegalArgumentException("Node not found: ${nodeId.toString(16)}") }
                .flatMapOne { it.resolveData() }
                .map { oldMap to it }
                .flatMapOne { (oldMap, nodeData) -> transform(nodeData).flatMapOne { newData -> oldMap.put(newData, objectGraph) } }
        }
    }

    override fun addNewChildren(
        parentId: Long,
        role: IChildLinkReference,
        index: Int,
        newIds: LongArray,
        concepts: Array<ConceptReference>,
    ): IStream.One<IAsyncMutableTree> {
        val newNodes = newIds.zip(concepts).map { (childId, concept) ->
            childId to resolvedTreeData.referenceFactory(
                CPNode.create(
                    childId,
                    concept.getUID().takeIf { it != NullConcept.getUID() },
                    parentId,
                    getRoleKey(role),
                    LongArray(0),
                    arrayOf(),
                    arrayOf(),
                    arrayOf(),
                    arrayOf(),
                ),
            )
        }

        val newParentData: IStream.One<CPNode> = insertChildrenIntoParentData(parentId, index, newIds, role)
        return nodesMap.resolveData().zipWith(newParentData) { nodesMap, newParentData ->
            nodesMap
                .getAll(newIds, 0)
                .assertEmpty { "Node with ID ${it.first.toString(16)} already exists" }
                .plus(nodesMap.putAll(newNodes + (parentId to objectGraph(newParentData)), 0, objectGraph))
                .ifEmpty { CPHamtInternal.createEmpty() }
        }.flatten().newTree()
    }

    private fun insertChildrenIntoParentData(parentId: Long, index: Int, newIds: LongArray, role: IChildLinkReference): IStream.One<CPNode> {
        return getNode(parentId).flatMapOne { parentData ->
            insertChildrenIntoParentData(parentData, index, newIds, role)
        }
    }

    private fun insertChildrenIntoParentData(parentData: CPNode, index: Int, newIds: LongArray, role: IChildLinkReference): IStream.One<CPNode> {
        return if (index == -1) {
            IStream.of(parentData.childrenIdArray + newIds)
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
    ): IStream.One<IAsyncMutableTree> {
        require(childId != ITree.ROOT_ID) { "Moving the root node is not allowed" }
        val checkCycle = getAncestors(newParentId, true).toList().map { ancestors ->
            if (ancestors.contains(childId)) {
                throw ContainmentCycleException(newParentId, childId)
            }
        }.drainAll()

        val oldParent = getParent(childId).exceptionIfEmpty() {
            IllegalArgumentException("Cannot move node without parent: ${childId.toString(16)}")
        }
        val adjustedIndex: IStream.One<Int> = oldParent.flatMapOne { oldParentId ->
            if (oldParentId != newParentId) {
                IStream.of(newIndex)
            } else {
                getRole(childId).flatMapOne { oldRole ->
                    if (getRoleKey(oldRole) != getRoleKey(newRole)) {
                        IStream.of(newIndex)
                    } else {
                        getChildren(oldParentId, oldRole).toList().map { oldSiblings ->
                            val oldIndex = oldSiblings.indexOf(childId)
                            if (oldIndex < newIndex) newIndex - 1 else newIndex
                        }
                    }
                }
            }
        }

        val newTree: IStream.One<IAsyncMutableTree> = oldParent.zipWith(adjustedIndex) { oldParentId, adjustedIndex ->
            val withChildRemoved = updateNode(oldParentId) {
                IStream.of(it.withChildRemoved(childId))
            }
            val withChildAdded = withChildRemoved.flatMapOne { tree ->
                tree.updateNode(newParentId) {
                    tree.insertChildrenIntoParentData(it, adjustedIndex, longArrayOf(childId), newRole)
                }
            }
            val withUpdatedRole = withChildAdded.flatMapOne { tree ->
                tree.updateNode(childId) {
                    IStream.of(it.withContainment(newParentId, getRoleKey(newRole)))
                }
            }
            withUpdatedRole
        }.flatten()
        return checkCycle.plus(newTree)
    }

    override fun setConcept(nodeId: Long, concept: ConceptReference): IStream.One<IAsyncMutableTree> {
        return updateNode(nodeId) { IStream.of(it.withConcept(concept.getUID().takeIf { it != NullConcept.getUID() })) }
    }

    override fun setPropertyValue(nodeId: Long, role: IPropertyReference, value: String?): IStream.One<IAsyncMutableTree> {
        return updateNode(nodeId) { IStream.of(it.withPropertyValue(getRoleKey(role), value)) }
    }

    override fun setReferenceTarget(sourceId: Long, role: IReferenceLinkReference, target: INodeReference?): IStream.One<IAsyncMutableTree> {
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
        return updateNode(sourceId) { IStream.of(it.withReferenceTarget(getRoleKey(role), refData)) }
    }

    override fun setReferenceTarget(sourceId: Long, role: IReferenceLinkReference, targetId: Long): IStream.One<IAsyncMutableTree> {
        return updateNode(sourceId) { IStream.of(it.withReferenceTarget(getRoleKey(role), local(targetId))) }
    }

    override fun deleteNodes(nodeIds: LongArray): IStream.One<IAsyncMutableTree> {
        if (nodeIds.size == 1) return deleteNodeRecursive(nodeIds[0])
        return IStream.many(nodeIds).fold(IStream.of(this)) { acc, nodeId ->
            acc.flatMapOne { it.deleteNodeRecursive(nodeId) }
        }.flatten()
    }

    private fun deleteNodeRecursive(nodeId: Long): IStream.One<AsyncTree> {
        val mapWithoutRemovedNodes: IStream.One<CPHamtNode> = getDescendantsAndSelf(nodeId)
            .fold(nodesMap.resolveData()) { map, node -> map.flatMapOne { it.remove(node, objectGraph).assertNotEmpty() } }
            .flatten()
        val parent = getParent(nodeId).exceptionIfEmpty { IllegalArgumentException("Cannot delete node without parent: ${nodeId.toString(16)}") }

        return parent.flatMapOne { parentId ->
            updateNodeInMap(mapWithoutRemovedNodes, parentId) { IStream.of(it.withChildRemoved(nodeId)) }
        }.newTree()
    }
}

class ContainmentCycleException(val newParentId: Long, val childId: Long) :
    RuntimeException(
        "${newParentId.toString(16)} is a descendant of ${childId.toString(16)}." +
            " Moving the node would create a cycle in the containment hierarchy.",
    )

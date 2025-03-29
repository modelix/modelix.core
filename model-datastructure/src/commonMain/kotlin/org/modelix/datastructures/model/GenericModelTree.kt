package org.modelix.datastructures.model

import org.modelix.datastructures.EntryAddedEvent
import org.modelix.datastructures.EntryChangedEvent
import org.modelix.datastructures.EntryRemovedEvent
import org.modelix.datastructures.IPersistentMap
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.model.TreeId
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.flatten
import org.modelix.streams.mapFirst
import org.modelix.streams.plus

abstract class GenericModelTree<NodeId>(
    val nodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>>,
    private val treeId: TreeId,
) : IModelTree<NodeId>, IStreamExecutorProvider by nodesMap {
    protected abstract fun withNewMap(newNodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>>): GenericModelTree<NodeId>
    protected abstract fun getRootNodeId(): NodeId

    override fun getId(): TreeId = treeId
    val graph: IObjectGraph = nodesMap.asObject().graph

    private fun IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>>.wrap() = map { withNewMap(it) }

    private fun resolveNode(nodeId: NodeId): IStream.One<NodeObjectData<NodeId>> {
        return nodesMap.get(nodeId).exceptionIfEmpty {
            NodeNotFoundException(nodesMap.getKeyTypeConfig().serialize(nodeId))
        }
    }

    override fun containsNode(nodeId: NodeId): IStream.One<Boolean> {
        return nodesMap.get(nodeId).map { true }.firstOrDefault(false)
    }

    override fun getConceptReference(nodeId: NodeId): IStream.One<ConceptReference> {
        return resolveNode(nodeId).map { it.concept }
    }

    override fun getParent(nodeId: NodeId): IStream.ZeroOrOne<NodeId> {
        return resolveNode(nodeId).mapNotNull { it.parentId }
    }

    override fun getRoleInParent(nodeId: NodeId): IStream.ZeroOrOne<IChildLinkReference> {
        return resolveNode(nodeId).mapNotNull { it.roleInParent }
    }

    override fun getContainment(nodeId: NodeId): IStream.ZeroOrOne<Pair<NodeId, IChildLinkReference>> {
        return resolveNode(nodeId).mapNotNull {
            (it.parentId ?: return@mapNotNull null) to (it.roleInParent ?: return@mapNotNull null)
        }
    }

    override fun getProperty(nodeId: NodeId, role: IPropertyReference): IStream.ZeroOrOne<String> {
        return resolveNode(nodeId).mapNotNull { it.getProperty(role) }
    }

    override fun getPropertyRoles(nodeId: NodeId): IStream.Many<IPropertyReference> {
        return getProperties(nodeId).map { it.first }
    }

    override fun getProperties(nodeId: NodeId): IStream.Many<Pair<IPropertyReference, String>> {
        return resolveNode(nodeId).flatMapIterable { it.properties }.mapFirst { IPropertyReference.fromUnclassifiedString(it) }
    }

    override fun getReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference> {
        return resolveNode(sourceId).mapNotNull { it.getReferenceTarget(role) }
    }

    override fun getReferenceRoles(sourceId: NodeId): IStream.Many<IReferenceLinkReference> {
        return getReferenceTargets(sourceId).map { it.first }
    }

    override fun getReferenceTargets(sourceId: NodeId): IStream.Many<Pair<IReferenceLinkReference, INodeReference>> {
        return resolveNode(sourceId).flatMapIterable { it.references }.mapFirst { IReferenceLinkReference.fromUnclassifiedString(it) }
    }

    override fun getChildren(parentId: NodeId): IStream.Many<NodeId> {
        return resolveNode(parentId).flatMapIterable { it.children }.map { it }
    }

    private fun getRoleOfChild(childId: NodeId): IStream.One<IChildLinkReference> {
        return getRoleInParent(childId).assertNotEmpty { "Inconsistent containment relation." }
    }

    override fun getChildren(parentId: NodeId, role: IChildLinkReference): IStream.Many<NodeId> {
        return getChildren(parentId).filterBySingle {
            getRoleOfChild(it).map { it.matches(role) }
        }
    }

    override fun getChildRoles(parentId: NodeId): IStream.Many<IChildLinkReference> {
        return getChildren(parentId).flatMap { getRoleOfChild(it) }.distinct()
    }

    override fun getChildrenAndRoles(parentId: NodeId): IStream.Many<Pair<IChildLinkReference, IStream.Many<NodeId>>> {
        return getChildren(parentId).flatMap { childId ->
            getRoleOfChild(childId).map { role -> role to childId }
        }.toList().flatMapIterable {
            it.groupBy { it.first }.map { it.key to IStream.Companion.many(it.value.map { it.second }) }
        }
    }

    override fun getChanges(oldVersion: IModelTree<NodeId>, changesOnly: Boolean): IStream.Many<ModelChangeEvent<NodeId>> {
        require(oldVersion is GenericModelTree<NodeId>)
        if (nodesMap.asObject().getHash() == oldVersion.nodesMap.asObject().getHash()) return IStream.empty()
        return getChanges(oldVersion, nodesMap, oldVersion.nodesMap, changesOnly)
    }

    private fun getChanges(
        oldTree: GenericModelTree<NodeId>,
        newNodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>>,
        oldNodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>>,
        changesOnly: Boolean,
    ): IStream.Many<ModelChangeEvent<NodeId>> {
        return newNodesMap.getChanges(oldNodesMap, changesOnly).flatMap { mapEvent ->
            when (mapEvent) {
                is EntryAddedEvent<NodeId, NodeObjectData<NodeId>> -> {
                    if (changesOnly) {
                        IStream.empty()
                    } else {
                        IStream.of(NodeAddedEvent(mapEvent.key))
                    }
                }
                is EntryRemovedEvent<NodeId, NodeObjectData<NodeId>> -> {
                    if (changesOnly) {
                        IStream.empty()
                    } else {
                        IStream.of(NodeRemovedEvent(mapEvent.key))
                    }
                }
                is EntryChangedEvent<NodeId, NodeObjectData<NodeId>> -> {
                    getChanges(oldTree, mapEvent.oldValue, mapEvent.newValue, mapEvent)
                }
            }
        }.distinct()
    }

    private fun getChanges(
        oldTree: GenericModelTree<NodeId>,
        oldNode: NodeObjectData<NodeId>,
        newNode: NodeObjectData<NodeId>,
        mapEvent: EntryChangedEvent<NodeId, NodeObjectData<NodeId>>,
    ): IStream.Many<ModelChangeEvent<NodeId>> {
        val changes = ArrayList<ModelChangeEvent<NodeId>>()

        if (oldNode.parentId != newNode.parentId) {
            changes += ContainmentChangedEvent(mapEvent.key)
        } else if (!oldNode.roleInParent.matches(newNode.roleInParent)) {
            changes += ContainmentChangedEvent(mapEvent.key)
            changes += ChildrenChangedEvent(oldNode.parentId!!, oldNode.roleInParent)
            changes += ChildrenChangedEvent(newNode.parentId!!, newNode.roleInParent)
        }

        if (oldNode.concept != newNode.concept) {
            changes += ConceptChangedEvent(mapEvent.key)
        }

        oldNode.properties.asSequence()
            .plus(newNode.properties.asSequence())
            .map { it.first }
            .distinct()
            .map { IPropertyReference.fromUnclassifiedString(it) }
            .forEach { role: IPropertyReference ->
                if (oldNode.getProperty(role) != newNode.getProperty(role)) {
                    changes += PropertyChangedEvent(newNode.id, role)
                }
            }

        oldNode.references.asSequence()
            .plus(newNode.references.asSequence())
            .map { it.first }
            .distinct()
            .map { IReferenceLinkReference.fromUnclassifiedString(it) }
            .forEach { role: IReferenceLinkReference ->
                if (oldNode.getReferenceTarget(role)?.serialize() != newNode.getReferenceTarget(role)?.serialize()) {
                    changes += ReferenceChangedEvent(newNode.id, role)
                }
            }

        val newChildren = IStream.many(newNode.children).flatMap { getNode(it) }.toList()
        val oldChildren = IStream.many(oldNode.children).flatMap { oldTree.getNode(it) }.toList()
        val childrenChanges: IStream.Many<ChildrenChangedEvent<NodeId>> = newChildren.zipWith(oldChildren) { newChildrenList, oldChildrenList ->
            val oldChildren: MutableMap<String?, MutableList<NodeObjectData<NodeId>>> = HashMap()
            val newChildren: MutableMap<String?, MutableList<NodeObjectData<NodeId>>> = HashMap()
            oldChildrenList.forEach { oldChildren.getOrPut(it.containment?.second, { ArrayList() }).add(it) }
            newChildrenList.forEach { newChildren.getOrPut(it.containment?.second, { ArrayList() }).add(it) }

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
                        ChildrenChangedEvent(newNode.id, IChildLinkReference.fromNullableUnclassifiedString(role))
                    } else {
                        null
                    }
                },
            )
        }.flatten()

        return IStream.many(changes) + childrenChanges
    }

    override fun mutate(operations: Iterable<MutationParameters<NodeId>>): IStream.One<GenericModelTree<NodeId>> {
        // TODO bulk apply operations to improve performance
        return operations.fold(IStream.Companion.of(this)) { tree, operation ->
            tree.flatMapOne { it.mutate(operation) }
        }
    }

    override fun mutate(operation: MutationParameters<NodeId>): IStream.One<GenericModelTree<NodeId>> {
        return when (operation) {
            is MutationParameters.AddNew -> {
                addNewChildren(
                    parentId = operation.nodeId,
                    role = operation.role,
                    index = operation.index,
                    newIds = operation.newIdAndConcept.map { it.first },
                    concepts = operation.newIdAndConcept.map { it.second },
                ).wrap()
            }
            is MutationParameters.Move -> {
                operation.existingChildIds.fold(IStream.of(this)) { acc, id ->
                    acc.flatMapOne { it.moveChild(operation.nodeId, operation.role, operation.index, id) }
                }
            }
            is MutationParameters.Concept -> {
                TODO()
            }
            is MutationParameters.Property -> {
                setPropertyValue(
                    nodeId = operation.nodeId,
                    role = operation.role,
                    value = operation.value,
                ).wrap()
            }
            is MutationParameters.Reference -> {
                TODO()
            }
            is MutationParameters.Remove -> {
                deleteNodes(listOf(operation.nodeId))
            }
        }
    }

    private fun updateNode(nodeId: NodeId, transform: (NodeObjectData<NodeId>) -> IStream.One<NodeObjectData<NodeId>>): IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>> {
        return updateNodeInMap(nodesMap, nodeId, transform)
    }

    private fun updateNodeInMap(
        nodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>>,
        nodeId: NodeId,
        transform: (NodeObjectData<NodeId>) -> IStream.One<NodeObjectData<NodeId>>,
    ): IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>> {
        return nodesMap.let { oldMap ->
            oldMap.get(nodeId)
                .exceptionIfEmpty { throw IllegalArgumentException("Node not found: $nodeId") }
                .flatMapOne { nodeData -> transform(nodeData).flatMapOne { newData -> oldMap.put(nodeId, newData) } }
        }
    }

    private fun addNewChildren(
        parentId: NodeId,
        role: IChildLinkReference,
        index: Int,
        newIds: Iterable<NodeId>,
        concepts: Iterable<ConceptReference>,
    ): IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>> {
        val newNodes = newIds.zip(concepts).map { (childId, concept) ->
            childId to NodeObjectData<NodeId>(
                deserializer = NodeObjectData.Deserializer(this.nodesMap.getKeyTypeConfig(), getId()),
                id = childId,
                concept = concept,
                containment = parentId to role.getIdOrNameOrNull(),
            )
        }

        val newParentData: IStream.One<NodeObjectData<NodeId>> = insertChildrenIntoParentData(parentId, index, newIds, role)
        return newParentData.flatMapOne { newParentData ->
            this.nodesMap
                .getAll(newIds)
                .assertEmpty { "Node with ID ${it.first} already exists" }
                .plus(nodesMap.putAll(newNodes + (parentId to newParentData)))
        }
    }

    private fun insertChildrenIntoParentData(parentId: NodeId, index: Int, newIds: Iterable<NodeId>, role: IChildLinkReference): IStream.One<NodeObjectData<NodeId>> {
        return getNode(parentId).flatMapOne { parentData ->
            insertChildrenIntoParentData(parentData, index, newIds, role)
        }
    }

    private fun insertChildrenIntoParentData(parentData: NodeObjectData<NodeId>, index: Int, newIds: Iterable<NodeId>, role: IChildLinkReference): IStream.One<NodeObjectData<NodeId>> {
        return if (index == -1) {
            IStream.Companion.of(parentData.children + newIds)
        } else {
            this.getChildren(parentData.id, role).toList().map { childrenInRole ->
                if (index > childrenInRole.size) throw RuntimeException("Invalid index $index. There are only ${childrenInRole.size} nodes in ${parentData.id}.$role")
                if (index == childrenInRole.size) {
                    parentData.children + newIds
                } else {
                    val indexInAll = parentData.children.indexOf(childrenInRole[index])
                    parentData.children.take(indexInAll) + newIds + parentData.children.drop(indexInAll)
                }
            }
        }.map { newChildrenArray ->
            parentData.copy(children = newChildrenArray)
        }
    }

    private fun getNode(id: NodeId): IStream.One<NodeObjectData<NodeId>> = nodesMap.get(id)
        .exceptionIfEmpty { NodeNotFoundException(nodesMap.getKeyTypeConfig().serialize(id)) }

    private fun setPropertyValue(nodeId: NodeId, role: IPropertyReference, value: String?): IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>> {
        return updateNode(nodeId) { IStream.Companion.of(it.withPropertyValue(role, value)) }
    }

    private fun moveChild(
        newParentId: NodeId,
        newRole: IChildLinkReference,
        newIndex: Int,
        childId: NodeId,
    ): IStream.One<GenericModelTree<NodeId>> {
        val internalChildId = childId
        require(internalChildId != getRootNodeId()) { "Moving the root node is not allowed" }
        val checkCycle = getAncestors(newParentId, true).toList().map { ancestors ->
            if (ancestors.contains(childId)) {
                throw ContainmentCycleException(newParentId, childId)
            }
        }.drainAll()

        val oldParent = getParent(childId).exceptionIfEmpty() {
            IllegalArgumentException("Cannot move node without parent: $childId")
        }
        val adjustedIndex: IStream.One<Int> = oldParent.flatMapOne { oldParentId ->
            if (oldParentId != newParentId) {
                IStream.of(newIndex)
            } else {
                getRoleOfChild(childId).flatMapOne { oldRole ->
                    if (oldRole.matches(newRole)) {
                        getChildren(oldParentId, oldRole).toList().map { oldSiblings ->
                            val oldIndex = oldSiblings.indexOf(childId)
                            if (oldIndex < newIndex) newIndex - 1 else newIndex
                        }
                    } else {
                        IStream.of(newIndex)
                    }
                }
            }
        }

        val newTree: IStream.One<GenericModelTree<NodeId>> = oldParent.zipWith(adjustedIndex) { oldParentId, adjustedIndex ->
            val withChildRemoved = updateNode(oldParentId) {
                IStream.of(it.withChildRemoved(childId))
            }.wrap()
            val withChildAdded = withChildRemoved.flatMapOne { tree ->
                tree.updateNode(newParentId) {
                    tree.insertChildrenIntoParentData(it, adjustedIndex, listOf(childId), newRole)
                }
            }.wrap()
            val withUpdatedRole = withChildAdded.flatMapOne { tree ->
                tree.updateNode(childId) {
                    IStream.of(it.copy(containment = newParentId to newRole.getIdOrNameOrNull()))
                }
            }
            withUpdatedRole
        }.flatten().wrap()
        return checkCycle.plus(newTree)
    }

    private fun deleteNodes(nodeIds: List<NodeId>): IStream.One<GenericModelTree<NodeId>> {
        if (nodeIds.size == 1) return deleteNodeRecursive(nodeIds.single())
        return IStream.many(nodeIds).fold(IStream.of(this)) { acc, nodeId ->
            acc.flatMapOne { it.deleteNodeRecursive(nodeId) }
        }.flatten()
    }

    private fun deleteNodeRecursive(nodeId: NodeId): IStream.One<GenericModelTree<NodeId>> {
        val mapWithoutRemovedNodes: IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>> = getDescendants(nodeId, true)
            .fold(IStream.of(nodesMap)) { map, node -> map.flatMapOne { it.remove(node) } }
            .flatten()
        val parent: IStream.One<NodeId> = getParent(nodeId).exceptionIfEmpty { IllegalArgumentException("Cannot delete node without parent: $nodeId") }

        return parent.zipWith(mapWithoutRemovedNodes) { parentId, mapWithoutRemovedNodes ->
            updateNodeInMap(mapWithoutRemovedNodes, parentId) { IStream.of(it.withChildRemoved(nodeId)) }
        }.flatten().wrap()
    }
}

package org.modelix.datastructures.model

// open class TreeMutator<NodeId : Any>(val tree: GenericModelTree<NodeId>) {
//    val nodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>> get() = tree.nodesMap

//    fun getNodes(ids: LongArray): IStream.Many<NodeObjectData<NodeId>> {
//        return nodesMap.resolveData().flatMap {
//            it.getAll(ids, 0).toList().map {
//                val entries = it.associateBy { it.first }
//                ids.map { id ->
//                    val value = entries[id]?.second
//                    if (value == null) throw NodeNotFoundException(id)
//                    value
//                }
//            }
//        }.flatMap {
//            IStream.many(it).flatMap { it.resolveData() }
//        }
//    }
//
//    override fun asSynchronousTree(): ITree {
//        return CLTree(resolvedTreeData)
//        // return AsyncAsSynchronousTree(this)
//    }
//
//    override fun containsNode(nodeId: NodeId): IStream.One<Boolean> {
//        return tryGetNodeRef(nodeId).map { true }.ifEmpty { false }
//    }
//
//    override fun getChanges(oldVersion: IAsyncTree, changesOnly: Boolean): IStream.Many<TreeChangeEvent> {
//        require(oldVersion is AsyncTree)
//        if (nodesMap.getHash() == oldVersion.nodesMap.getHash()) return IStream.empty()
//        return nodesMap.resolveData().zipWith(oldVersion.nodesMap.resolveData()) { newMap, oldMap ->
//            getChanges(oldVersion, newMap, oldMap, changesOnly)
//        }.flatten()
//    }
//
//    private fun getChanges(oldTree: AsyncTree, newNodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>>, oldNodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>>, changesOnly: Boolean): IStream.Many<TreeChangeEvent> {
//        return newNodesMap.getChanges(oldNodesMap, 0, changesOnly).flatMap { mapEvent ->
//            when (mapEvent) {
//                is EntryAddedEvent -> {
//                    if (changesOnly) {
//                        IStream.empty()
//                    } else {
//                        IStream.of(NodeAddedEvent(mapEvent.key))
//                    }
//                }
//                is EntryRemovedEvent -> {
//                    if (changesOnly) {
//                        IStream.empty()
//                    } else {
//                        IStream.of(NodeRemovedEvent(mapEvent.key))
//                    }
//                }
//                is EntryChangedEvent -> {
//                    mapEvent.newValue.requestBoth(mapEvent.oldValue) { newNode, oldNode ->
//                        getChanges(oldTree, oldNode.data, newNode.data, mapEvent)
//                    }.flatten()
//                }
//            }
//        }.distinct()
//    }
//
//    private fun getChanges(oldTree: AsyncTree, oldNode: NodeObjectData<NodeId>, newNode: NodeObjectData<NodeId>, mapEvent: EntryChangedEvent): IStream.Many<TreeChangeEvent> {
//        val changes = ArrayList<TreeChangeEvent>()
//
//        if (oldNode.parentId != newNode.parentId) {
//            changes += ContainmentChangedEvent(mapEvent.key)
//        } else if (oldNode.roleInParent != newNode.roleInParent) {
//            changes += ContainmentChangedEvent(mapEvent.key)
//            changes += ChildrenChangedEvent(oldNode.parentId, getChildLinkFromString(oldNode.roleInParent))
//            changes += ChildrenChangedEvent(newNode.parentId, getChildLinkFromString(newNode.roleInParent))
//        }
//
//        if (oldNode.concept != newNode.concept) {
//            changes += ConceptChangedEvent(mapEvent.key)
//        }
//
//        oldNode.propertyRoles.asSequence()
//            .plus(newNode.propertyRoles.asSequence())
//            .distinct()
//            .forEach { role: String ->
//                if (oldNode.getPropertyValue(role) != newNode.getPropertyValue(role)) {
//                    changes += PropertyChangedEvent(newNode.id, getPropertyFromString(role))
//                }
//            }
//
//        oldNode.referenceRoles.asSequence()
//            .plus(newNode.referenceRoles.asSequence())
//            .distinct()
//            .forEach { role: String ->
//                if (oldNode.getReferenceTarget(role) != newNode.getReferenceTarget(role)) {
//                    changes += ReferenceChangedEvent(newNode.id, getReferenceLinkFromString(role))
//                }
//            }
//
//        val newChildren = IStream.many(newNode.childrenIdArray).flatMap { getNode(it) }.toList()
//        val oldChildren = IStream.many(oldNode.childrenIdArray).flatMap { oldTree.getNode(it) }.toList()
//        val childrenChanges = newChildren.zipWith(oldChildren) { newChildrenList, oldChildrenList ->
//            val oldChildren: MutableMap<String?, MutableList<NodeObjectData<NodeId>>> = HashMap()
//            val newChildren: MutableMap<String?, MutableList<NodeObjectData<NodeId>>> = HashMap()
//            oldChildrenList.forEach { oldChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
//            newChildrenList.forEach { newChildren.getOrPut(it.roleInParent, { ArrayList() }).add(it) }
//
//            val roles: MutableSet<String?> = HashSet()
//            roles.addAll(oldChildren.keys)
//            roles.addAll(newChildren.keys)
//            IStream.many(
//                roles.mapNotNull { role ->
//                    val oldChildrenInRole = oldChildren[role]
//                    val newChildrenInRole = newChildren[role]
//                    val oldValues = oldChildrenInRole?.map { it.id }
//                    val newValues = newChildrenInRole?.map { it.id }
//                    if (oldValues != newValues) {
//                        ChildrenChangedEvent(newNode.id, getChildLinkFromString(role))
//                    } else {
//                        null
//                    }
//                },
//            )
//        }.flatten()
//
//        return IStream.many(changes) + childrenChanges
//    }
//
//    override fun getConceptReference(nodeId: NodeId): IStream.One<ConceptReference> {
//        return getNode(nodeId).map { ConceptReference(it.concept ?: NullConcept.getUID()) }
//    }
//
//    override fun getParent(nodeId: NodeId): IStream.ZeroOrOne<NodeId> {
//        return getNode(nodeId).map { it.parentId }.filter { it != 0L }
//    }
//
//    override fun getRole(nodeId: NodeId): IStream.One<IChildLinkReference> {
//        return getNode(nodeId).map { getChildLinkFromString(it.roleInParent) }
//    }
//
//    override fun getReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference> {
//        return getNode(sourceId).map { node ->
//            node.getReferenceTarget(role.key())?.convertReference()
//        }.notNull()
//    }
//
//    private fun CPNodeRef.convertReference(): INodeReference {
//        val targetRef = this
//        return when (targetRef) {
//            is CPNodeRef.LocalRef -> PNodeReference(targetRef.elementId, treeData.id.id)
//            is CPNodeRef.GlobalRef -> PNodeReference(targetRef.elementId, targetRef.treeId)
//            is CPNodeRef.ForeignRef -> NodeReference(targetRef.serializedRef)
//            else -> throw UnsupportedOperationException("Unsupported reference: $targetRef")
//        }
//    }
//
//    override fun getReferenceRoles(sourceId: NodeId): IStream.Many<IReferenceLinkReference> {
//        return getNode(sourceId).flatMapIterable { it.referenceRoles.map { getReferenceLinkFromString(it) } }
//    }
//
//    override fun getPropertyRoles(sourceId: NodeId): IStream.Many<IPropertyReference> {
//        return getNode(sourceId).flatMapIterable { it.propertyRoles.map { getPropertyFromString(it) } }
//    }
//
//    override fun getAllPropertyValues(sourceId: NodeId): IStream.Many<Pair<IPropertyReference, String>> {
//        return getNode(sourceId).flatMapIterable { data ->
//            data.propertyRoles.mapIndexed { index, role ->
//                getPropertyFromString(role) to data.propertyValues[index]
//            }
//        }
//    }
//
//    override fun getChildRoles(sourceId: NodeId): IStream.Many<IChildLinkReference> {
//        return getAllChildren(sourceId).flatMap {
//            getNode(it).map { getChildLinkFromString(it.roleInParent) }
//        }.distinct()
//    }
//
//    override fun getAllChildren(parentId: NodeId): IStream.Many<NodeId> {
//        return getNode(parentId).flatMapIterable { it.childrenIdArray.toList() }
//    }
//
//    override fun getAllReferenceTargetRefs(sourceId: NodeId): IStream.Many<Pair<IReferenceLinkReference, INodeReference>> {
//        return getNode(sourceId).flatMapIterable { data ->
//            data.referenceRoles.mapIndexed { index, role ->
//                getReferenceLinkFromString(role) to data.referenceTargets[index].convertReference()
//            }
//        }
//    }
//
//    override fun getPropertyValue(nodeId: NodeId, role: IPropertyReference): IStream.ZeroOrOne<String> {
//        return getNode(nodeId).map { node ->
//            node.getPropertyValue(role.key())
//        }.notNull()
//    }
//
//
//    private fun IRoleReference.key() = getRoleKey(this)
//    private fun IChildLinkReference.key() = if (this is NullChildLinkReference) null else getRoleKey(this)
//
//    fun getRoleKey(role: IChildLinkReference): String? = if (role is NullChildLinkReference) null else getRoleKey(role as IRoleReference)
//    fun getRoleKey(role: IRoleReference): String {
//        return if (treeData.usesRoleIds) {
//            role.getIdOrName()
//        } else {
//            role.getNameOrId()
//        }
//    }
//    private fun getPropertyFromString(value: String): IPropertyReference {
//        return if (treeData.usesRoleIds) IPropertyReference.fromId(value) else IPropertyReference.fromName(value)
//    }
//    private fun getReferenceLinkFromString(value: String): IReferenceLinkReference {
//        return if (treeData.usesRoleIds) IReferenceLinkReference.fromId(value) else IReferenceLinkReference.fromName(value)
//    }
//    private fun getChildLinkFromString(value: String?): IChildLinkReference {
//        return when {
//            value == null -> NullChildLinkReference
//            treeData.usesRoleIds -> IChildLinkReference.fromId(value)
//            else -> IChildLinkReference.fromName(value)
//        }
//    }
//
//    private fun IStream.ZeroOrOne<IPersistentMap<NodeId, NodeObjectData<NodeId>>>.assertNotEmpty(): IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>> = exceptionIfEmpty { IllegalStateException("Tree is empty. It should contain at least the root node.") }
//    private fun IStream.ZeroOrOne<IPersistentMap<NodeId, NodeObjectData<NodeId>>>.newTree() = withNewNodesMap(assertNotEmpty())
//    private fun IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>>.newTree() = withNewNodesMap(this)
//
//    private fun withNewNodesMap(newMap: IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>>): IStream.One<AsyncTree> {
//        return newMap.map {
//            val newIdToHash = resolvedTreeData.referenceFactory(it)
//            @OptIn(DelicateModelixApi::class) // this is a new object
//            AsyncTree(CPTree(treeData.id, newIdToHash, treeData.usesRoleIds).asObject(objectGraph))
//        }
//    }
//

//    override fun setConcept(nodeId: NodeId, concept: ConceptReference): IStream.One<IAsyncMutableTree> {
//        return updateNode(nodeId) { IStream.of(it.withConcept(concept.getUID().takeIf { it != NullConcept.getUID() })) }
//    }
//

//
//    override fun setReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference, target: INodeReference?): IStream.One<IAsyncMutableTree> {
//        val refData: CPNodeRef? = when (target) {
//            null -> null
//            is LocalPNodeReference -> {
//                local(target.id)
//            }
//            is PNodeReference -> {
//                if (target.branchId.isEmpty() || target.treeId == treeData.id.id) {
//                    local(target.id)
//                } else {
//                    global(target.branchId, target.id)
//                }
//            }
//            else -> foreign(INodeReferenceSerializer.serialize(target))
//        }
//        return updateNode(sourceId) { IStream.of(it.withReferenceTarget(getRoleKey(role), refData)) }
//    }
//
//    override fun setReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference, targetId: NodeId): IStream.One<IAsyncMutableTree> {
//        return updateNode(sourceId) { IStream.of(it.withReferenceTarget(getRoleKey(role), local(targetId))) }
//    }
//
//    override fun deleteNodes(nodeIds: LongArray): IStream.One<IAsyncMutableTree> {
//        if (nodeIds.size == 1) return deleteNodeRecursive(nodeIds[0])
//        return IStream.many(nodeIds).fold(IStream.of(this)) { acc, nodeId ->
//            acc.flatMapOne { it.deleteNodeRecursive(nodeId) }
//        }.flatten()
//    }
//
//    private fun deleteNodeRecursive(nodeId: NodeId): IStream.One<AsyncTree> {
//        val mapWithoutRemovedNodes: IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>> = getDescendantsAndSelf(nodeId)
//            .fold(nodesMap.resolveData()) { map, node -> map.flatMapOne { it.remove(node, objectGraph).assertNotEmpty() } }
//            .flatten()
//        val parent = getParent(nodeId).exceptionIfEmpty { IllegalArgumentException("Cannot delete node without parent: ${nodeId.toString(16)}") }
//
//        return parent.flatMapOne { parentId ->
//            updateNodeInMap(mapWithoutRemovedNodes, parentId) { IStream.of(it.withChildRemoved(nodeId)) }
//        }.newTree()
//    }
// }

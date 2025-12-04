package org.modelix.model.lazy

import org.modelix.datastructures.IPersistentMapRootData
import org.modelix.datastructures.autoResolveValues
import org.modelix.datastructures.createMapInstance
import org.modelix.datastructures.hamt.HamtInternalNode
import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.NodeObjectData
import org.modelix.datastructures.model.asLegacyTree
import org.modelix.datastructures.model.fromNodeReference
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.ObjectReferenceDataTypeConfiguration
import org.modelix.datastructures.objects.asObject
import org.modelix.datastructures.objects.getHashString
import org.modelix.datastructures.objects.getObject
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.TreeId
import org.modelix.model.api.ITree
import org.modelix.model.api.async.getDescendants
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.getAsyncStore
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import org.modelix.model.persistent.CPTree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.getBlocking

private fun createNewTreeData(
    graph: IObjectGraph,
    treeId: TreeId = TreeId.random(),
    useRoleIds: Boolean = true,
): Object<CPTree> {
    val root = NodeObjectData<Long>(
        deserializer = NodeObjectData.Deserializer(graph, LongDataTypeConfiguration(), treeId),
        id = ITree.ROOT_ID,
        concept = null,
        containment = null,
    )
    val config = HamtNode.Config(
        graph = graph,
        keyConfig = LongDataTypeConfiguration(),
        valueConfig = ObjectReferenceDataTypeConfiguration(
            graph,
            NodeObjectData.Deserializer(graph, LongDataTypeConfiguration(), treeId),
        ),
    )
    @OptIn(DelicateModelixApi::class) // this is a new object
    return CPTree(
        id = treeId,
        int64Hamt = graph.fromCreated(
            HamtInternalNode.createEmpty(config)
                .put(root.id, graph.fromCreated(root), graph)
                .orNull()
                .getBlocking(graph)!!,
        ),
        trieWithNodeRefIds = null,
        usesRoleIds = useRoleIds,
    ).asObject(graph)
}

@Deprecated("Use IModelTree<Long>")
class CLTree private constructor(val resolvedData: Object<CPTree>) :
    ITree by resolvedData.data.getLegacyModelTree().asLegacyTree(),
    IStreamExecutorProvider {

    override fun asObject() = resolvedData

    val asyncStore: IAsyncObjectStore get() = resolvedData.graph.getAsyncStore()

    val data: CPTree get() = resolvedData.data

    @Deprecated("Use asyncStore")
    val store: IDeserializingKeyValueStore get() = asyncStore.getLegacyObjectStore()

    override fun getStreamExecutor(): IStreamExecutor {
        return resolvedData.graph.getStreamExecutor()
    }

    override fun getId(): String = data.id.id

    fun getSize(): Long {
        return -1
    }

    @Deprecated("BulkQuery is now responsible for prefetching")
    fun prefetchAll() {
        getStreamExecutor().iterate({ asAsyncTree().getDescendants(ITree.ROOT_ID) }) { }
    }

    val hash: String
        get() = resolvedData.ref.getHashString()

    val nodesMap: IPersistentMapRootData<Long, ObjectReference<NodeObjectData<Long>>>
        get() = getStreamExecutor().query { data.int64Hamt!!.resolveData() }

    val root: NodeObjectData<Long>?
        get() = getStreamExecutor().query { resolveElement(ITree.ROOT_ID).orNull() }

    fun resolveElement(id: Long): IStream.ZeroOrOne<NodeObjectData<Long>> {
        if (id == 0L) {
            return IStream.empty()
        }
        return data.int64Hamt!!.resolveNow().createMapInstance().autoResolveValues().get(id)
    }

    override fun toString(): String {
        return "CLTree[${resolvedData.ref.getHash()}]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CLTree

        return hash == other.hash
    }

    override fun hashCode(): Int {
        return hash.hashCode()
    }

    companion object {
        fun builder(graph: IObjectGraph) = Builder(graph)
        fun builder(store: IDeserializingKeyValueStore) = Builder(store.getAsyncStore().asObjectGraph())
        fun builder(store: IAsyncObjectStore) = Builder(store.asObjectGraph())
        fun fromHash(hash: String, graph: IObjectGraph): ITree {
            return graph.getObject(hash, CPTree).let { it.data.getLegacyModelTree().asLegacyTree() }
        }
        fun fromHash(hash: String, store: IAsyncObjectStore): ITree {
            return fromHash(hash, store.asObjectGraph())
        }

        @Deprecated("Use CLTree.builder")
        operator fun invoke(store: IAsyncObjectStore, useRoleIds: Boolean = true): ITree {
            return createNewTreeData(store.asObjectGraph(), useRoleIds = useRoleIds).data.getLegacyModelTree().asLegacyTree()
        }
    }

    class Builder(graph: IObjectGraph) {
        private val modelBuilder = IGenericModelTree.builder().graph(graph)

        fun useRoleIds(value: Boolean = true): Builder {
            modelBuilder.storeRoleNames(!value)
            return this
        }

        fun treeId(id: TreeId): Builder = also {
            modelBuilder.treeId(id)
        }

        @Deprecated("Provide a treeId")
        fun repositoryId(id: RepositoryId) = treeId(TreeId.fromLegacyId(id.id))

        @Deprecated("Provide a treeId")
        fun repositoryId(id: String) = treeId(TreeId.fromLegacyId(id))

        fun build(): ITree {
            return modelBuilder.build().asLegacyTree()
        }
    }
}

fun ITree.resolveElementSynchronous(id: Long): CPNode {
    val sortedPropertyRoles = getPropertyRoles(id).sorted()
    val sortedReferenceRoles = getReferenceRoles(id).sorted()
    return CPNode(
        id = id,
        concept = getConceptReference(id)?.getUID(),
        parentId = getParent(id),
        roleInParent = getRole(id),
        childrenIds = getAllChildren(id).toList().toLongArray(),
        propertyRoles = sortedPropertyRoles.toList().toTypedArray(),
        propertyValues = sortedPropertyRoles.map { getProperty(id, it)!! }.toTypedArray(),
        referenceRoles = sortedReferenceRoles.toList().toTypedArray(),
        referenceTargets = sortedReferenceRoles.map {
            CPNodeRef.fromNodeReference(getReferenceTarget(id, it)!!, TreeId.fromLegacyId(getId()!!))
        }.toTypedArray(),
    )
}

val ITree.root: CPNode get() = resolveElementSynchronous(ITree.ROOT_ID)

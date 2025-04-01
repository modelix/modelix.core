package org.modelix.model.lazy

import org.modelix.datastructures.hamt.HamtInternalNode
import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.model.NodeObjectData
import org.modelix.datastructures.model.asLegacyTree
import org.modelix.datastructures.model.toLegacy
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
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.getAsyncStore
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPTree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.IStreamExecutorProvider

private fun createNewTreeData(
    graph: IObjectGraph,
    treeId: TreeId = TreeId.random(),
    useRoleIds: Boolean = true,
): Object<CPTree> {
    val root = NodeObjectData<Long>(
        deserializer = NodeObjectData.Deserializer(LongDataTypeConfiguration(), treeId),
        id = ITree.ROOT_ID,
        concept = NullConcept.getReference(),
        containment = null,
    )
    val config = HamtNode.Config(
        graph = graph,
        keyConfig = LongDataTypeConfiguration(),
        valueConfig = ObjectReferenceDataTypeConfiguration(graph, NodeObjectData.Deserializer(LongDataTypeConfiguration(), treeId)),
    )
    @OptIn(DelicateModelixApi::class) // this is a new object
    return CPTree(
        id = treeId,
        int64Hamt = graph.fromCreated(
            HamtInternalNode.createEmpty(config)
                .put(root.id, graph.fromCreated(root), graph)
                .orNull()
                .getSynchronous()!!,
        ),
        trieWithNodeRefIds = null,
        usesRoleIds = useRoleIds,
    ).asObject(graph)
}

@Deprecated("Use IModelTree<Long>")
class CLTree(val resolvedData: Object<CPTree>) :
    ITree by resolvedData.data.getLegacyModelTree().asLegacyTree(),
    IStreamExecutorProvider {

    override fun asObject() = resolvedData

    val asyncStore: IAsyncObjectStore get() = resolvedData.graph.getAsyncStore()

    @Deprecated("Use CLTree.builder")
    constructor(store: IAsyncObjectStore, useRoleIds: Boolean = true) :
        this(createNewTreeData(store.asObjectGraph(), useRoleIds = useRoleIds))

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

    val nodesMap: HamtNode<Long, ObjectReference<NodeObjectData<Long>>>
        get() = getStreamExecutor().query { data.int64Hamt!!.resolveData() }

    val root: NodeObjectData<Long>?
        get() = getStreamExecutor().query { resolveElement(ITree.ROOT_ID).orNull() }

    fun resolveElement(id: Long): IStream.ZeroOrOne<NodeObjectData<Long>> {
        if (id == 0L) {
            return IStream.empty()
        }
        return nodesMap.get(id).flatMapZeroOrOne {
            it.resolveData()
        }
    }

    fun resolveElementSynchronous(id: Long): CPNode {
        return getStreamExecutor().query { resolveElement(id).assertNotEmpty { "Not found: $id" } }.toLegacy()
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
        fun fromHash(hash: String, graph: IObjectGraph): CLTree {
            return graph.getObject(hash, CPTree).let { CLTree(it) }
        }
        fun fromHash(hash: String, store: IAsyncObjectStore): CLTree {
            return fromHash(hash, store.asObjectGraph())
        }
    }

    class Builder(var graph: IObjectGraph) {
        private var treeId: TreeId? = null
        private var useRoleIds: Boolean = true

        fun useRoleIds(value: Boolean = true): Builder {
            this.useRoleIds = value
            return this
        }

        fun treeId(id: TreeId): Builder = also {
            this.treeId = id
        }

        @Deprecated("Provide a treeId")
        fun repositoryId(id: RepositoryId): Builder {
            this.treeId = TreeId.fromLegacyId(id.id)
            return this
        }

        @Deprecated("Provide a treeId")
        fun repositoryId(id: String): Builder = treeId(TreeId.fromLegacyId(id))

        fun build(): CLTree {
            return CLTree(
                createNewTreeData(
                    graph,
                    treeId ?: TreeId.random(),
                    useRoleIds,
                ),
            )
        }
    }
}

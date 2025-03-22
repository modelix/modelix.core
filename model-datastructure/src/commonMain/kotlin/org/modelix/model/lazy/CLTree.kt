package org.modelix.model.lazy

import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.api.ITree
import org.modelix.model.api.async.getAncestors
import org.modelix.model.api.async.getDescendants
import org.modelix.model.async.AsyncAsSynchronousTree
import org.modelix.model.async.AsyncTree
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.getAsyncStore
import org.modelix.model.objects.IObjectGraph
import org.modelix.model.objects.Object
import org.modelix.model.objects.asObject
import org.modelix.model.objects.getHashString
import org.modelix.model.objects.getObject
import org.modelix.model.persistent.CPHamtInternal
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPTree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.IStreamExecutorProvider

private fun createNewTreeData(
    graph: IObjectGraph,
    repositoryId: RepositoryId = RepositoryId.random(), // TODO This should be a separate TreeId
    useRoleIds: Boolean = true,
): Object<CPTree> {
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
    @OptIn(DelicateModelixApi::class) // this is a new object
    return CPTree(
        repositoryId.id,
        graph(
            graph.getStreamExecutor().query {
                CPHamtInternal.createEmpty()
                    .put(root.id, graph(root), graph)
                    .orNull()
            }!!,
        ),
        useRoleIds,
    ).asObject(graph)
}

class CLTree(val resolvedData: Object<CPTree>) :
    ITree by AsyncAsSynchronousTree(AsyncTree(resolvedData)),
    IBulkTree,
    IStreamExecutorProvider {

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

    override fun getId(): String = data.id

    fun getSize(): Long {
        return -1
    }

    @Deprecated("BulkQuery is now responsible for prefetching")
    fun prefetchAll() {
        getStreamExecutor().iterate({ asAsyncTree().getDescendants(ITree.ROOT_ID) }) { }
    }

    val hash: String
        get() = resolvedData.ref.getHashString()

    val nodesMap: CPHamtNode
        get() = getStreamExecutor().query { data.idToHash.resolveData() }

    val root: CPNode?
        get() = getStreamExecutor().query { resolveElement(ITree.ROOT_ID).orNull() }

    override fun getDescendants(root: Long, includeSelf: Boolean): Iterable<CLNode> {
        val asyncTree = asAsyncTree()
        return getStreamExecutor().query {
            asyncTree.getDescendants(root, includeSelf)
                .flatMap { (asyncTree as AsyncTree).getNode(it) }.map { CLNode(this, it) }.toList()
        }
    }

    override fun getDescendants(rootIds: Iterable<Long>, includeSelf: Boolean): Iterable<CLNode> {
        val asyncTree = asAsyncTree() as AsyncTree
        return getStreamExecutor().query {
            IStream.many(rootIds)
                .flatMap { asyncTree.getDescendants(it, includeSelf) }
                .flatMap { asyncTree.getNode(it) }
                .map { CLNode(this, it) }
                .toList()
        }
    }

    override fun getAncestors(nodeIds: Iterable<Long>, includeSelf: Boolean): Set<Long> {
        val asyncTree = asAsyncTree() as AsyncTree
        return getStreamExecutor().query {
            IStream.many(nodeIds)
                .flatMap { asyncTree.getAncestors(it, includeSelf) }
                .toList()
        }.toSet()
    }

    fun resolveElement(id: Long): IStream.ZeroOrOne<CPNode> {
        if (id == 0L) {
            return IStream.empty()
        }
        return nodesMap.get(id).flatMapZeroOrOne {
            it.resolveData()
        }
    }

    fun resolveElementSynchronous(id: Long): CPNode {
        return getStreamExecutor().query { resolveElement(id).assertNotEmpty { "Not found: $id" } }
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
        private var repositoryId: RepositoryId? = null
        private var useRoleIds: Boolean = true

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
                createNewTreeData(
                    graph,
                    repositoryId ?: RepositoryId.random(),
                    useRoleIds,
                ),
            )
        }
    }
}

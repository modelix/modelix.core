package org.modelix.model.lazy

import org.modelix.model.api.ITree
import org.modelix.model.api.async.getAncestors
import org.modelix.model.api.async.getDescendants
import org.modelix.model.async.AsyncAsSynchronousTree
import org.modelix.model.async.AsyncTree
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.asObjectLoader
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.asObject
import org.modelix.model.objects.getHashString
import org.modelix.model.persistent.CPHamtInternal
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPTree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

private fun createNewTreeData(
    store: IAsyncObjectStore,
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
    return CPTree(
        repositoryId.id,
        ObjectReference<CPHamtNode>(
            store.getStreamExecutor().query {
                CPHamtInternal.createEmpty()
                    .put(root.id, ObjectReference<CPNode>(root), store.asObjectLoader())
                    .orNull()
            }!!,
        ),
        useRoleIds,
    ).asObject()
}

class CLTree(val resolvedData: Object<CPTree>, val asyncStore: IAsyncObjectStore) :
    ITree by AsyncAsSynchronousTree(AsyncTree(resolvedData, asyncStore)), IBulkTree, IStreamExecutorProvider by asyncStore {

    constructor(data: Object<CPTree>, store: IDeserializingKeyValueStore) : this(data, store.getAsyncStore())

    @Deprecated("Provide an Object<CPTree>")
    constructor(data: CPTree, store: IAsyncObjectStore) : this(data.asObject(), store)

    @Deprecated("Use CLTree.builder")
    constructor(store: IAsyncObjectStore, useRoleIds: Boolean = true) : this(createNewTreeData(store, useRoleIds = useRoleIds), store)

    @Deprecated("Use CLTree.builder")
    constructor(store: IDeserializingKeyValueStore, useRoleIds: Boolean = true) : this(store.getAsyncStore(), useRoleIds)

    @Deprecated("Provide an Object<CPTree>")
    constructor(data: CPTree, store: IDeserializingKeyValueStore) : this(data.asObject(), store.getAsyncStore())

    @Deprecated("Provide an Object<CPTree>")
    constructor(data: CPTree?, repositoryId_: RepositoryId?, store_: IDeserializingKeyValueStore, useRoleIds: Boolean = true) : this(data, repositoryId_, store_.getAsyncStore(), useRoleIds)

    @Deprecated("Provide an Object<CPTree>")
    constructor(data: CPTree?, repositoryId: RepositoryId?, store: IAsyncObjectStore, useRoleIds: Boolean = true) : this(
        data?.asObject() ?: createNewTreeData(store, repositoryId ?: RepositoryId.random(), useRoleIds),
        store,
    )

    val data: CPTree get() = resolvedData.data

    @Deprecated("Use asyncStore")
    val store: IDeserializingKeyValueStore = asyncStore.getLegacyObjectStore()

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
        get() = getStreamExecutor().query { data.idToHash.requestData(asyncStore.asObjectLoader()) }

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
        return nodesMap.get(id, asyncStore.asObjectLoader()).flatMapZeroOrOne {
            it.requestData(asyncStore.asObjectLoader())
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
        fun builder(store: IDeserializingKeyValueStore) = Builder(store.getAsyncStore())
        fun builder(store: IAsyncObjectStore) = Builder(store)
    }

    class Builder(var store: IAsyncObjectStore) {
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
                data = null as CPTree?,
                repositoryId = repositoryId ?: RepositoryId.random(),
                store = store,
                useRoleIds = useRoleIds,
            )
        }
    }
}

package org.modelix.model.lazy

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.flatMapSingle
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.toList
import org.modelix.model.api.ITree
import org.modelix.model.api.async.getAncestors
import org.modelix.model.api.async.getDescendants
import org.modelix.model.async.AsyncAsSynchronousTree
import org.modelix.model.async.AsyncStoreAsLegacyDeserializingStore
import org.modelix.model.async.AsyncTree
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.persistent.CPHamtInternal
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPTree
import org.modelix.streams.getSynchronous
import org.modelix.streams.iterateSynchronous

fun createNewTreeData(
    store: IAsyncObjectStore,
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
        KVEntryReference<CPHamtNode>(CPHamtInternal.createEmpty().put(root.id, KVEntryReference<CPNode>(root), store).getSynchronous()!!),
        useRoleIds,
    )
}

class CLTree(val data: CPTree, val asyncStore: IAsyncObjectStore) : ITree by AsyncAsSynchronousTree(AsyncTree(data, asyncStore)), IBulkTree {

    constructor(store: IAsyncObjectStore, useRoleIds: Boolean = true) : this(createNewTreeData(store, useRoleIds = useRoleIds), store)
    constructor(store: IDeserializingKeyValueStore, useRoleIds: Boolean = true) : this(store.getAsyncStore(), useRoleIds)
    constructor(data: CPTree, store: IDeserializingKeyValueStore) : this(data, store.getAsyncStore())

    constructor(data: CPTree?, repositoryId_: RepositoryId?, store_: IDeserializingKeyValueStore, useRoleIds: Boolean = true) : this(data, repositoryId_, store_.getAsyncStore(), useRoleIds)

    constructor(data: CPTree?, repositoryId: RepositoryId?, store: IAsyncObjectStore, useRoleIds: Boolean = true) : this(
        data ?: createNewTreeData(store, repositoryId ?: RepositoryId.random(), useRoleIds),
        store,
    )

    val store: IDeserializingKeyValueStore = AsyncStoreAsLegacyDeserializingStore(asyncStore)

    override fun getId(): String = data.id

    fun getSize(): Long {
        return -1
    }

    @Deprecated("BulkQuery is now responsible for prefetching")
    fun prefetchAll() {
        asAsyncTree().getDescendants(ITree.ROOT_ID).iterateSynchronous { }
    }

    val hash: String
        get() = data.hash

    val nodesMap: CPHamtNode
        get() = data.idToHash.getValue(asyncStore).getSynchronous()

    val root: CPNode?
        get() = resolveElement(ITree.ROOT_ID).getSynchronous()

    override fun getDescendants(root: Long, includeSelf: Boolean): Iterable<CLNode> {
        return asAsyncTree().getDescendants(root, includeSelf)
            .flatMapSingle { (asAsyncTree() as AsyncTree).getNode(it) }.map { CLNode(this, it) }.toList().getSynchronous()
    }

    override fun getDescendants(rootIds: Iterable<Long>, includeSelf: Boolean): Iterable<CLNode> {
        val asyncTree = asAsyncTree() as AsyncTree
        return rootIds.asObservable()
            .flatMap { asyncTree.getDescendants(it, includeSelf) }
            .flatMapSingle { asyncTree.getNode(it) }
            .map { CLNode(this, it) }
            .toList()
            .getSynchronous()
    }

    override fun getAncestors(nodeIds: Iterable<Long>, includeSelf: Boolean): Set<Long> {
        val asyncTree = asAsyncTree() as AsyncTree
        return nodeIds.asObservable()
            .flatMap { asyncTree.getAncestors(it, includeSelf) }
            .toList()
            .getSynchronous()
            .toSet()
    }

    fun resolveElement(id: Long): Maybe<CPNode> {
        if (id == 0L) {
            return maybeOfEmpty()
        }
        val hash = nodesMap.get(id, asyncStore)
        return hash.flatMapSingle {
            it.getValue(asyncStore)
        }
    }

    override fun toString(): String {
        return "CLTree[${data.hash}]"
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

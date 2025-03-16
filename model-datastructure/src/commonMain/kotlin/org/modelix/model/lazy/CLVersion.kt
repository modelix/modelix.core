package org.modelix.model.lazy

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.modelix.model.IVersion
import org.modelix.model.VersionMerger
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.TreePointer
import org.modelix.model.async.AsyncAsSynchronousTree
import org.modelix.model.async.AsyncTree
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.IKVValue
import org.modelix.model.persistent.OperationsList
import org.modelix.model.persistent.getAllObjects
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.notNull
import org.modelix.streams.plus
import kotlin.jvm.JvmName

class CLVersion : IVersion {
    val asyncStore: IAsyncObjectStore
    var store: IDeserializingKeyValueStore
    val data: CPVersion
    val treeHash: KVEntryReference<CPTree>

    private constructor(
        id: Long,
        time: String?,
        author: String?,
        treeData: CPTree,
        store: IAsyncObjectStore,
        previousVersion: CLVersion?,
        originalVersion: CLVersion?,
        baseVersion: CLVersion?,
        mergedVersion1: CLVersion?,
        mergedVersion2: CLVersion?,
        operations: Array<IOperation>,
    ) {
        this.asyncStore = store
        this.store = store.getLegacyObjectStore()
        this.treeHash = KVEntryReference(treeData)
        val localizedOps = localizeOps(operations.asList())
        if (localizedOps.size <= INLINED_OPS_LIMIT) {
            data = CPVersion(
                id = id,
                time = time,
                author = author,
                treeHash = this.treeHash,
                previousVersion = previousVersion?.let { KVEntryReference(it.data) },
                originalVersion = originalVersion?.let { KVEntryReference(it.data) },
                baseVersion = baseVersion?.let { KVEntryReference(it.data) },
                mergedVersion1 = mergedVersion1?.let { KVEntryReference(it.data) },
                mergedVersion2 = mergedVersion2?.let { KVEntryReference(it.data) },
                operations = localizedOps.toTypedArray(),
                operationsHash = null,
                numberOfOperations = localizedOps.size,
            )
        } else {
            val opsList = OperationsList.of(localizedOps.toList())
            data = CPVersion(
                id = id,
                time = time,
                author = author,
                treeHash = this.treeHash,
                previousVersion = previousVersion?.let { KVEntryReference(it.data) },
                originalVersion = originalVersion?.let { KVEntryReference(it.data) },
                baseVersion = baseVersion?.let { KVEntryReference(it.data) },
                mergedVersion1 = mergedVersion1?.let { KVEntryReference(it.data) },
                mergedVersion2 = mergedVersion2?.let { KVEntryReference(it.data) },
                operations = null,
                operationsHash = KVEntryReference(opsList),
                numberOfOperations = localizedOps.size,
            )
        }
        write()
    }

    constructor(hash: String, store: IDeserializingKeyValueStore) : this(
        store.get<CPVersion>(hash, { CPVersion.deserialize(it) })
            ?: throw IllegalArgumentException("version '$hash' not found"),
        store,
    )
    constructor(data: CPVersion?, store: IDeserializingKeyValueStore) : this(data, store.getAsyncStore())
    constructor(data: CPVersion?, store: IAsyncObjectStore) {
        if (data == null) {
            throw NullPointerException("data is null")
        }
        this.data = data
        this.treeHash = data.treeHash
        this.asyncStore = store
        this.store = asyncStore.getLegacyObjectStore()
    }

    val author: String?
        get() = data.author

    val id: Long
        get() = data.id

    @Deprecated("Use getTimestamp()")
    val time: String?
        get() = data.time

    fun getTimestamp(): Instant? {
        val dateTimeStr = data.time ?: return null
        try {
            return Instant.fromEpochSeconds(dateTimeStr.toLong())
        } catch (ex: Exception) {}
        try {
            return kotlinx.datetime.LocalDateTime.parse(dateTimeStr).toInstant(TimeZone.currentSystemDefault())
        } catch (ex: Exception) {}
        return null
    }

    @Deprecated("Use getContentHash()", ReplaceWith("getContentHash()"))
    val hash: String
        get() = data.hash

    override fun getContentHash(): String = data.hash

    @Deprecated("Use getTree()", ReplaceWith("getTree()"))
    @get:JvmName("getTree_()")
    val tree: CLTree
        get() = CLTree(treeHash.getValue(store), store)

    override fun getTree(): CLTree = tree

    val baseVersion: CLVersion?
        get() {
            val previousVersionHash = data.baseVersion ?: data.previousVersion ?: return null
            val previousVersion = previousVersionHash.getValue(store)
            return CLVersion(previousVersion, store)
        }

    val operations: Iterable<IOperation>
        get() {
            val operationsHash = data.operationsHash
            val ops = operationsHash?.let { h -> store.getStreamExecutor().query { h.getValue(store).getOperations(asyncStore).toList() } }
                ?: data.operations?.toList()
                ?: emptyList()
            return globalizeOps(ops)
        }

    val numberOfOperations: Int
        get() = data.numberOfOperations

    fun operationsInlined(): Boolean {
        return data.operations != null
    }

    fun isMerge() = this.data.mergedVersion1 != null

    fun getMergedVersion1() = this.data.mergedVersion1?.let { CLVersion(it.getValue(store), store) }
    fun getMergedVersion2() = this.data.mergedVersion2?.let { CLVersion(it.getValue(store), store) }

    fun write(): String {
        KVEntryReference(data).write(store)
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CLVersion

        if (data.id != other.data.id) return false

        return true
    }

    override fun hashCode(): Int {
        return data.id.hashCode()
    }

    override fun toString(): String {
        return hash
    }

    companion object {
        val INLINED_OPS_LIMIT = 10
        fun createAutoMerge(
            id: Long,
            tree: ITree,
            baseVersion: CLVersion,
            mergedVersion1: CLVersion,
            mergedVersion2: CLVersion,
            operations: Array<IOperation>,
            store: IDeserializingKeyValueStore,
        ): CLVersion {
            val dataAndStore = tree.extractDataAndStore()
            return CLVersion(
                id = id,
                time = null,
                author = null,
                treeData = dataAndStore.first,
                store = dataAndStore.second,
                previousVersion = null,
                originalVersion = null,
                baseVersion = baseVersion,
                mergedVersion1 = mergedVersion1,
                mergedVersion2 = mergedVersion2,
                operations = operations,
            )
        }

        fun createRegularVersion(
            id: Long,
            time: String?,
            author: String?,
            tree: ITree,
            baseVersion: CLVersion?,
            operations: Array<IOperation>,
        ): CLVersion {
            val dataAndStore = tree.extractDataAndStore()
            return CLVersion(
                id = id,
                time = time,
                author = author,
                treeData = dataAndStore.first,
                store = dataAndStore.second,
                previousVersion = null,
                originalVersion = null,
                baseVersion = baseVersion,
                mergedVersion1 = null,
                mergedVersion2 = null,
                operations = OperationsCompressor(CLTree(dataAndStore.first, dataAndStore.second)).compressOperations(operations),
            )
        }

        fun createRegularVersion(
            id: Long,
            time: Instant = Clock.System.now(),
            author: String?,
            tree: ITree,
            baseVersion: CLVersion?,
            operations: Array<IOperation>,
        ): CLVersion {
            return createRegularVersion(
                id = id,
                time = time.epochSeconds.toString(),
                author = author,
                tree = tree,
                baseVersion = baseVersion,
                operations = operations,
            )
        }

        fun loadFromHash(hash: String, store: IDeserializingKeyValueStore): CLVersion {
            return tryLoadFromHash(hash, store) ?: throw RuntimeException("Version with hash $hash not found")
        }

        fun tryLoadFromHash(hash: String, store: IDeserializingKeyValueStore): CLVersion? {
            val data = store.get(hash, { CPVersion.deserialize(it) }) ?: return null
            return CLVersion(data, store)
        }

        fun tryLoadFromHash(hash: String, store: IAsyncObjectStore): IStream.ZeroOrOne<CLVersion> {
            return KVEntryReference(hash, CPVersion.DESERIALIZER).tryGetValue(store).map { CLVersion(it, store) }
        }
    }

    private fun globalizeOps(ops: List<IOperation>): List<IOperation> {
        return ops.map {
            when (it) {
                is SetReferenceOp -> it.withTarget(globalizeNodeRef(it.target))
                else -> it
            }
        }
    }

    private fun globalizeNodeRef(ref: INodeReference?): INodeReference? {
        return when (ref) {
            null -> null
            is LocalPNodeReference -> ref.toGlobal(tree.getId())
            else -> ref
        }
    }

    private fun localizeNodeRef(ref: INodeReference?): INodeReference? {
        return if (ref is PNodeReference && ref.branchId == tree.getId()) ref.toLocal() else ref
    }

    private fun localizeOps(ops: List<IOperation>): List<IOperation> {
        return ops.map {
            when (it) {
                is SetReferenceOp -> it.withTarget(localizeNodeRef(it.target))
                else -> it
            }
        }
    }

    fun getParents(stopAt: CLVersion?): List<CLVersion> {
        if (stopAt != null && this.getContentHash() == stopAt.getContentHash()) {
            return emptyList()
        }
        val ancestors = if (isMerge()) {
            listOf(getMergedVersion1()!!, getMergedVersion2()!!)
        } else {
            listOfNotNull(baseVersion)
        }
        return ancestors.filter { stopAt == null || it.getContentHash() != stopAt.getContentHash() }
    }

    fun collectAncestors(stopAt: CLVersion?, result: MutableMap<String, CLVersion>) {
        if (stopAt != null && this.getContentHash() == stopAt.getContentHash()) return
        if (result.contains(getContentHash())) return
        result[getContentHash()] = this
        for (parent in getParents(stopAt)) {
            parent.collectAncestors(stopAt, result)
        }
    }
}

fun CLVersion.fullDiff(baseVersion: CLVersion?): IStream.Many<IKVValue> {
    val history = historyDiff(baseVersion)
    return history.plus(
        history.flatMap { version ->
            val baseVersion = version.baseVersion?.getValue(asyncStore) ?: IStream.of(null)
            val currentVersion = version.treeHash.getValue(asyncStore)
            val treeDiff = currentVersion.zipWith(baseVersion) { v, b ->
                if (b == null) v.getAllObjects(asyncStore) else v.objectDiff(b, asyncStore)
            }.flatten()
            if (version.operationsHash != null) {
                val operations = version.operationsHash.getValue(asyncStore).flatMap { it.getAllObjects(asyncStore) }
                treeDiff.plus(operations)
            } else {
                treeDiff
            }
        },
    )
}

fun CLVersion.historyDiff(baseVersion: CLVersion?): IStream.Many<CPVersion> {
    val commonBase = VersionMerger.commonBaseVersion(this, baseVersion)
    val history = LinkedHashMap<String, CLVersion>()
    collectAncestors(commonBase, history)
    return IStream.many(history.values.map { it.data })
}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> = filterValues { it != null } as Map<K, V>

fun CLVersion.runWrite(idGenerator: IIdGenerator, author: String?, body: (IWriteTransaction) -> Unit): CLVersion {
    val branch = OTBranch(TreePointer(getTree(), idGenerator), idGenerator, store)
    branch.computeWriteT(body)
    val (ops, newTree) = branch.getPendingChanges()
    return CLVersion.createRegularVersion(
        id = idGenerator.generate(),
        author = author,
        tree = newTree,
        baseVersion = this,
        operations = ops.map { it.getOriginalOp() }.toTypedArray(),
    )
}

fun ITree.extractDataAndStore() = when (this) {
    is CLTree -> this.data to this.asyncStore
    is AsyncAsSynchronousTree -> (this.asyncTree as AsyncTree).let { it.treeData to it.store }
    else -> throw IllegalArgumentException("Unknown tree type: $this")
}

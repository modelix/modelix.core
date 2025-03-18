package org.modelix.model.lazy

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
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
import org.modelix.model.async.ObjectRequest
import org.modelix.model.async.asObjectLoader
import org.modelix.model.async.asObjectWriter
import org.modelix.model.async.getObject
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectHash
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.asObject
import org.modelix.model.objects.getDescendantsAndSelf
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.CPVersion
import org.modelix.streams.IStream
import org.modelix.streams.plus
import kotlin.jvm.JvmName

class CLVersion(val obj: Object<CPVersion>, val asyncStore: IAsyncObjectStore) : IVersion {

    init {
        write()
    }

    @Deprecated("Use asyncStore", ReplaceWith("asyncStore.getLegacyObjectStore()"))
    val store: IDeserializingKeyValueStore get() = asyncStore.getLegacyObjectStore()

    @Deprecated("Use obj", ReplaceWith("obj"))
    val resolvedData: Object<CPVersion> get() = obj

    @Deprecated("Use obj.data.treeHash", ReplaceWith("obj.data.treeHash"))
    val treeRef: ObjectReference<CPTree> get() = resolvedData.data.treeHash

    @Deprecated("Use obj.data", ReplaceWith("obj.data"))
    val data: CPVersion get() = resolvedData.data

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
            return LocalDateTime.parse(dateTimeStr).toInstant(TimeZone.currentSystemDefault())
        } catch (ex: Exception) {}
        return null
    }

    @Deprecated("Use getContentHash()", ReplaceWith("getContentHash()"))
    val hash: String
        get() = getContentHash()

    override fun getContentHash(): String = getObjectHash().toString()
    fun getObjectHash(): ObjectHash = resolvedData.ref.getHash()

    @Deprecated("Use getTree()", ReplaceWith("getTree()"))
    @get:JvmName("getTree_()")
    val tree: CLTree
        get() = CLTree(treeRef.getObject(store), asyncStore)

    override fun getTree(): CLTree = tree

    val baseVersion: CLVersion?
        get() {
            val previousVersionHash = data.baseVersion ?: data.previousVersion ?: return null
            val previousVersion = previousVersionHash.getObject(store)
            return CLVersion(previousVersion, asyncStore)
        }

    val operations: Iterable<IOperation>
        get() {
            val operationsHash = data.operationsHash
            val ops = operationsHash?.let { h ->
                store.getStreamExecutor().query {
                    val loader = asyncStore.asObjectLoader()
                    h.requestData(loader).flatMap { it.getOperations(loader) }.toList()
                }
            }
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

    fun getMergedVersion1() = obj.data.mergedVersion1?.let { CLVersion(it.getObject(asyncStore), asyncStore) }
    fun getMergedVersion2() = obj.data.mergedVersion2?.let { CLVersion(it.getObject(asyncStore), asyncStore) }

    fun write(): String {
        obj.ref.write(asyncStore.asObjectWriter())
        return obj.getHashString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CLVersion

        if (obj.getHash() != other.obj.getHash()) return false

        return true
    }

    override fun hashCode(): Int {
        return obj.getHash().hashCode()
    }

    override fun toString(): String {
        return obj.getHashString()
    }

    companion object {
        val INLINED_OPS_LIMIT = 10

        private fun localizeNodeRef(ref: INodeReference?, tree: Object<CPTree>): INodeReference? {
            return if (ref is PNodeReference && ref.branchId == tree.data.id) ref.toLocal() else ref
        }

        private fun localizeOps(ops: List<IOperation>, tree: Object<CPTree>): List<IOperation> {
            return ops.map {
                when (it) {
                    is SetReferenceOp -> it.withTarget(localizeNodeRef(it.target, tree))
                    else -> it
                }
            }
        }

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
                CPVersion(
                    id = id,
                    time = Clock.System.now().epochSeconds.toString(),
                    author = null,
                    treeHash = dataAndStore.first.ref,
                    previousVersion = null,
                    originalVersion = null,
                    baseVersion = baseVersion.obj.ref,
                    mergedVersion1 = mergedVersion1.obj.ref,
                    mergedVersion2 = mergedVersion2.obj.ref,
                    operations = emptyList(),
                    operationsHash = null,
                    numberOfOperations = 0,
                ).withOperations(localizeOps(operations.toList(), dataAndStore.first))
                    .asObject(),
                store.getAsyncStore(),
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
            val compressedOps = OperationsCompressor(CLTree(dataAndStore.first, dataAndStore.second))
                .compressOperations(operations)
            val localizedOps = localizeOps(compressedOps.toList(), dataAndStore.first)
            return CLVersion(
                CPVersion(
                    id = id,
                    time = time,
                    author = author,
                    treeHash = dataAndStore.first.ref,
                    previousVersion = null,
                    originalVersion = null,
                    baseVersion = baseVersion?.obj?.ref,
                    mergedVersion1 = null,
                    mergedVersion2 = null,
                    operations = emptyList(),
                    operationsHash = null,
                    numberOfOperations = 0,
                ).withOperations(localizedOps).asObject(),
                dataAndStore.second,
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

        fun loadFromHash(hash: String, store: IAsyncObjectStore): CLVersion {
            return store.getStreamExecutor().query { tryLoadFromHash(hash, store).orNull() }
                ?: throw RuntimeException("Version with hash $hash not found")
        }

        fun tryLoadFromHash(hash: String, store: IDeserializingKeyValueStore): CLVersion? {
            val data = store.get(hash, { CPVersion.deserialize(it) }) ?: return null
            return CLVersion(Object(data, ObjectReference(ObjectHash(hash), data)), store.getAsyncStore())
        }

        fun tryLoadFromHash(hash: String, store: IAsyncObjectStore): IStream.ZeroOrOne<CLVersion> {
            return store.get(ObjectRequest(hash, CPVersion.DESERIALIZER))
                .map { CLVersion(Object(it, ObjectReference(ObjectHash(hash), it)), store) }
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

    fun collectAncestors(stopAt: Set<String>, result: MutableMap<String, CLVersion>) {
        if (stopAt.contains(this.getContentHash())) return
        if (result.contains(getContentHash())) return
        result[getContentHash()] = this
        for (parent in listOfNotNull(baseVersion, getMergedVersion1(), getMergedVersion2())) {
            parent.collectAncestors(stopAt, result)
        }
    }
}

fun CLVersion.fullDiff(baseVersion: CLVersion?): IStream.Many<Object<IObjectData>> {
    return diff(ObjectDeltaFilter(knownVersions = setOfNotNull(baseVersion?.getContentHash())))
}

fun CLVersion.diff(filter: ObjectDeltaFilter): IStream.Many<Object<IObjectData>> {
    val loader = asyncStore.asObjectLoader()
    val history = historyDiff(filter)
    return history.flatMap { version ->
        var result: IStream.Many<Object<IObjectData>> = IStream.of(version)
        if (filter.includeTrees) {
            val baseVersion = version.data.baseVersion
            result += if (baseVersion == null) {
                version.data.treeHash.resolve(loader).flatMap { it.getDescendantsAndSelf(loader) }
            } else {
                baseVersion.resolve(loader).flatMap { baseVersion ->
                    version.data.treeHash.diff(baseVersion.data.treeHash, loader)
                }
            }
        }
        if (filter.includeOperations && version.data.operationsHash != null) {
            result += version.data.operationsHash.resolve(loader)
                .flatMap { it.getDescendantsAndSelf(loader) }
        }
        result
    }
}

fun CLVersion.historyDiff(filter: ObjectDeltaFilter): IStream.Many<Object<CPVersion>> {
    if (filter.includeHistory) {
        val knownVersions = asyncStore.getStreamExecutor().query {
            IStream.many(filter.knownVersions).flatMap { CLVersion.tryLoadFromHash(it, asyncStore) }.toList()
        }
        val commonBases = knownVersions.mapNotNull { VersionMerger.commonBaseVersion(this, it) }
            .map { it.getContentHash() }.toSet()
        val history = LinkedHashMap<String, CLVersion>()
        collectAncestors(stopAt = commonBases, result = history)
        return IStream.many(history.values.map { it.resolvedData })
    } else {
        return IStream.of(this.resolvedData)
    }
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

fun ITree.extractDataAndStore(): Pair<Object<CPTree>, IAsyncObjectStore> = when (this) {
    is CLTree -> this.resolvedData to this.asyncStore
    is AsyncAsSynchronousTree -> (this.asyncTree as AsyncTree).let { it.resolvedTreeData to it.store }
    else -> throw IllegalArgumentException("Unknown tree type: $this")
}

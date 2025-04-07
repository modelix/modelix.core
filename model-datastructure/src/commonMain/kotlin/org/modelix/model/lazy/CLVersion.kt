package org.modelix.model.lazy

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.asLegacyTree
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.asObject
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.TreeType
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.ObjectRequest
import org.modelix.model.mutable.INodeIdGenerator
import org.modelix.model.mutable.VersionedModelTree
import org.modelix.model.mutable.getRootNode
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.CPVersion
import org.modelix.streams.IStream
import org.modelix.streams.plus
import org.modelix.streams.query
import kotlin.jvm.JvmName

class CLVersion(val obj: Object<CPVersion>) : IVersion {

    init {
        write()
    }

    @Deprecated("asyncStore parameter isn't required anymore")
    constructor(obj: Object<CPVersion>, asyncStore: IAsyncObjectStore) : this(obj)

    val graph: IObjectGraph get() = obj.graph

    @Deprecated("Use obj", ReplaceWith("obj"))
    val resolvedData: Object<CPVersion> get() = obj

    @Deprecated("Use obj.data", ReplaceWith("obj.data"))
    val data: CPVersion get() = resolvedData.data

    val author: String?
        get() = data.author

    @Deprecated("Use the ObjectHash instead. New versions of Modelix may set this to 0 and not generate actual IDs.")
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

    @Deprecated("Use getObjectHash()", ReplaceWith("getObjectHash()"))
    val hash: String
        get() = getContentHash()

    override fun getContentHash(): String = getObjectHash().toString()
    override fun getObjectHash(): ObjectHash = resolvedData.ref.getHash()
    override fun asObject() = obj

    @Deprecated("Use getTree()", ReplaceWith("getTree()"))
    @get:JvmName("getTree_()")
    val tree: ITree
        get() = getTree()

    override fun getTree(): ITree = getTree(TreeType.MAIN)

    fun getTree(type: TreeType): ITree = data.getTree(type).resolveNow().data.getLegacyModelTree().asLegacyTree()

    fun getTreeReference(type: TreeType): ObjectReference<CPTree> = data.getTree(type)
    fun getTreeReference(): ObjectReference<CPTree> = data.getTree(TreeType.MAIN)

    fun getModelTree(type: TreeType): IGenericModelTree<INodeReference> = data.getTree(type).resolveNow().data.getModelTree()
    override fun getModelTree(): IGenericModelTree<INodeReference> = getModelTree(TreeType.MAIN)

    override fun getTrees(): Map<TreeType, ITree> {
        return graph.getStreamExecutor().query {
            getTreesLater().toMap({ it.first }, { it.second })
        }
    }

    fun getTreesLater(): IStream.Many<Pair<TreeType, ITree>> {
        return IStream.many(data.treeRefs.entries).flatMap { entry ->
            entry.value.resolve().map { tree ->
                entry.key to (tree as IGenericModelTree<Long>).asLegacyTree()
            }
        }
    }

    val baseVersion: CLVersion?
        get() {
            val previousVersionHash = data.baseVersion ?: data.previousVersion ?: return null
            val previousVersion = previousVersionHash.resolveLater().query()
            return CLVersion(previousVersion)
        }

    val operations: Iterable<IOperation>
        get() {
            val operationsHash = data.operationsHash
            val ops = operationsHash?.let { h ->
                graph.getStreamExecutor().query {
                    h.resolveData().flatMap { it.getOperations() }.toList()
                }
            }
                ?: data.operations?.toList()
                ?: emptyList()
            return globalizeOps(ops)
        }

    fun operationsAsStream(): IStream.Many<IOperation> {
        val operationsHash = data.operationsHash
        val operations = if (operationsHash != null) {
            operationsHash.resolveData().flatMap { it.getOperations() }
        } else {
            IStream.many(obj.data.operations?.toList() ?: emptyList())
        }
        return operations.map { globalizeOp(it) }
    }

    val numberOfOperations: Int
        get() = data.numberOfOperations

    fun operationsInlined(): Boolean {
        return data.operations != null
    }

    override fun getAttributes(): Map<String, String> = obj.data.attributes

    fun isMerge() = this.data.mergedVersion1 != null

    fun getMergedVersion1() = obj.data.mergedVersion1?.let { CLVersion(it.resolveLater().query()) }
    fun getMergedVersion2() = obj.data.mergedVersion2?.let { CLVersion(it.resolveLater().query()) }

    fun write(): String {
        obj.write()
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

        fun builder(): VersionBuilder = VersionBuilder()

        private fun localizeNodeRef(ref: INodeReference?, tree: Object<CPTree>): INodeReference? {
            return if (ref is PNodeReference && ref.treeId == tree.data.id.id) ref.toLocal() else ref
        }

        private fun localizeOps(ops: List<IOperation>, tree: Object<CPTree>): List<IOperation> {
            return ops.map {
                when (it) {
                    is SetReferenceOp -> it.withTarget(localizeNodeRef(it.target, tree))
                    else -> it
                }
            }
        }

        @Deprecated("Use builder()")
        fun createAutoMerge(
            id: Long,
            tree: ITree,
            baseVersion: CLVersion,
            mergedVersion1: CLVersion,
            mergedVersion2: CLVersion,
            operations: Array<IOperation>,
            graph: IObjectGraph? = null,
        ): CLVersion {
            return builder()
                .graph(graph)
                .id(id)
                .tree(tree)
                .autoMerge(baseVersion.obj.ref, mergedVersion1.obj.ref, mergedVersion2.obj.ref)
                .operations(operations)
                .buildLegacy()
        }

        @Deprecated("Use builder()")
        fun createRegularVersion(
            id: Long,
            time: String?,
            author: String?,
            tree: ITree,
            baseVersion: CLVersion?,
            operations: Array<IOperation>,
            graph: IObjectGraph? = null,
        ): CLVersion {
            return builder()
                .graph(graph)
                .id(id)
                .time(time)
                .author(author)
                .tree(tree)
                .also { if (baseVersion != null) it.regularUpdate(baseVersion.obj.ref) }
                .operations(operations)
                .buildLegacy()
        }

        @Deprecated("Use builder()")
        fun createRegularVersion(
            id: Long,
            time: Instant = Clock.System.now(),
            author: String?,
            tree: ITree,
            baseVersion: CLVersion?,
            operations: Array<IOperation>,
        ): CLVersion {
            return builder()
                .id(id)
                .time(time)
                .author(author)
                .tree(tree)
                .also { if (baseVersion != null) it.regularUpdate(baseVersion.obj.ref) }
                .operations(operations)
                .buildLegacy()
        }

        fun loadFromHash(hash: String, store: IDeserializingKeyValueStore): CLVersion {
            return tryLoadFromHash(hash, store) ?: throw RuntimeException("Version with hash $hash not found")
        }

        fun loadFromHash(hash: String, store: IAsyncObjectStore): CLVersion {
            return store.getStreamExecutor().query { tryLoadFromHash(hash, store).orNull() }
                ?: throw RuntimeException("Version with hash $hash not found")
        }

        fun tryLoadFromHash(hash: String, store: IDeserializingKeyValueStore): CLVersion? {
            val asyncStore = store.getAsyncStore()
            return asyncStore.query { tryLoadFromHash(hash, asyncStore).orNull() }
        }

        fun tryLoadFromHash(hash: String, store: IAsyncObjectStore): IStream.ZeroOrOne<CLVersion> {
            val graph = store.asObjectGraph()
            return store.get(ObjectRequest(hash, CPVersion.DESERIALIZER, graph))
                .map { CLVersion(it.asObject(ObjectHash(hash), graph)) }
        }
    }

    private fun globalizeOps(ops: List<IOperation>): List<IOperation> {
        return ops.map { globalizeOp(it) }
    }

    private fun globalizeOp(op: IOperation): IOperation {
        return when (op) {
            is SetReferenceOp -> op.withTarget(globalizeNodeRef(op.target))
            else -> op
        }
    }

    private fun globalizeNodeRef(ref: INodeReference?): INodeReference? {
        return when (ref) {
            null -> null
            is LocalPNodeReference -> ref.toGlobal(tree.getId()!!)
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

/**
 * Assuming one peer already has all the data of [baseVersion], this method return those objects that missing in the
 * object graph to fully load [this] version.
 */
fun CLVersion.fullDiff(baseVersion: CLVersion?): IStream.Many<Object<IObjectData>> {
    if (baseVersion?.getObjectHash() == this.getObjectHash()) return IStream.empty()

    val commonBase = baseVersion?.let { commonBaseVersion(it) }

    if (commonBase?.getObjectHash() == this.getObjectHash()) {
        /**
         *  Base version is newer than this version.
         *  There are two cases in which this method is used.
         *  - The client uploads a version to the server:
         *    No need to upload anything. The server already knows this version.
         *  - The server verifies that all objects of a new version exist in the key value store:
         *    It already verified the version before. Also, nothing needs to be traversed.
         */
        return IStream.empty()
    }

    return diff(
        ObjectDeltaFilter(knownVersions = setOfNotNull(baseVersion?.getContentHash())),
        commonBase,
    )
}

fun CLVersion.diff(filter: ObjectDeltaFilter, commonBase: CLVersion?): IStream.Many<Object<IObjectData>> {
    if (this.getObjectHash() == commonBase?.getObjectHash()) {
        TODO()
    }

    val history = historyDiff(filter, commonBase)
    return history.flatMap { version ->
        var result: IStream.Many<Object<IObjectData>> = IStream.of(version)
        if (filter.includeTrees) {
            val baseVersion = version.data.baseVersion
            result += if (baseVersion == null) {
                IStream.many(version.data.treeRefs.values)
                    .flatMap { it.resolve() }
                    .flatMap { it.getDescendantsAndSelf() }
            } else {
                baseVersion.resolve().flatMap { baseVersion ->
                    IStream.many(version.data.treeRefs.entries).flatMap { (type, newTree) ->
                        val oldTree = baseVersion.data.treeRefs[type]
                        if (oldTree == null) {
                            newTree.resolve().flatMap { it.getDescendantsAndSelf() }
                        } else {
                            newTree.diff(oldTree)
                        }
                    }
                }
            }
        }
        val operationsRef = version.data.operationsHash
        if (filter.includeOperations && operationsRef != null) {
            result += operationsRef.resolve().flatMap { it.getDescendantsAndSelf() }
        }
        result
    }
}

/**
 * @param commonBase The common base version of this version and all versions mentioned in the filter.
 */
fun CLVersion.historyDiff(filter: ObjectDeltaFilter, commonBase: CLVersion?): IStream.Many<Object<CPVersion>> {
    if (this.getObjectHash() == commonBase?.getObjectHash()) {
        TODO()
    }

    if (filter.includeHistory) {
        val history = LinkedHashMap<String, CLVersion>()
        collectAncestors(
            stopAt = filter.knownVersions + setOfNotNull(
                commonBase?.takeIf {
                    filter.knownVersions.isNotEmpty()
                }?.getContentHash(),
            ),
            result = history,
        )
        return IStream.many(history.values.map { it.resolvedData })
    } else {
        return IStream.of(this.resolvedData)
    }
}

fun CLVersion.commonBaseVersion(other: CLVersion): CLVersion? {
    var leftVersion: CLVersion? = this
    var rightVersion: CLVersion? = other
    val leftVersions: MutableSet<ObjectHash> = HashSet()
    val rightVersions: MutableSet<ObjectHash> = HashSet()
    leftVersions.add(this.getObjectHash())
    rightVersions.add(other.getObjectHash())

    while (leftVersion != null || rightVersion != null) {
        val leftBaseRef = leftVersion?.obj?.data?.baseVersion
        val rightBaseRef = rightVersion?.obj?.data?.baseVersion
        leftBaseRef?.let { leftVersions.add(it.getHash()) }
        rightBaseRef?.let { rightVersions.add(it.getHash()) }

        if (leftVersion != null) {
            if (rightVersions.contains(leftVersion.getObjectHash())) {
                return leftVersion
            }
        }
        if (rightVersion != null) {
            if (leftVersions.contains(rightVersion.getObjectHash())) {
                return rightVersion
            }
        }

        val leftLoadedBase = leftBaseRef?.getLoadedData()
        val rightLoadedBase = rightBaseRef?.getLoadedData()

        if (leftLoadedBase != null || rightLoadedBase != null) {
            // As long as one of the versions is available without sending a query, follow that path. The probability
            // is high that the common base is found in there, and we don't have to send any queries at all.

            if (leftLoadedBase != null) {
                leftVersion = CLVersion(Object(leftLoadedBase, leftBaseRef))
            }
            if (rightLoadedBase != null) {
                rightVersion = CLVersion(Object(rightLoadedBase, rightBaseRef))
            }
        } else {
            if (leftVersion != null) {
                leftVersion = leftVersion.baseVersion
            }
            if (rightVersion != null) {
                rightVersion = rightVersion.baseVersion
            }
        }
    }
    return null
}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> = filterValues { it != null } as Map<K, V>

fun CLVersion.runWrite(idGenerator: IIdGenerator, author: String?, body: (IWriteTransaction) -> Unit): CLVersion {
    val branch = OTBranch(TreePointer(getTree(), idGenerator), idGenerator)
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

fun CLVersion.runWriteWithNode(idGenerator: IIdGenerator, author: String?, body: (IWritableNode) -> Unit): CLVersion {
    val branch = OTBranch(TreePointer(getTree(), idGenerator), idGenerator)
    branch.runWrite {
        body(branch.getRootNode().asWritableNode())
    }
    val (ops, newTree) = branch.getPendingChanges()
    return CLVersion.createRegularVersion(
        id = idGenerator.generate(),
        author = author,
        tree = newTree,
        baseVersion = this,
        operations = ops.map { it.getOriginalOp() }.toTypedArray(),
    )
}

fun IVersion.runWriteOnModel(
    versionIdGenerator: IIdGenerator,
    nodeIdGenerator: INodeIdGenerator<INodeReference>,
    author: String?,
    body: (IWritableNode) -> Unit,
): IVersion {
    val baseVersion = this as CLVersion
    val mutableTree = VersionedModelTree(baseVersion, nodeIdGenerator)
    mutableTree.runWrite {
        body(mutableTree.getRootNode())
    }
    return mutableTree.createVersion(versionIdGenerator.generate(), author) ?: baseVersion
}

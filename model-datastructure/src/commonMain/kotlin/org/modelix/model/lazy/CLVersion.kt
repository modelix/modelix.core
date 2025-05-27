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
import org.modelix.model.VersionAndHash
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
import org.modelix.model.historyDiff
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.INodeIdGenerator
import org.modelix.model.mutable.VersionedModelTree
import org.modelix.model.mutable.getRootNode
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.CPVersion
import org.modelix.streams.IStream
import org.modelix.streams.getBlocking
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

    val data: CPVersion get() = resolvedData.data

    val author: String?
        get() = data.author

    @Deprecated("Use the ObjectHash instead. New versions of Modelix may set this to 0 and not generate actual IDs.")
    val id: Long
        get() = data.id

    @Deprecated("Use getTimestamp()")
    val time: String?
        get() = data.time

    override fun getTimestamp(): Instant? {
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

    override fun getParentVersions(): List<IVersion> {
        return if (isMerge()) listOfNotNull(getMergedVersion1(), getMergedVersion2()) else listOfNotNull(baseVersion)
    }

    override fun tryGetParentVersions(): List<VersionAndHash> {
        return if (isMerge()) {
            listOfNotNull(
                obj.data.mergedVersion1?.tryResolve(),
                obj.data.mergedVersion2?.tryResolve(),
            )
        } else {
            listOfNotNull(
                obj.data.baseVersion?.tryResolve(),
            )
        }
    }

    fun getParentHashes(): List<ObjectHash> {
        return if (isMerge()) {
            listOf(obj.data.mergedVersion1!!.getHash(), obj.data.mergedVersion2!!.getHash())
        } else {
            listOfNotNull(obj.data.baseVersion?.getHash())
        }
    }

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

        fun loadFromHash(hash: ObjectHash, graph: IObjectGraph): CLVersion {
            return requestFromHash(hash, graph).getBlocking(graph)
        }

        fun requestFromHash(hash: ObjectHash, graph: IObjectGraph): IStream.One<CLVersion> {
            return graph.fromHash(hash, CPVersion).resolve().map { CLVersion(it) }
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
 * Assuming one peer already has all the data of [knownVersions], this method return those objects that are missing in
 * the object graph to fully load [this] version.
 *
 * The values in [ObjectDeltaFilter.knownVersions] are ignored and expected to be resolved and included in
 * [knownVersions] before calling this method.
 */
fun IVersion.diff(knownVersions: List<IVersion>, filter: ObjectDeltaFilter = ObjectDeltaFilter()): IStream.Many<Object<IObjectData>> {
    this as CLVersion

    val unknownHistory: List<CLVersion> = historyDiff(knownVersions).toList().map { it as CLVersion }

    if (unknownHistory.isEmpty()) return IStream.empty()

    val allKnownVersions: MutableMap<ObjectHash, CLVersion> = knownVersions
        .associate { it.getObjectHash() to (it as CLVersion) }
        .toMutableMap()

    val includedVersions = if (filter.includeHistory) unknownHistory else listOf(this)
    return IStream.many(includedVersions.reversed()).flatMap { version ->
        var result: IStream.Many<Object<IObjectData>> = IStream.of(version.asObject())
        if (filter.includeTrees) {
            /**
             * For the tree diff it is valid to use any tree as the base that is known to the other side.
             * That can be one of the versions directly mentioned in [ObjectDeltaFilter.knownVersions] or one of the
             * versions also included in this diff.
             *
             * Use the version closest to [this] version as the base because that one should produce the smallest diff.
             */
            val baseVersion = version.data.baseVersion
                ?.takeIf { allKnownVersions.contains(it.getHash()) }
                ?.tryResolve()?.version?.getOrNull()?.let { it as CLVersion }
                ?: allKnownVersions.values.lastOrNull()
            result += if (baseVersion == null) {
                IStream.many(version.data.treeRefs.values)
                    .flatMap { it.resolve() }
                    .flatMap { it.getDescendantsAndSelf() }
            } else {
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
        val operationsRef = version.data.operationsHash
        if (filter.includeOperations && operationsRef != null) {
            result += operationsRef.resolve().flatMap { it.getDescendantsAndSelf() }
        }

        allKnownVersions.put(version.getObjectHash(), version)

        result
    }
}

fun IVersion.diff(knownVersion: IVersion?, filter: ObjectDeltaFilter = ObjectDeltaFilter()) = diff(listOfNotNull(knownVersion), filter)

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> = filterValues { it != null } as Map<K, V>

@Deprecated("Use runWriteOnModel instead")
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

@Deprecated("Use runWriteOnModel instead")
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
    nodeIdGenerator: INodeIdGenerator<INodeReference>,
    author: String?,
    body: (IWritableNode) -> Unit,
): IVersion {
    return runWriteOnTree(nodeIdGenerator, author) { body(it.getRootNode()) }
}

fun IVersion.runWriteOnTree(
    nodeIdGenerator: INodeIdGenerator<INodeReference>,
    author: String?,
    body: (IMutableModelTree) -> Unit,
): IVersion {
    val baseVersion = this as CLVersion
    val mutableTree = VersionedModelTree(baseVersion, nodeIdGenerator)
    mutableTree.runWrite {
        body(mutableTree)
    }
    return mutableTree.createVersion(author) ?: baseVersion
}

fun ObjectReference<CPVersion>.tryResolve(): VersionAndHash {
    return VersionAndHash(getHash(), runCatching { CLVersion(resolveNow()) })
}

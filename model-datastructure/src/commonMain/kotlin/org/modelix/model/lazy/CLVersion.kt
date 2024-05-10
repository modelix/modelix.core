/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.lazy

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore
import org.modelix.model.IVersion
import org.modelix.model.LinearHistory
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.TreePointer
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPOperationsList
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.CPVersion
import kotlin.jvm.JvmName

class CLVersion : IVersion {
    var store: IDeserializingKeyValueStore
    var data: CPVersion? = null
        private set
    val treeHash: KVEntryReference<CPTree>?

    private constructor(
        id: Long,
        time: String?,
        author: String?,
        tree: CLTree,
        previousVersion: CLVersion?,
        originalVersion: CLVersion?,
        baseVersion: CLVersion?,
        mergedVersion1: CLVersion?,
        mergedVersion2: CLVersion?,
        operations: Array<IOperation>,
    ) {
        this.store = tree.store
        this.treeHash = KVEntryReference(tree.data)
        val localizedOps = localizeOps(operations.asList()).toTypedArray()
        if (localizedOps.size <= INLINED_OPS_LIMIT) {
            data = CPVersion(
                id = id,
                time = time,
                author = author,
                treeHash = this.treeHash,
                previousVersion = previousVersion?.let { KVEntryReference(it.data!!) },
                originalVersion = originalVersion?.let { KVEntryReference(it.data!!) },
                baseVersion = baseVersion?.let { KVEntryReference(it.data!!) },
                mergedVersion1 = mergedVersion1?.let { KVEntryReference(it.data!!) },
                mergedVersion2 = mergedVersion2?.let { KVEntryReference(it.data!!) },
                operations = localizedOps,
                operationsHash = null,
                numberOfOperations = localizedOps.size,
            )
        } else {
            val opsList = CPOperationsList(localizedOps)
            data = CPVersion(
                id = id,
                time = time,
                author = author,
                treeHash = this.treeHash,
                previousVersion = previousVersion?.let { KVEntryReference(it.data!!) },
                originalVersion = originalVersion?.let { KVEntryReference(it.data!!) },
                baseVersion = baseVersion?.let { KVEntryReference(it.data!!) },
                mergedVersion1 = mergedVersion1?.let { KVEntryReference(it.data!!) },
                mergedVersion2 = mergedVersion2?.let { KVEntryReference(it.data!!) },
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
    constructor(data: CPVersion?, store: IDeserializingKeyValueStore) {
        if (data == null) {
            throw NullPointerException("data is null")
        }
        this.data = data
        this.treeHash = data.treeHash
        this.store = store
    }

    val author: String?
        get() = data!!.author

    val id: Long
        get() = data!!.id

    @Deprecated("Use getTimestamp()")
    val time: String?
        get() = data!!.time

    fun getTimestamp(): Instant? {
        val dateTimeStr = data!!.time ?: return null
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
        get() = data!!.hash

    override fun getContentHash(): String = data!!.hash

    @Deprecated("Use getTree()", ReplaceWith("getTree()"))
    @get:JvmName("getTree_()")
    val tree: CLTree
        get() = CLTree(treeHash!!.getValue(store), store)

    override fun getTree(): CLTree = tree

    val baseVersion: CLVersion?
        get() {
            val previousVersionHash = data!!.baseVersion ?: data!!.previousVersion ?: return null
            val previousVersion = previousVersionHash.getValue(store)
            return CLVersion(previousVersion, store)
        }

    val operations: Iterable<IOperation>
        get() {
            val operationsHash = data!!.operationsHash
            val ops = operationsHash?.getValue(store)?.operations ?: data!!.operations
            return globalizeOps((ops ?: arrayOf()).toList())
        }

    val numberOfOperations: Int
        get() = data!!.numberOfOperations

    fun operationsInlined(): Boolean {
        return data!!.operations != null
    }

    fun isMerge() = this.data!!.mergedVersion1 != null

    fun getMergedVersion1() = this.data!!.mergedVersion1?.let { CLVersion(it.getValue(store), store) }
    fun getMergedVersion2() = this.data!!.mergedVersion2?.let { CLVersion(it.getValue(store), store) }

    fun write(): String {
        KVEntryReference(data!!).write(store)
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CLVersion

        if (data?.id != other.data?.id) return false

        return true
    }

    override fun hashCode(): Int {
        return data?.id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return hash
    }

    companion object {
        val INLINED_OPS_LIMIT = 10
        fun createAutoMerge(
            id: Long,
            tree: CLTree,
            baseVersion: CLVersion,
            mergedVersion1: CLVersion,
            mergedVersion2: CLVersion,
            operations: Array<IOperation>,
            store: IDeserializingKeyValueStore,
        ) = CLVersion(
            id = id,
            time = null,
            author = null,
            tree = tree,
            previousVersion = null,
            originalVersion = null,
            baseVersion = baseVersion,
            mergedVersion1 = mergedVersion1,
            mergedVersion2 = mergedVersion2,
            operations = operations,
        )

        fun createRegularVersion(
            id: Long,
            time: String?,
            author: String?,
            tree: CLTree,
            baseVersion: CLVersion?,
            operations: Array<IOperation>,
        ): CLVersion = CLVersion(
            id = id,
            time = time,
            author = author,
            tree = tree,
            previousVersion = null,
            originalVersion = null,
            baseVersion = baseVersion,
            mergedVersion1 = null,
            mergedVersion2 = null,
            operations = OperationsCompressor(tree).compressOperations(operations),
        )

        fun createRegularVersion(
            id: Long,
            time: Instant = Clock.System.now(),
            author: String?,
            tree: CLTree,
            baseVersion: CLVersion?,
            operations: Array<IOperation>,
        ): CLVersion = createRegularVersion(
            id = id,
            time = time.epochSeconds.toString(),
            author = author,
            tree = tree,
            baseVersion = baseVersion,
            operations = operations,
        )

        fun loadFromHash(hash: String, store: IDeserializingKeyValueStore): CLVersion {
            return tryLoadFromHash(hash, store) ?: throw RuntimeException("Version with hash $hash not found")
        }

        fun tryLoadFromHash(hash: String, store: IDeserializingKeyValueStore): CLVersion? {
            val data = store[hash, { CPVersion.deserialize(it) }] ?: return null
            return CLVersion(data, store)
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
}

fun CLVersion.computeDelta(baseVersion: CLVersion?): Map<String, String> {
    return computeDelta(store.keyValueStore, this.getContentHash(), baseVersion?.getContentHash()).filterNotNullValues()
}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> = filterValues { it != null } as Map<K, V>

private fun computeDelta(keyValueStore: IKeyValueStore, versionHash: String, baseVersionHash: String?): Map<String, String?> {
    val changedNodeIds = HashSet<Long>()
    val oldAndNewEntries: Map<String, String?> = trackAccessedEntries(keyValueStore) { store ->
        val version = CLVersion(versionHash, store)
//        generateSequence(version) { it.baseVersion }.map { it.getTree() }.count()

        val visitedVersions = HashSet<String>()
        if (baseVersionHash != null) visitedVersions += baseVersionHash
        fun iterateHistory(v: CLVersion?) {
            if (v == null) return
            if (v.getContentHash() == baseVersionHash) return
            if (visitedVersions.contains(v.getContentHash())) return
            visitedVersions += v.getContentHash()
            val tree = v.getTree()
            v.operations.forEach {
                // we only need to record the required entries
                runCatching {
                    it.captureIntend(tree, store)
                }
            }
            iterateHistory(v.baseVersion)
            iterateHistory(v.getMergedVersion1())
            iterateHistory(v.getMergedVersion2())
        }
        iterateHistory(version)

        val baseVersion = baseVersionHash?.let { CLVersion(it, store) }
//        if (baseVersion != null) {
//            VersionMerger(store, IdGenerator.newInstance(0)).mergeChange(version, baseVersion)
//        }

        val history = LinearHistory(baseVersionHash).load(version)
        val bulkQuery = store.newBulkQuery()
        var v1 = baseVersion
        for (v2 in history) {
            v2.operations // include them in the result

            if (v1 == null) {
                v2.getTree().getDescendants(v2.getTree().root!!.id, true)
                continue
            }

            val oldTree = v1.getTree()
            v2.getTree().nodesMap!!.visitChanges(
                oldTree.nodesMap!!,
                object : CPHamtNode.IChangeVisitor {
                    override fun visitChangesOnly(): Boolean = false
                    override fun entryAdded(key: Long, value: KVEntryReference<CPNode>) {
                        changedNodeIds += key
                        if (value != null) bulkQuery.query(value)
                    }

                    override fun entryRemoved(key: Long, value: KVEntryReference<CPNode>) {
                        changedNodeIds += key
                    }

                    override fun entryChanged(
                        key: Long,
                        oldValue: KVEntryReference<CPNode>,
                        newValue: KVEntryReference<CPNode>,
                    ) {
                        changedNodeIds += key
                        if (newValue != null) bulkQuery.query(newValue)
                    }
                },
                bulkQuery,
            )
            v1 = v2
        }
        (bulkQuery as? BulkQuery)?.executeQuery()
    }
    val oldEntries: Map<String, String?> = trackAccessedEntries(keyValueStore) { store ->
        if (baseVersionHash == null) return@trackAccessedEntries

        // record read access on the version data itself
        val baseVersion = CLVersion(baseVersionHash, store)

        // The operations may not be available on the client, but then they don't need to be part of the delta anyway.
        // Ignoring that case should be safe.
        runCatching { baseVersion.operations }

        val oldTree = baseVersion.getTree()
        val bulkQuery = store.newBulkQuery()

        val nodesMap = oldTree.nodesMap!!
        changedNodeIds.forEach { changedNodeId ->
            nodesMap.get(changedNodeId, 0, bulkQuery).onReceive { nodeRef: KVEntryReference<CPNode>? ->
                if (nodeRef != null) bulkQuery.query(nodeRef)
            }
        }

        (bulkQuery as? BulkQuery)?.executeQuery()
    }
    return oldAndNewEntries - oldEntries.keys
}

private fun trackAccessedEntries(store: IKeyValueStore, body: (IDeserializingKeyValueStore) -> Unit): Map<String, String?> {
    val accessTrackingStore = AccessTrackingStore(store)
    val objectStore = ObjectStoreCache(accessTrackingStore)
    body(objectStore)
    return accessTrackingStore.accessedEntries
}

private class AccessTrackingStore(val store: IKeyValueStore) : IKeyValueStore {
    val accessedEntries: MutableMap<String, String?> = LinkedHashMap()

    override fun newBulkQuery(deserializingCache: IDeserializingKeyValueStore): IBulkQuery {
        return store.newBulkQuery(deserializingCache)
    }

    override fun get(key: String): String? {
        val value = store.get(key)
        accessedEntries.put(key, value)
        return value
    }

    override fun put(key: String, value: String?) {
        TODO("Not yet implemented")
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val entries = store.getAll(keys)
        accessedEntries.putAll(entries)
        return entries
    }

    override fun putAll(entries: Map<String, String?>) {
        TODO("Not yet implemented")
    }

    override fun prefetch(key: String) {
        TODO("Not yet implemented")
    }

    override fun listen(key: String, listener: IKeyListener) {
        TODO("Not yet implemented")
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        TODO("Not yet implemented")
    }

    override fun getPendingSize(): Int {
        TODO("Not yet implemented")
    }
}

fun CLVersion.runWrite(idGenerator: IIdGenerator, author: String?, body: (IWriteTransaction) -> Unit): CLVersion {
    val branch = OTBranch(TreePointer(getTree(), idGenerator), idGenerator, store)
    branch.computeWriteT(body)
    val (ops, newTree) = branch.getPendingChanges()
    return CLVersion.createRegularVersion(
        id = idGenerator.generate(),
        author = author,
        tree = newTree as CLTree,
        baseVersion = this,
        operations = ops.map { it.getOriginalOp() }.toTypedArray(),
    )
}

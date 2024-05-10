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

package org.modelix.model.persistent

import org.modelix.model.bitCount
import org.modelix.model.lazy.COWArrays
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.NonBulkQuery
import org.modelix.model.persistent.SerializationUtil.intToHex

class CPHamtInternal(
    val bitmap: Int,
    val children: Array<KVEntryReference<CPHamtNode>>,
) : CPHamtNode() {
    val data get() = this

    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = children.asList()

    override fun serialize(): String {
        return "I" +
            Separators.LEVEL1 +
            intToHex(bitmap) +
            Separators.LEVEL1 +
            (if (children.isEmpty()) "" else children.joinToString(Separators.LEVEL2) { it.getHash() })
    }

    companion object {
        fun createEmpty() = create(0, arrayOf())

        fun create(bitmap: Int, childHashes: Array<KVEntryReference<CPHamtNode>>): CPHamtInternal {
            return CPHamtInternal(bitmap, childHashes)
        }

        fun create(key: Long, childHash: KVEntryReference<CPNode>, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
            return createEmpty().put(key, childHash, shift, store)
        }

        fun replace(single: CPHamtSingle): CPHamtInternal {
            if (single.numLevels != 1) throw RuntimeException("Can only replace single level nodes")
            val data: CPHamtSingle = single
            val logicalIndex: Int = data.bits.toInt()
            return create(1 shl logicalIndex, arrayOf(data.child))
        }
    }

    override fun calculateSize(bulkQuery: IBulkQuery): IBulkQuery.Value<Long> {
        val childRefs = data.children
        return bulkQuery
            .flatMap(childRefs.asIterable(), { bulkQuery.query(it) })
            .flatMap { resolvedChildren: List<CPHamtNode?> ->
                val resolvedChildrenNN = resolvedChildren.mapIndexed { index, child ->
                    child ?: throw RuntimeException("Entry not found in store: " + childRefs[index].getHash())
                }
                bulkQuery.flatMap(resolvedChildrenNN) { it.calculateSize(bulkQuery) }
            }
            .map { it.reduce { a, b -> a + b } }
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        val child = getChild(childIndex, NonBulkQuery(store)).executeQuery()
        return if (child == null) {
            setChild(childIndex, CPHamtLeaf.create(key, value), shift, store)
        } else {
            setChild(childIndex, child.put(key, value, shift + CPHamtNode.BITS_PER_LEVEL, store), shift, store)
        }
    }

    override fun remove(key: Long, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        val child = getChild(childIndex, NonBulkQuery(store)).executeQuery()
        return if (child == null) {
            this
        } else {
            setChild(childIndex, child.remove(key, shift + CPHamtNode.BITS_PER_LEVEL, store), shift, store)
        }
    }

    override fun get(key: Long, shift: Int, bulkQuery: IBulkQuery): IBulkQuery.Value<KVEntryReference<CPNode>?> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex, bulkQuery).flatMap { child: CPHamtNode? ->
            if (child == null) {
                bulkQuery.constant(null)
            } else {
                child.get(key, shift + CPHamtNode.BITS_PER_LEVEL, bulkQuery)
            }
        }
    }

    protected fun getChild(logicalIndex: Int, bulkQuery: IBulkQuery): IBulkQuery.Value<CPHamtNode?> {
        if (isBitNotSet(data.bitmap, logicalIndex)) {
            return bulkQuery.constant(null as CPHamtNode?)
        }
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        require(physicalIndex < data.children.size) { "Invalid physical index ($physicalIndex). N. children: ${data.children.size}. Logical index: $logicalIndex" }
        val childHash = data.children[physicalIndex]
        return getChild(childHash, bulkQuery)
    }

    protected fun getChild(childHash: KVEntryReference<CPHamtNode>, bulkQuery: IBulkQuery): IBulkQuery.Value<CPHamtNode> {
        return bulkQuery.query(childHash).map { childData ->
            if (childData == null) throw RuntimeException("Entry not found in store: ${childHash.getHash()}")
            childData
        }
    }

    protected fun getChild(logicalIndex: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        return getChild(logicalIndex, NonBulkQuery(store)).executeQuery()
    }

    protected fun getChild(childHash: KVEntryReference<CPHamtNode>, store: IDeserializingKeyValueStore): CPHamtNode? {
        return getChild(childHash, NonBulkQuery(store)).executeQuery()
    }

    fun setChild(logicalIndex: Int, child: CPHamtNode?, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        if (child == null) {
            return deleteChild(logicalIndex, store)
        }
        val childHash = KVEntryReference(child)
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        val newNode = if (isBitNotSet(data.bitmap, logicalIndex)) {
            create(
                data.bitmap or (1 shl logicalIndex),
                COWArrays.insert(data.children, physicalIndex, childHash),
            )
        } else {
            create(
                data.bitmap,
                COWArrays.set(data.children, physicalIndex, childHash),
            )
        }
        return if (shift < CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL) CPHamtSingle.replaceIfSingleChild(newNode, store) else newNode
    }

    fun deleteChild(logicalIndex: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        if (isBitNotSet(data.bitmap, logicalIndex)) {
            return this
        }
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        val newBitmap = data.bitmap and (1 shl logicalIndex).inv()
        if (newBitmap == 0) {
            return null
        }
        val newChildren = COWArrays.removeAt(data.children, physicalIndex)
        if (newChildren.size == 1) {
            val child0 = getChild(newChildren[0], NonBulkQuery(store)).executeQuery()
            if (child0 is CPHamtLeaf) {
                return child0
            }
        }
        return create(newBitmap, newChildren)
    }

    override fun visitEntries(bulkQuery: IBulkQuery, visitor: (Long, KVEntryReference<CPNode>) -> Unit): IBulkQuery.Value<Unit> {
        return bulkQuery.flatMap(data.children.asIterable()) { bulkQuery.query(it) }.flatMap { children ->
            bulkQuery.flatMap(children) { it!!.visitEntries(bulkQuery, visitor) }.map { }
        }
    }

    override fun visitChanges(oldNode: CPHamtNode?, shift: Int, visitor: CPHamtNode.IChangeVisitor, bulkQuery: IBulkQuery) {
        if (oldNode === this || data.hash == oldNode?.hash) {
            return
        }
        when (oldNode) {
            is CPHamtInternal -> {
                val oldInternalNode: CPHamtInternal = oldNode
                if (data.bitmap == oldInternalNode.data.bitmap) {
                    for (i in data.children.indices) {
                        val oldChildHash = oldInternalNode.data.children[i]
                        val newChildHash = data.children[i]
                        if (oldChildHash != newChildHash) {
                            getChild(newChildHash, bulkQuery).map { child ->
                                oldInternalNode.getChild(oldChildHash, bulkQuery).map { oldChild ->
                                    child!!.visitChanges(oldChild, shift + CPHamtNode.BITS_PER_LEVEL, visitor, bulkQuery)
                                }
                            }
                        }
                    }
                } else {
                    for (logicalIndex in 0 until CPHamtNode.ENTRIES_PER_LEVEL) {
                        getChild(logicalIndex, bulkQuery).map { child ->
                            oldInternalNode.getChild(logicalIndex, bulkQuery).map { oldChild ->
                                if (child == null) {
                                    if (oldChild == null) {
                                        // no change
                                    } else {
                                        if (!visitor.visitChangesOnly()) {
                                            oldChild.visitEntries(bulkQuery) { key, value ->
                                                visitor.entryRemoved(key, value)
                                            }
                                        }
                                    }
                                } else {
                                    if (oldChild == null) {
                                        if (!visitor.visitChangesOnly()) {
                                            child.visitEntries(bulkQuery) { key, value ->
                                                visitor.entryAdded(key, value)
                                            }
                                        }
                                    } else {
                                        child.visitChanges(oldChild, shift + CPHamtNode.BITS_PER_LEVEL, visitor, bulkQuery)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is CPHamtLeaf -> {
                if (visitor.visitChangesOnly()) {
                    get(oldNode.key, shift, bulkQuery).map { newValue ->
                        if (newValue != null && newValue != oldNode.value) {
                            visitor.entryChanged(oldNode.key, oldNode.value, newValue)
                        }
                    }
                } else {
                    var oldEntryExists = false
                    var entryVisitingDone = false
                    visitEntries(bulkQuery) { k, v ->
                        check(!entryVisitingDone)
                        if (k == oldNode.key) {
                            oldEntryExists = true
                            val oldValue = oldNode.value
                            if (v != oldValue) {
                                visitor.entryChanged(k, oldValue, v)
                            }
                        } else {
                            visitor.entryAdded(k, v)
                        }
                    }.onReceive {
                        entryVisitingDone = true
                        if (!oldEntryExists) visitor.entryRemoved(oldNode.key, oldNode.value)
                    }
                }
            }
            is CPHamtSingle -> {
                if (oldNode.numLevels == 1) {
                    visitChanges(CPHamtInternal.replace(oldNode), shift, visitor, bulkQuery)
                } else {
                    visitChanges(CPHamtInternal.replace(oldNode.splitOneLevel()), shift, visitor, bulkQuery)
                }
            }
            else -> {
                throw RuntimeException("Unknown type: " + oldNode!!::class.simpleName)
            }
        }
    }

    private fun isBitNotSet(bitmap: Int, logicalIndex: Int): Boolean {
        return bitmap and (1 shl logicalIndex) == 0
    }

    private fun logicalToPhysicalIndex(bitmap: Int, logicalIndex: Int): Int {
        return bitCount(bitmap and (1 shl logicalIndex) - 1)
    }
}

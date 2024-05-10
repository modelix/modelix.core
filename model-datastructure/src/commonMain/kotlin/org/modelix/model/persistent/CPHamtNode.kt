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

import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.NonBulkQuery
import org.modelix.model.persistent.SerializationUtil.intFromHex
import org.modelix.model.persistent.SerializationUtil.longFromHex
import kotlin.jvm.JvmStatic

/**
 * Implementation of a hash array mapped trie.
 */
abstract class CPHamtNode : IKVValue {
    override var isWritten: Boolean = false

    override val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) { HashUtil.sha256(serialize()) }

    override fun getDeserializer(): (String) -> IKVValue = DESERIALIZER

    protected fun createEmptyNode(): CPHamtNode {
        return CPHamtInternal(0, arrayOf())
    }

    abstract fun calculateSize(bulkQuery: IBulkQuery): IBulkQuery.Value<Long>

    fun get(key: Long, store: IDeserializingKeyValueStore): KVEntryReference<CPNode>? {
        val bulkQuery: IBulkQuery = NonBulkQuery(store)
        return get(key, 0, bulkQuery).executeQuery()
    }

    fun getAll(keys: Iterable<Long>, bulkQuery: IBulkQuery): IBulkQuery.Value<List<KVEntryReference<CPNode>?>> {
        return bulkQuery.flatMap(keys) { key: Long -> get(key, 0, bulkQuery) }
    }

    fun put(key: Long, value: KVEntryReference<CPNode>?, store: IDeserializingKeyValueStore): CPHamtNode? {
        return put(key, value, 0, store)
    }

    fun put(data: CPNode, store: IDeserializingKeyValueStore): CPHamtNode? {
        return put(data.id, KVEntryReference(data), store)
    }

    fun remove(key: Long, store: IDeserializingKeyValueStore): CPHamtNode? {
        return remove(key, 0, store)
    }

    fun remove(element: CPNode, store: IDeserializingKeyValueStore): CPHamtNode? {
        return remove(element.id, store)
    }

    fun get(key: Long, bulkQuery: IBulkQuery): IBulkQuery.Value<KVEntryReference<CPNode>?> = get(key, 0, bulkQuery)

    abstract fun get(key: Long, shift: Int, bulkQuery: IBulkQuery): IBulkQuery.Value<KVEntryReference<CPNode>?>
    abstract fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode?
    abstract fun remove(key: Long, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode?
    abstract fun visitEntries(bulkQuery: IBulkQuery, visitor: (Long, KVEntryReference<CPNode>) -> Unit): IBulkQuery.Value<Unit>
    abstract fun visitChanges(oldNode: CPHamtNode?, shift: Int, visitor: IChangeVisitor, bulkQuery: IBulkQuery)
    fun visitChanges(oldNode: CPHamtNode?, visitor: IChangeVisitor, bulkQuery: IBulkQuery) = visitChanges(oldNode, 0, visitor, bulkQuery)
    interface IChangeVisitor {
        fun visitChangesOnly(): Boolean
        fun entryAdded(key: Long, value: KVEntryReference<CPNode>)
        fun entryRemoved(key: Long, value: KVEntryReference<CPNode>)
        fun entryChanged(key: Long, oldValue: KVEntryReference<CPNode>, newValue: KVEntryReference<CPNode>)
    }

    companion object {
        const val BITS_PER_LEVEL = 5
        const val ENTRIES_PER_LEVEL = 1 shl BITS_PER_LEVEL
        const val LEVEL_MASK: Long = (-0x1 ushr 32 - BITS_PER_LEVEL).toLong()
        const val MAX_BITS = 64
        const val MAX_SHIFT = MAX_BITS - 1
        const val MAX_LEVELS = (MAX_BITS + BITS_PER_LEVEL - 1) / BITS_PER_LEVEL

        fun indexFromKey(key: Long, shift: Int): Int = levelBits(key, shift)

        fun levelBits(key: Long, shift: Int): Int {
            val s = MAX_BITS - BITS_PER_LEVEL - shift
            return if (s >= 0) {
                ((key ushr s) and LEVEL_MASK).toInt()
            } else {
                ((key shl -s) and LEVEL_MASK).toInt()
            }
        }

        val DESERIALIZER = { s: String -> deserialize(s) }

        @JvmStatic
        fun deserialize(input: String): CPHamtNode {
            val parts = input.split(Separators.LEVEL1)
            val data = when (parts[0]) {
                "L" -> CPHamtLeaf(longFromHex(parts[1]), KVEntryReference(parts[2], CPNode.DESERIALIZER))
                "I" -> CPHamtInternal(
                    intFromHex(parts[1]),
                    parts[2].split(Separators.LEVEL2)
                        .filter { it.isNotEmpty() }
                        .map { KVEntryReference(it, DESERIALIZER) }
                        .toTypedArray(),
                )
                "S" -> CPHamtSingle(
                    parts[1].toInt(),
                    longFromHex(parts[2]),
                    KVEntryReference(parts[3], DESERIALIZER),
                )
                else -> throw RuntimeException("Unknown type: " + parts[0] + ", input: " + input)
            }
            data.isWritten = true
            return data
        }
    }
}

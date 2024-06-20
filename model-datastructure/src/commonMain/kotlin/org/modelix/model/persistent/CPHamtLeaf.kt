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
import org.modelix.model.persistent.SerializationUtil.longToHex

class CPHamtLeaf(
    val key: Long,
    val value: KVEntryReference<CPNode>,
) : CPHamtNode() {
    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf(value)

    override fun serialize(): String {
        return """L/${longToHex(key)}/${value.getHash()}"""
    }

    override fun calculateSize(bulkQuery: IBulkQuery): IBulkQuery.Value<Long> {
        return bulkQuery.constant(1L)
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) {
            if (value?.getHash() == this.value?.getHash()) {
                this
            } else {
                create(key, value)
            }
        } else {
            var result: CPHamtNode? = createEmptyNode()
            result = result!!.put(this.key, this.value, shift, store)
            if (result == null) {
                result = createEmptyNode()
            }
            result = result.put(key, value, shift, store)
            result
        }
    }

    override fun remove(key: Long, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) {
            null
        } else {
            this
        }
    }

    override fun get(key: Long, shift: Int, bulkQuery: IBulkQuery): IBulkQuery.Value<KVEntryReference<CPNode>?> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return bulkQuery.constant(if (key == this.key) value else null)
    }

    override fun visitEntries(bulkQuery: IBulkQuery, visitor: (Long, KVEntryReference<CPNode>) -> Unit): IBulkQuery.Value<Unit> {
        return bulkQuery.constant(visitor(key, value))
    }

    override fun visitChanges(oldNode: CPHamtNode?, shift: Int, visitor: CPHamtNode.IChangeVisitor, bulkQuery: IBulkQuery) {
        if (oldNode === this || hash == oldNode?.hash) {
            return
        }
        if (visitor.visitChangesOnly()) {
            if (oldNode != null) {
                oldNode.get(key, shift, bulkQuery).map { oldValue ->
                    if (oldValue != null && value != oldValue) visitor.entryChanged(key, oldValue, value)
                }
            }
        } else {
            var oldValue: KVEntryReference<CPNode>? = null
            val bp = { k: Long, v: KVEntryReference<CPNode> ->
                if (k == key) {
                    oldValue = v
                } else {
                    visitor.entryRemoved(k, v)
                }
            }
            oldNode!!.visitEntries(bulkQuery, bp).onReceive {
                val oldValue = oldValue
                if (oldValue == null) {
                    visitor.entryAdded(key, value)
                } else if (oldValue.getHash() !== value.getHash()) {
                    visitor.entryChanged(key, oldValue, value)
                }
            }
        }
    }

    companion object {
        fun create(key: Long, value: KVEntryReference<CPNode>?): CPHamtLeaf? {
            if (value == null) return null
            return CPHamtLeaf(key, value)
        }
    }
}

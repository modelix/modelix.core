/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.lazy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.modelix.model.bitCount
import org.modelix.model.persistent.CPHamtSingle
import org.modelix.model.persistent.CPNode
import org.modelix.modelql.core.flatMapConcatConcurrent

class CLHamtSingle(private val data: CPHamtSingle, store: IDeserializingKeyValueStore) : CLHamtNode(store) {
    private val mask: Long = maskForLevels(data.numLevels)

    init {
        require(data.numLevels <= MAX_LEVELS) { "Only $MAX_LEVELS levels expected, but was ${data.numLevels}" }
    }

    override fun calculateSize(bulkQuery: IBulkQuery): IBulkQuery.Value<Long> {
        return getChild(bulkQuery).mapBulk { it.calculateSize(bulkQuery) }
    }

    override fun getData(): CPHamtSingle = data

    private fun maskBits(key: Long, shift: Int): Long = (key ushr (MAX_BITS - BITS_PER_LEVEL * data.numLevels - shift)) and mask

    override fun get(key: Long, shift: Int, bulkQuery: IBulkQuery): IBulkQuery.Value<KVEntryReference<CPNode>?> {
        require(shift <= MAX_SHIFT) { "$shift > $MAX_SHIFT" }
        if (maskBits(key, shift) == data.bits) {
            return bulkQuery.get(data.child)
                .mapBulk {
                    val childData = it ?: throw RuntimeException("Entry not found in store: " + data.child.getHash())
                    create(childData, store).get(key, shift + data.numLevels * BITS_PER_LEVEL, bulkQuery)
                }
        } else {
            return bulkQuery.constant(null)
        }
    }

    override fun getLater(key: Long, shift: Int): Flow<KVEntryReference<CPNode>?> {
        require(shift <= MAX_SHIFT) { "$shift > $MAX_SHIFT" }
        if (maskBits(key, shift) == data.bits) {
            return getChildLater()
                .flatMapConcatConcurrent {
                    it.getLater(key, shift + data.numLevels * BITS_PER_LEVEL)
                }
        } else {
            return flowOf(null)
        }
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int): CLHamtNode? {
        require(shift <= MAX_SHIFT) { "$shift > $MAX_SHIFT" }
        if (maskBits(key, shift) == data.bits) {
            return withNewChild(getChild().put(key, value, shift + BITS_PER_LEVEL * data.numLevels))
        } else {
            if (data.numLevels > 1) {
                return splitOneLevel().put(key, value, shift)
//                val nextLevel = CLHamtSingle(CPHamtSingle(data.numLevels - 1, data.bits and maskForLevels(data.numLevels - 1), data.child), store)
//                if (nextLevel.maskBits(key, shift + BITS_PER_LEVEL) == nextLevel.data.bits) {
//                    val newNextLevel = nextLevel.put(key, value, shift + BITS_PER_LEVEL)
//                    if (newNextLevel == null) return null
//                    return CLHamtSingle(CPHamtSingle(1, data.bits ushr (BITS_PER_LEVEL * (data.numLevels - 1)), KVEntryReference(newNextLevel.getData())), store)
//                } else {
//                }
            } else {
                return CLHamtInternal.replace(this).put(key, value, shift)
            }
        }
    }

    fun splitOneLevel(): CLHamtSingle {
        val nextLevel = CLHamtSingle(CPHamtSingle(data.numLevels - 1, data.bits and maskForLevels(data.numLevels - 1), data.child), store)
        return CLHamtSingle(CPHamtSingle(1, data.bits ushr (BITS_PER_LEVEL * (data.numLevels - 1)), KVEntryReference(nextLevel.getData())), store)
    }

    fun withNewChild(newChild: CLHamtNode?): CLHamtSingle? {
        if (newChild is CLHamtSingle) {
            return CLHamtSingle(
                CPHamtSingle(
                    data.numLevels + newChild.data.numLevels,
                    (data.bits shl (newChild.data.numLevels * BITS_PER_LEVEL)) or newChild.data.bits,
                    newChild.data.child
                ),
                store
            )
        }
        return if (newChild == null) {
            null
        } else {
            CLHamtSingle(CPHamtSingle(data.numLevels, data.bits, KVEntryReference(newChild.getData())), store)
        }
    }

    override fun remove(key: Long, shift: Int): CLHamtNode? {
        require(shift <= MAX_SHIFT) { "$shift > $MAX_SHIFT" }
        return put(key, null, shift)
    }

    fun getChild(bulkQuery: IBulkQuery): IBulkQuery.Value<CLHamtNode> {
        return bulkQuery[data.child].map { childData -> create(childData!!, store)!! }
    }

    fun getChildLater(): Flow<CLHamtNode> {
        return IBulkQuery2.requestLater(data.child).map { create(it, store) }
    }

    fun getChild(): CLHamtNode {
        return getChild(NonBulkQuery(store)).execute()
    }

    override fun visitEntries(visitor: (Long, KVEntryReference<CPNode>?) -> Boolean): Boolean {
        return getChild(NonBulkQuery(store)).execute().visitEntries(visitor)
    }

    override fun visitChanges(oldNode: CLHamtNode?, shift: Int, visitor: IChangeVisitor, bulkQuery: IBulkQuery) {
        if (oldNode === this || data.hash == oldNode?.getData()?.hash) {
            return
        }
        if (oldNode is CLHamtSingle && oldNode.data.numLevels == data.numLevels) {
            getChild(bulkQuery).map { child ->
                oldNode.getChild(bulkQuery).map { oldNode ->
                    child.visitChanges(oldNode, shift + data.numLevels * BITS_PER_LEVEL, visitor, bulkQuery)
                }
            }
        } else if (data.numLevels == 1) {
            CLHamtInternal.replace(this).visitChanges(oldNode, shift, visitor, bulkQuery)
        } else {
            splitOneLevel().visitChanges(oldNode, shift, visitor, bulkQuery)
        }
    }

    companion object {
        fun maskForLevels(numLevels: Int) = -1L ushr (MAX_BITS - BITS_PER_LEVEL * numLevels)

        fun replace(node: CLHamtInternal): CLHamtSingle {
            val data = node.getData()
            if (data.children.size != 1) throw RuntimeException("Can only replace nodes with a single child")
            val child = data.children[0].getValue(node.store)
            if (child is CPHamtSingle) {
                return CLHamtSingle(
                    CPHamtSingle(
                        child.numLevels + 1,
                        (indexFromBitmap(data.bitmap).toLong() shl (child.numLevels * BITS_PER_LEVEL)) or child.bits,
                        child.child
                    ),
                    node.store
                )
            }
            return CLHamtSingle(CPHamtSingle(1, indexFromBitmap(data.bitmap).toLong(), data.children[0]), node.store)
        }

        fun replaceIfSingleChild(node: CLHamtInternal): CLHamtNode {
            return if (node.getData().children.size == 1) replace(node) else node
        }

        private fun indexFromBitmap(bitmap: Int): Int = bitCount(bitmap - 1)
    }
}

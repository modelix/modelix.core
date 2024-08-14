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

import org.modelix.model.api.async.IAsyncValue
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.bitCount
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.NonBulkQuery
import org.modelix.model.persistent.SerializationUtil.longToHex

class CPHamtSingle(
    val numLevels: Int,
    val bits: Long,
    val child: KVEntryReference<CPHamtNode>,
) : CPHamtNode() {

    init {
        require(numLevels <= 13) { "$numLevels > 13" }
    }

    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf(child)

    override fun serialize(): String {
        return "S/$numLevels/${longToHex(bits)}/${child.getHash()}"
    }

    private val mask: Long = maskForLevels(numLevels)

    init {
        require(numLevels <= CPHamtNode.MAX_LEVELS) { "Only ${CPHamtNode.MAX_LEVELS} levels expected, but was $numLevels" }
    }

    override fun calculateSize(store: IAsyncObjectStore): IAsyncValue<Long> {
        return getChild(store).thenRequest { it.calculateSize(store) }
    }

    private fun maskBits(key: Long, shift: Int): Long = (key ushr (CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL * numLevels - shift)) and mask

    override fun get(key: Long, shift: Int, store: IAsyncObjectStore): IAsyncValue<KVEntryReference<CPNode>?> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        if (maskBits(key, shift) == bits) {
            return store.get(child)
                .thenRequest {
                    val childData = it ?: throw RuntimeException("Entry not found in store: " + child.getHash())
                    childData.get(key, shift + numLevels * CPHamtNode.BITS_PER_LEVEL, store)
                }
        } else {
            return IAsyncValue.nullConstant()
        }
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        if (maskBits(key, shift) == bits) {
            return withNewChild(getChild(store.getAsyncStore()).awaitBlocking().put(key, value, shift + CPHamtNode.BITS_PER_LEVEL * numLevels, store))
        } else {
            if (numLevels > 1) {
                return splitOneLevel().put(key, value, shift, store)
//                val nextLevel = CPHamtSingle(CPHamtSingle(numLevels - 1, bits and maskForLevels(numLevels - 1), child), store)
//                if (nextLevel.maskBits(key, shift + BITS_PER_LEVEL) == nextLevel.bits) {
//                    val newNextLevel = nextLevel.put(key, value, shift + BITS_PER_LEVEL)
//                    if (newNextLevel == null) return null
//                    return CPHamtSingle(CPHamtSingle(1, bits ushr (BITS_PER_LEVEL * (numLevels - 1)), KVEntryReference(newNextLevel.getData())), store)
//                } else {
//                }
            } else {
                return CPHamtInternal.replace(this).put(key, value, shift, store)
            }
        }
    }

    fun splitOneLevel(): CPHamtSingle {
        val nextLevel = CPHamtSingle(numLevels - 1, bits and maskForLevels(numLevels - 1), child)
        return CPHamtSingle(1, bits ushr (CPHamtNode.BITS_PER_LEVEL * (numLevels - 1)), KVEntryReference(nextLevel))
    }

    fun withNewChild(newChild: CPHamtNode?): CPHamtSingle? {
        if (newChild is CPHamtSingle) {
            return CPHamtSingle(
                numLevels + newChild.numLevels,
                (bits shl (newChild.numLevels * CPHamtNode.BITS_PER_LEVEL)) or newChild.bits,
                newChild.child,
            )
        }
        return if (newChild == null) {
            null
        } else {
            CPHamtSingle(numLevels, bits, KVEntryReference(newChild))
        }
    }

    override fun remove(key: Long, shift: Int, store: IDeserializingKeyValueStore): CPHamtNode? {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        return put(key, null, shift, store)
    }

    fun getChild(store: IAsyncObjectStore): IAsyncValue<CPHamtNode> {
        return store.get(child).map { childData -> checkNotNull(childData) { "Entry not found: $child" } }
    }

    override fun visitEntries(store: IAsyncObjectStore, visitor: (Long, KVEntryReference<CPNode>) -> Unit): IAsyncValue<Unit> {
        return getChild(store).thenRequest { it.visitEntries(store, visitor) }
    }

    override fun visitChanges(oldNode: CPHamtNode?, shift: Int, visitor: CPHamtNode.IChangeVisitor, store: IAsyncObjectStore): IAsyncValue<Unit> {
        return if (oldNode === this || hash == oldNode?.hash) {
            return IAsyncValue.UNIT
        } else if (oldNode is CPHamtSingle && oldNode.numLevels == numLevels) {
            getChild(store).thenRequest { child ->
                oldNode.getChild(store).thenRequest { oldNode ->
                    child.visitChanges(oldNode, shift + numLevels * CPHamtNode.BITS_PER_LEVEL, visitor, store)
                }
            }
        } else if (numLevels == 1) {
            CPHamtInternal.replace(this).visitChanges(oldNode, shift, visitor, store)
        } else {
            splitOneLevel().visitChanges(oldNode, shift, visitor, store)
        }
    }

    companion object {
        fun maskForLevels(numLevels: Int) = -1L ushr (CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL * numLevels)

        fun replace(node: CPHamtInternal, store: IDeserializingKeyValueStore): CPHamtSingle {
            if (node.children.size != 1) throw RuntimeException("Can only replace nodes with a single child")
            val child = node.children[0].getValue(store)
            if (child is CPHamtSingle) {
                return CPHamtSingle(
                    child.numLevels + 1,
                    (indexFromBitmap(node.bitmap).toLong() shl (child.numLevels * CPHamtNode.BITS_PER_LEVEL)) or child.bits,
                    child.child,
                )
            }
            return CPHamtSingle(1, indexFromBitmap(node.bitmap).toLong(), node.children[0])
        }

        fun replaceIfSingleChild(node: CPHamtInternal, store: IDeserializingKeyValueStore): CPHamtNode {
            return if (node.children.size == 1) replace(node, store) else node
        }

        private fun indexFromBitmap(bitmap: Int): Int = bitCount(bitmap - 1)
    }
}

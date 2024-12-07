package org.modelix.model.persistent

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.observableOfEmpty
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.flatMapMaybe
import com.badoo.reaktive.single.flatMapObservable
import com.badoo.reaktive.single.flatten
import com.badoo.reaktive.single.map
import com.badoo.reaktive.single.toSingle
import com.badoo.reaktive.single.zipWith
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.bitCount
import org.modelix.model.lazy.KVEntryReference
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

    private fun maskBits(key: Long, shift: Int): Long = (key ushr (CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL * numLevels - shift)) and mask

    override fun get(key: Long, shift: Int, store: IAsyncObjectStore): Maybe<KVEntryReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        if (maskBits(key, shift) == bits) {
            return child.getValue(store).flatMapMaybe {
                it.get(key, shift + numLevels * CPHamtNode.BITS_PER_LEVEL, store)
            }
        } else {
            return maybeOfEmpty()
        }
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        if (maskBits(key, shift) == bits) {
            return getChild(store).flatMapMaybe { it.put(key, value, shift + CPHamtNode.BITS_PER_LEVEL * numLevels, store) }.map { withNewChild(it) }
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

    fun withNewChild(newChild: CPHamtNode): CPHamtSingle {
        return if (newChild is CPHamtSingle) {
            CPHamtSingle(
                numLevels + newChild.numLevels,
                (bits shl (newChild.numLevels * CPHamtNode.BITS_PER_LEVEL)) or newChild.bits,
                newChild.child,
            )
        } else {
            CPHamtSingle(numLevels, bits, KVEntryReference(newChild))
        }
    }

    override fun remove(key: Long, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        return put(key, null, shift, store)
    }

    fun getChild(store: IAsyncObjectStore): Single<CPHamtNode> {
        return child.getValue(store)
    }

    override fun getEntries(store: IAsyncObjectStore): Observable<Pair<Long, KVEntryReference<CPNode>>> {
        return getChild(store).flatMapObservable { it.getEntries(store) }
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IAsyncObjectStore, changesOnly: Boolean): Observable<MapChangeEvent> {
        return if (oldNode === this || hash == oldNode?.hash) {
            observableOfEmpty()
        } else if (oldNode is CPHamtSingle && oldNode.numLevels == numLevels) {
            getChild(store).zipWith(oldNode.getChild(store)) { child, oldNode ->
                child.getChanges(oldNode, shift + numLevels * CPHamtNode.BITS_PER_LEVEL, store, changesOnly)
            }.flatten()
        } else if (numLevels == 1) {
            CPHamtInternal.replace(this).getChanges(oldNode, shift, store, changesOnly)
        } else {
            splitOneLevel().getChanges(oldNode, shift, store, changesOnly)
        }
    }

    companion object {
        fun maskForLevels(numLevels: Int) = -1L ushr (CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL * numLevels)

        fun replace(node: CPHamtInternal, store: IAsyncObjectStore): Single<CPHamtSingle> {
            if (node.children.size != 1) throw RuntimeException("Can only replace nodes with a single child")
            return node.children[0].getValue(store).map { child ->
                if (child is CPHamtSingle) {
                    CPHamtSingle(
                        child.numLevels + 1,
                        (indexFromBitmap(node.bitmap).toLong() shl (child.numLevels * CPHamtNode.BITS_PER_LEVEL)) or child.bits,
                        child.child,
                    )
                } else {
                    CPHamtSingle(1, indexFromBitmap(node.bitmap).toLong(), node.children[0])
                }
            }
        }

        fun replaceIfSingleChild(node: CPHamtInternal, store: IAsyncObjectStore): Single<CPHamtNode> {
            return if (node.children.size == 1) replace(node, store) else node.toSingle()
        }

        private fun indexFromBitmap(bitmap: Int): Int = bitCount(bitmap - 1)
    }
}

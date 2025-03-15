package org.modelix.model.persistent

import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.bitCount
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.plus

/**
 * Replacement for a chain of CPHamtInternals with a single child.
 * Helps to reduce the depth of the tree and therefor the number of requests necessary to access an entry.
 */
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

    override fun get(key: Long, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<KVEntryReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        if (maskBits(key, shift) == bits) {
            return child.getValue(store).flatMapZeroOrOne {
                it.get(key, shift + numLevels * CPHamtNode.BITS_PER_LEVEL, store)
            }
        } else {
            return IStream.empty()
        }
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
        store: IAsyncObjectStore,
    ): IStream.Many<Pair<Long, KVEntryReference<CPNode>?>> {
        if (keys.any { maskBits(it, shift) == bits }) {
            return child.getValue(store).flatMap {
                it.getAll(keys, shift + numLevels * BITS_PER_LEVEL, store)
            }
        } else {
            return IStream.empty()
        }
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        return putAll(listOf(key to value), shift, store)
    }

    override fun putAll(
        entries: List<Pair<Long, KVEntryReference<CPNode>?>>,
        shift: Int,
        store: IAsyncObjectStore,
    ): IStream.ZeroOrOne<CPHamtNode> {
        if (entries.all { maskBits(it.first, shift) == bits }) {
            return getChild(store)
                .flatMapZeroOrOne { it.putAll(entries, shift + BITS_PER_LEVEL * numLevels, store) }
                .map { withNewChild(it) }
        } else {
            if (numLevels > 1) {
                return splitOneLevel().putAll(entries, shift, store)
            } else {
                return CPHamtInternal.replace(this).putAll(entries, shift, store)
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

    override fun remove(key: Long, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        return put(key, null, shift, store)
    }

    fun getChild(store: IAsyncObjectStore): IStream.One<CPHamtNode> {
        return child.getValue(store)
    }

    override fun getEntries(store: IAsyncObjectStore): IStream.Many<Pair<Long, KVEntryReference<CPNode>>> {
        return getChild(store).flatMap { it.getEntries(store) }
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IAsyncObjectStore, changesOnly: Boolean): IStream.Many<MapChangeEvent> {
        return if (oldNode === this || hash == oldNode?.hash) {
            IStream.empty()
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

    fun logicalIndexOfChild(relativeLevel: Int): Int {
        return ((bits ushr (MAX_BITS - BITS_PER_LEVEL * relativeLevel)) and LEVEL_MASK).toInt()
    }

    override fun objectDiff(oldObject: IKVValue?, shift: Int, store: IAsyncObjectStore): IStream.Many<IKVValue> {
        return when (oldObject) {
            is CPHamtSingle -> {
                if (oldObject.hash == this.hash) {
                    IStream.empty()
                } else {
                    if (oldObject.numLevels == this.numLevels && oldObject.mask == this.mask) {
                        val childDiff = this.child.getValue(store)
                            .zipWith(oldObject.child.getValue(store)) { newChild, oldChild ->
                                newChild.objectDiff(oldChild, shift + numLevels * BITS_PER_LEVEL, store)
                            }.flatten()
                        IStream.of(this).plus(childDiff)
                    } else {
                        var oldChild: IStream.ZeroOrOne<CPHamtNode> = IStream.of(oldObject)
                        repeat(numLevels) { relativeLevel ->
                            oldChild.map { oldChild ->
                                when (oldChild) {
                                    is CPHamtSingle -> CPHamtInternal.replace(oldChild)
                                    is CPHamtInternal -> oldChild
                                    is CPHamtLeaf -> null
                                    else -> null
                                }?.getChild(logicalIndexOfChild(relativeLevel), store)
                                    ?: IStream.empty()
                            }
                        }
                        val childDiff = child.getValue(store).zipWith(oldChild.orNull()) { n, o ->
                            if (o == null) IStream.empty() else n.objectDiff(o, shift + BITS_PER_LEVEL + numLevels, store)
                        }.flatten()
                        IStream.of(this).plus(childDiff)
                    }
                }
            }
            is CPHamtInternal -> {
                // TODO using CPHamtInternal.replace may result in some of these replacements being returned,
                //      which isn't totally wrong, but inefficient.
                IStream.of(this).plus(CPHamtInternal.replace(this).diffChildren(oldObject, shift, store))
            }
            is CPHamtLeaf -> {
                getAllObjects(store).filter { it.hash != oldObject.hash }
            }
            else -> getAllObjects(store)
        }
    }

    companion object {
        fun maskForLevels(numLevels: Int) = -1L ushr (CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL * numLevels)

        fun replace(node: CPHamtInternal, store: IAsyncObjectStore): IStream.One<CPHamtSingle> {
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

        fun replaceIfSingleChild(node: CPHamtInternal, store: IAsyncObjectStore): IStream.One<CPHamtNode> {
            return if (node.children.size == 1) replace(node, store) else IStream.of(node)
        }

        private fun indexFromBitmap(bitmap: Int): Int = bitCount(bitmap - 1)
    }
}

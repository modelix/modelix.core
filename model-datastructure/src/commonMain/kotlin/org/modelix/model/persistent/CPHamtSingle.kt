package org.modelix.model.persistent

import org.modelix.model.bitCount
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectLoader
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.customDiff
import org.modelix.model.objects.getDescendantRefs
import org.modelix.model.objects.getDescendantsAndSelf
import org.modelix.model.objects.requireDifferentHash
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.plus

/**
 * Replacement for a chain of CPHamtInternals with a single child.
 * Helps to reduce the depth of the tree and therefor the number of requests necessary to access an entry.
 */
data class CPHamtSingle(
    val numLevels: Int,
    val bits: Long,
    val child: ObjectReference<CPHamtNode>,
) : CPHamtNode() {

    init {
        require(numLevels <= 13) { "$numLevels > 13" }
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> = listOf(child)

    override fun serialize(): String {
        return "S/$numLevels/${longToHex(bits)}/${child.getHash()}"
    }

    private val mask: Long = maskForLevels(numLevels)

    init {
        require(numLevels <= CPHamtNode.MAX_LEVELS) { "Only ${CPHamtNode.MAX_LEVELS} levels expected, but was $numLevels" }
    }

    private fun maskBits(key: Long, shift: Int): Long = (key ushr (CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL * numLevels - shift)) and mask

    override fun get(key: Long, shift: Int, store: IObjectLoader): IStream.ZeroOrOne<ObjectReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        if (maskBits(key, shift) == bits) {
            return child.requestData(store).flatMapZeroOrOne {
                it.get(key, shift + numLevels * CPHamtNode.BITS_PER_LEVEL, store)
            }
        } else {
            return IStream.empty()
        }
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
        loader: IObjectLoader,
    ): IStream.Many<Pair<Long, ObjectReference<CPNode>?>> {
        if (keys.any { maskBits(it, shift) == bits }) {
            return child.requestData(loader).flatMap {
                it.getAll(keys, shift + numLevels * BITS_PER_LEVEL, loader)
            }
        } else {
            return IStream.empty()
        }
    }

    override fun put(key: Long, value: ObjectReference<CPNode>?, shift: Int, store: IObjectLoader): IStream.ZeroOrOne<CPHamtNode> {
        return putAll(listOf(key to value), shift, store)
    }

    override fun putAll(
        entries: List<Pair<Long, ObjectReference<CPNode>?>>,
        shift: Int,
        store: IObjectLoader,
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
        return CPHamtSingle(1, bits ushr (CPHamtNode.BITS_PER_LEVEL * (numLevels - 1)), ObjectReference(nextLevel))
    }

    fun withNewChild(newChild: CPHamtNode): CPHamtSingle {
        return if (newChild is CPHamtSingle) {
            CPHamtSingle(
                numLevels + newChild.numLevels,
                (bits shl (newChild.numLevels * CPHamtNode.BITS_PER_LEVEL)) or newChild.bits,
                newChild.child,
            )
        } else {
            CPHamtSingle(numLevels, bits, ObjectReference(newChild))
        }
    }

    override fun remove(key: Long, shift: Int, store: IObjectLoader): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        return put(key, null, shift, store)
    }

    fun getChild(store: IObjectLoader): IStream.One<CPHamtNode> {
        return child.requestData(store)
    }

    override fun getEntries(store: IObjectLoader): IStream.Many<Pair<Long, ObjectReference<CPNode>>> {
        return getChild(store).flatMap { it.getEntries(store) }
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IObjectLoader, changesOnly: Boolean): IStream.Many<MapChangeEvent> {
        requireDifferentHash(oldNode)
        return if (oldNode === this) {
            IStream.empty()
        } else if (oldNode is CPHamtSingle && oldNode.numLevels == numLevels) {
            if (child.getHash() == oldNode.child.getHash()) {
                IStream.empty()
            } else {
                getChild(store).zipWith(oldNode.getChild(store)) { child, oldNode ->
                    child.getChanges(oldNode, shift + numLevels * BITS_PER_LEVEL, store, changesOnly)
                }.flatten()
            }
        } else if (numLevels == 1) {
            CPHamtInternal.replace(this).getChanges(oldNode, shift, store, changesOnly)
        } else {
            splitOneLevel().getChanges(oldNode, shift, store, changesOnly)
        }
    }

    fun logicalIndexOfChild(relativeLevel: Int): Int {
        return ((bits ushr (MAX_BITS - BITS_PER_LEVEL * relativeLevel)) and LEVEL_MASK).toInt()
    }

    override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
        shift: Int,
        loader: IObjectLoader,
    ): IStream.Many<Object<*>> {
        if (oldObject == null) {
            return self.getDescendantsAndSelf(loader)
        }
        val (oldObject, oldRef) = oldObject
        return when (oldObject) {
            is CPHamtSingle -> {
                requireDifferentHash(oldObject)
                if (oldObject.numLevels == this.numLevels && oldObject.mask == this.mask) {
                    if (this.child.getHash() == oldObject.child.getHash()) {
                        IStream.of(self)
                    } else {
                        IStream.of(self) + this.child.customDiff(oldObject.child, loader) { newChild, oldChild ->
                            newChild.data.objectDiff(newChild, oldChild, shift + numLevels * BITS_PER_LEVEL, loader)
                        }
                    }
                } else {
                    var oldChildRef: IStream.ZeroOrOne<ObjectReference<CPHamtNode>> = IStream.of(oldObject.child)
                    repeat(numLevels - 1) { iteration ->
                        val relativeLevel = iteration + 1
                        oldChildRef = oldChildRef.flatMapZeroOrOne { it.resolve(loader) }.flatMapZeroOrOne { oldChild ->
                            when (oldChild.data) {
                                is CPHamtSingle -> CPHamtInternal.replace(oldChild.data)
                                is CPHamtInternal -> oldChild.data
                                is CPHamtLeaf -> null
                                else -> null
                            }?.getChildRef(logicalIndexOfChild(relativeLevel))
                                ?.let { IStream.of(it) }
                                ?: IStream.empty()
                        }
                    }
                    val childDiff = oldChildRef.orNull().flatMap { oldChildRef ->
                        if (oldChildRef == null) {
                            child.resolve(loader).flatMap { it.getDescendantsAndSelf(loader) }
                        } else {
                            child.customDiff(oldChildRef, loader) { n, o ->
                                n.data.objectDiff(n, o, shift + BITS_PER_LEVEL + numLevels, loader)
                            }
                        }
                    }
                    IStream.of(self) + childDiff
                }
            }
            is CPHamtInternal -> {
                // TODO using CPHamtInternal.replace may result in some of these replacements being returned,
                //      which isn't totally wrong, but inefficient.
                IStream.of(self) + CPHamtInternal.replace(this).diffChildren(oldObject, shift, loader)
            }
            is CPHamtLeaf -> {
                IStream.of(self) +
                    getDescendantRefs(loader)
                        .filter { it.getHash() != oldRef.getHash() }
                        .flatMap { it.resolve(loader) }
            }
            else -> self.getDescendantsAndSelf(loader)
        }
    }

    companion object {
        fun maskForLevels(numLevels: Int) = -1L ushr (CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL * numLevels)

        fun replace(node: CPHamtInternal, loader: IObjectLoader): IStream.One<CPHamtSingle> {
            if (node.children.size != 1) throw RuntimeException("Can only replace nodes with a single child")
            return node.children[0].requestData(loader).map { child ->
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

        fun replaceIfSingleChild(node: CPHamtInternal, store: IObjectLoader): IStream.One<CPHamtNode> {
            return if (node.children.size == 1) replace(node, store) else IStream.of(node)
        }

        private fun indexFromBitmap(bitmap: Int): Int = bitCount(bitmap - 1)
    }
}

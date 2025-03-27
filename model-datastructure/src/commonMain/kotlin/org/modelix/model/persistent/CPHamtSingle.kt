package org.modelix.model.persistent

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.customDiff
import org.modelix.datastructures.objects.getDescendantRefs
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.datastructures.objects.requireDifferentHash
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.bitCount
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

    override fun get(key: Long, shift: Int): IStream.ZeroOrOne<ObjectReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        if (maskBits(key, shift) == bits) {
            return child.resolveData().flatMapZeroOrOne {
                it.get(key, shift + numLevels * CPHamtNode.BITS_PER_LEVEL)
            }
        } else {
            return IStream.empty()
        }
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
    ): IStream.Many<Pair<Long, ObjectReference<CPNode>?>> {
        if (keys.any { maskBits(it, shift) == bits }) {
            return child.resolveData().flatMap {
                it.getAll(keys, shift + numLevels * BITS_PER_LEVEL)
            }
        } else {
            return IStream.empty()
        }
    }

    override fun put(key: Long, value: ObjectReference<CPNode>?, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        return putAll(listOf(key to value), shift, graph)
    }

    override fun putAll(
        entries: List<Pair<Long, ObjectReference<CPNode>?>>,
        shift: Int,
        graph: IObjectGraph,
    ): IStream.ZeroOrOne<CPHamtNode> {
        if (entries.all { maskBits(it.first, shift) == bits }) {
            return getChild()
                .flatMapZeroOrOne { it.putAll(entries, shift + BITS_PER_LEVEL * numLevels, graph) }
                .map { withNewChild(it, graph) }
        } else {
            if (numLevels > 1) {
                return splitOneLevel(graph).putAll(entries, shift, graph)
            } else {
                return CPHamtInternal.replace(this, graph).putAll(entries, shift, graph)
            }
        }
    }

    fun splitOneLevel(graph: IObjectGraph): CPHamtSingle {
        val nextLevel = CPHamtSingle(numLevels - 1, bits and maskForLevels(numLevels - 1), child)
        return CPHamtSingle(1, bits ushr (BITS_PER_LEVEL * (numLevels - 1)), graph(nextLevel))
    }

    fun withNewChild(newChild: CPHamtNode, graph: IObjectGraph): CPHamtSingle {
        return if (newChild is CPHamtSingle) {
            CPHamtSingle(
                numLevels + newChild.numLevels,
                (bits shl (newChild.numLevels * BITS_PER_LEVEL)) or newChild.bits,
                newChild.child,
            )
        } else {
            CPHamtSingle(numLevels, bits, graph(newChild))
        }
    }

    override fun remove(key: Long, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= MAX_SHIFT) { "$shift > ${MAX_SHIFT}" }
        return put(key, null, shift, graph)
    }

    fun getChild(): IStream.One<CPHamtNode> {
        return child.resolveData()
    }

    override fun getEntries(): IStream.Many<Pair<Long, ObjectReference<CPNode>>> {
        return getChild().flatMap { it.getEntries() }
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, changesOnly: Boolean): IStream.Many<MapChangeEvent> {
        requireDifferentHash(oldNode)
        return if (oldNode === this) {
            IStream.empty()
        } else if (oldNode is CPHamtSingle && oldNode.numLevels == numLevels) {
            if (child.getHash() == oldNode.child.getHash()) {
                IStream.empty()
            } else {
                getChild().zipWith(oldNode.getChild()) { child, oldNode ->
                    child.getChanges(oldNode, shift + numLevels * BITS_PER_LEVEL, changesOnly)
                }.flatten()
            }
        } else if (numLevels == 1) {
            @OptIn(DelicateModelixApi::class) // free floating objects are not returned
            CPHamtInternal.replace(this, IObjectGraph.FREE_FLOATING).getChanges(oldNode, shift, changesOnly)
        } else {
            @OptIn(DelicateModelixApi::class) // free floating objects are not returned
            splitOneLevel(IObjectGraph.FREE_FLOATING).getChanges(oldNode, shift, changesOnly)
        }
    }

    fun logicalIndexOfChild(relativeLevel: Int): Int {
        return ((bits ushr (MAX_BITS - BITS_PER_LEVEL * relativeLevel)) and LEVEL_MASK).toInt()
    }

    override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
        shift: Int,
    ): IStream.Many<Object<*>> {
        if (oldObject == null) {
            return self.getDescendantsAndSelf()
        }
        val (oldObject, oldRef) = oldObject
        return when (oldObject) {
            is CPHamtSingle -> {
                requireDifferentHash(oldObject)
                if (oldObject.numLevels == this.numLevels && oldObject.mask == this.mask) {
                    if (this.child.getHash() == oldObject.child.getHash()) {
                        IStream.of(self)
                    } else {
                        IStream.of(self) + this.child.customDiff(oldObject.child) { newChild, oldChild ->
                            newChild.data.objectDiff(newChild, oldChild, shift + numLevels * BITS_PER_LEVEL)
                        }
                    }
                } else {
                    var oldChildRef: IStream.ZeroOrOne<ObjectReference<CPHamtNode>> = IStream.of(oldObject.child)
                    repeat(numLevels - 1) { iteration ->
                        val relativeLevel = iteration + 1
                        oldChildRef = oldChildRef.flatMapZeroOrOne { it.resolve() }.flatMapZeroOrOne { oldChild ->
                            val oldData = oldChild.data
                            when (oldData) {
                                is CPHamtSingle -> {
                                    // oldChildRef is checked below to ensure this doesn't leak into the result
                                    @OptIn(DelicateModelixApi::class)
                                    CPHamtInternal.replace(oldData, IObjectGraph.FREE_FLOATING)
                                }
                                is CPHamtInternal -> oldData
                                is CPHamtLeaf -> null
                            }?.getChildRef(logicalIndexOfChild(relativeLevel))
                                ?.let { IStream.of(it) }
                                ?: IStream.empty()
                        }
                    }
                    val childDiff = oldChildRef.orNull().flatMap { oldChildRef ->
                        @OptIn(DelicateModelixApi::class) // just used for checking, can't be part of the result
                        check(oldChildRef?.graph != IObjectGraph.FREE_FLOATING)
                        if (oldChildRef == null) {
                            child.resolve().flatMap { it.getDescendantsAndSelf() }
                        } else {
                            child.customDiff(oldChildRef) { n, o ->
                                n.data.objectDiff(n, o, shift + BITS_PER_LEVEL + numLevels)
                            }
                        }
                    }
                    IStream.of(self) + childDiff
                }
            }
            is CPHamtInternal -> {
                @OptIn(DelicateModelixApi::class) // free floating objects are filtered out
                IStream.of(self) + CPHamtInternal.replace(this, IObjectGraph.FREE_FLOATING)
                    .diffChildren(oldObject, shift).filter { it.graph != IObjectGraph.FREE_FLOATING }
            }
            is CPHamtLeaf -> {
                IStream.of(self) +
                    getDescendantRefs()
                        .filter { it.getHash() != oldRef.getHash() }
                        .flatMap { it.resolve() }
            }
            else -> self.getDescendantsAndSelf()
        }
    }

    companion object {
        fun maskForLevels(numLevels: Int) = -1L ushr (CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL * numLevels)

        fun replace(node: CPHamtInternal): IStream.One<CPHamtSingle> {
            if (node.children.size != 1) throw RuntimeException("Can only replace nodes with a single child")
            return node.children[0].resolveData().map { child ->
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

        fun replaceIfSingleChild(node: CPHamtInternal): IStream.One<CPHamtNode> {
            return if (node.children.size == 1) replace(node) else IStream.of(node)
        }

        private fun indexFromBitmap(bitmap: Int): Int = bitCount(bitmap - 1)
    }
}

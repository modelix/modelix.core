package org.modelix.datastructures.hamt

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.customDiff
import org.modelix.datastructures.objects.getDescendantRefs
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.datastructures.objects.requireDifferentHash
import org.modelix.datastructures.objects.upcast
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.plus

/**
 * Replacement for a chain of CPHamtInternals with a single child.
 * Helps to reduce the depth of the tree and therefor the number of requests necessary to access an entry.
 */
data class HamtSingleChildNode<K, V : Any>(
    override val config: Config<K, V>,
    val numLevels: Int,
    val bits: Long,
    val child: ObjectReference<HamtNode<K, V>>,
) : HamtNode<K, V>() {

    init {
        require(numLevels <= 13) { "$numLevels > 13" }
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> = listOf(child)

    override fun serialize(): String {
        return "S/$numLevels/${longToHex(bits)}/${child.getHash()}"
    }

    private val mask: Long = maskForLevels(numLevels)

    init {
        require(numLevels <= MAX_LEVELS) { "Only $MAX_LEVELS levels expected, but was $numLevels" }
    }

    private fun maskBits(hash: Long, shift: Int): Long = (hash ushr (MAX_BITS - BITS_PER_LEVEL * numLevels - shift)) and mask

    override fun get(key: K, shift: Int): IStream.ZeroOrOne<V> {
        require(shift <= MAX_SHIFT) { "$shift > $MAX_SHIFT" }
        if (maskBits(config.keyConfig.hashCode64(key), shift) == bits) {
            return child.resolveData().flatMapZeroOrOne {
                it.get(key, shift + numLevels * BITS_PER_LEVEL)
            }
        } else {
            return IStream.empty()
        }
    }

    override fun getAll(
        keys: Iterable<K>,
        shift: Int,
    ): IStream.Many<Pair<K, V?>> {
        if (keys.any { maskBits(config.keyConfig.hashCode64(it), shift) == bits }) {
            return child.resolveData().flatMap {
                it.getAll(keys, shift + numLevels * BITS_PER_LEVEL)
            }
        } else {
            return IStream.empty()
        }
    }

    override fun put(key: K, value: V, shift: Int, graph: IObjectGraph): IStream.One<HamtNode<K, V>> {
        return putAll(listOf(key to value), shift, graph)
    }

    override fun putAll(
        entries: List<Pair<K, V>>,
        shift: Int,
        graph: IObjectGraph,
    ): IStream.One<HamtNode<K, V>> {
        // TODO handle collisions
        if (entries.all { maskBits(config.keyConfig.hashCode64(it.first), shift) == bits }) {
            return getChild()
                .flatMapOne { it.putAll(entries, shift + BITS_PER_LEVEL * numLevels, graph) }
                .map { withNewChild(it, graph) }
        } else {
            if (numLevels > 1) {
                return splitOneLevel(graph).putAll(entries, shift, graph)
            } else {
                return HamtInternalNode.replace(this, graph).putAll(entries, shift, graph)
            }
        }
    }

    fun splitOneLevel(graph: IObjectGraph): HamtSingleChildNode<K, V> {
        val nextLevel = HamtSingleChildNode(config, numLevels - 1, bits and maskForLevels(numLevels - 1), child)
        return HamtSingleChildNode(config, 1, bits ushr (BITS_PER_LEVEL * (numLevels - 1)), graph(nextLevel))
    }

    fun withNewChild(newChild: HamtNode<K, V>, graph: IObjectGraph): HamtSingleChildNode<K, V> {
        return if (newChild is HamtSingleChildNode) {
            HamtSingleChildNode(
                config,
                numLevels + newChild.numLevels,
                (bits shl (newChild.numLevels * BITS_PER_LEVEL)) or newChild.bits,
                newChild.child,
            )
        } else {
            HamtSingleChildNode(config, numLevels, bits, graph(newChild))
        }
    }

    override fun remove(key: K, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
        require(shift <= MAX_SHIFT) { "$shift > ${MAX_SHIFT}" }
        return getChild()
            .flatMapZeroOrOne { it.remove(key, shift + BITS_PER_LEVEL * numLevels, graph) }
            .map { withNewChild(it, graph) }
    }

    fun getChild(): IStream.One<HamtNode<K, V>> {
        return child.resolveData()
    }

    override fun getEntries(): IStream.Many<Pair<K, V>> {
        return getChild().flatMap { it.getEntries() }
    }

    override fun getChanges(oldNode: HamtNode<K, V>?, shift: Int, changesOnly: Boolean): IStream.Many<MapChangeEvent<K, V>> {
        requireDifferentHash(oldNode)
        return if (oldNode === this) {
            IStream.empty()
        } else if (oldNode is HamtSingleChildNode && oldNode.numLevels == numLevels) {
            if (child.getHash() == oldNode.child.getHash()) {
                IStream.empty()
            } else {
                getChild().zipWith(oldNode.getChild()) { child, oldNode ->
                    child.getChanges(oldNode, shift + numLevels * BITS_PER_LEVEL, changesOnly)
                }.flatten()
            }
        } else if (numLevels == 1) {
            @OptIn(DelicateModelixApi::class) // free floating objects are not returned
            HamtInternalNode.replace(this, IObjectGraph.FREE_FLOATING).getChanges(oldNode, shift, changesOnly)
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
            is HamtSingleChildNode<*, *> -> {
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
                    var oldChildRef: IStream.ZeroOrOne<ObjectReference<HamtNode<K, V>>> =
                        IStream.of(oldObject.child.upcast())
                    repeat(numLevels - 1) { iteration ->
                        val relativeLevel = iteration + 1
                        oldChildRef = oldChildRef.flatMapZeroOrOne { it.resolve() }.flatMapZeroOrOne { oldChild ->
                            val oldData = oldChild.data
                            when (oldData) {
                                is HamtSingleChildNode -> {
                                    // oldChildRef is checked below to ensure this doesn't leak into the result
                                    @OptIn(DelicateModelixApi::class)
                                    HamtInternalNode.replace(oldData, IObjectGraph.FREE_FLOATING)
                                }
                                is HamtInternalNode -> oldData
                                is HamtLeafNode, is HamtCollisionNode -> null
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
            is HamtInternalNode<*, *> -> {
                @OptIn(DelicateModelixApi::class) // free floating objects are filtered out
                IStream.of(self) + HamtInternalNode.replace(this, IObjectGraph.FREE_FLOATING)
                    .diffChildren(oldObject, shift).filter { it.graph != IObjectGraph.FREE_FLOATING }
            }
            is HamtLeafNode<*, *> -> {
                IStream.of(self) +
                    getDescendantRefs()
                        .filter { it.getHash() != oldRef.getHash() }
                        .flatMap { it.resolve() }
            }
            else -> self.getDescendantsAndSelf()
        }
    }

    companion object {
        fun maskForLevels(numLevels: Int) = -1L ushr (MAX_BITS - BITS_PER_LEVEL * numLevels)

        fun <K, V : Any> replace(node: HamtInternalNode<K, V>): IStream.One<HamtSingleChildNode<K, V>> {
            if (node.children.size != 1) throw RuntimeException("Can only replace nodes with a single child")
            return node.children[0].resolveData().map { child ->
                if (child is HamtSingleChildNode) {
                    HamtSingleChildNode(
                        node.config,
                        child.numLevels + 1,
                        (indexFromBitmap(node.bitmap).toLong() shl (child.numLevels * BITS_PER_LEVEL)) or child.bits,
                        child.child,
                    )
                } else {
                    HamtSingleChildNode(node.config, 1, indexFromBitmap(node.bitmap).toLong(), node.children[0])
                }
            }
        }

        fun <K, V : Any> replaceIfSingleChild(node: HamtInternalNode<K, V>): IStream.One<HamtNode<K, V>> {
            return if (node.children.size == 1) replace(node) else IStream.of(node)
        }

        private fun indexFromBitmap(bitmap: Int): Int = bitCount(bitmap - 1)
    }
}

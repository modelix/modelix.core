package org.modelix.datastructures.hamt

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.customDiff
import org.modelix.datastructures.objects.getDescendantRefs
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.datastructures.objects.getHashString
import org.modelix.datastructures.objects.requestBoth
import org.modelix.datastructures.objects.requireDifferentHash
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.plus

class HamtInternalNode<K, V : Any>(
    override val config: Config<K, V>,
    val bitmap: Int,
    val children: Array<out ObjectReference<HamtNode<K, V>>>,
) : HamtNode<K, V>() {

    val data get() = this

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> = children.asList()

    override fun serialize(): String {
        return "I" +
            SEPARATOR +
            intToHex(bitmap) +
            SEPARATOR +
            (if (children.isEmpty()) "" else children.joinToString(SEPARATOR2) { it.getHashString() })
    }

    companion object {
        fun <K, V : Any> createEmpty(config: Config<K, V>) = create(config, 0, arrayOf<ObjectReference<HamtNode<K, V>>>())

        fun <K, V : Any> create(config: Config<K, V>, bitmap: Int, childHashes: Array<out ObjectReference<HamtNode<K, V>>>): HamtInternalNode<K, V> {
            return HamtInternalNode(config, bitmap, childHashes)
        }

        fun <K, V : Any> create(config: Config<K, V>, key: K, childHash: V, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
            return createEmpty<K, V>(config).put(key, childHash, shift, graph)
        }

        fun <K, V : Any> replace(single: HamtSingleChildNode<K, V>, graph: IObjectGraph): HamtInternalNode<K, V> {
            if (single.numLevels != 1) return replace(single.splitOneLevel(graph), graph)
            val data: HamtSingleChildNode<K, V> = single
            val logicalIndex: Int = data.bits.toInt()
            return create(single.config, 1 shl logicalIndex, arrayOf(data.child))
        }
    }

    override fun put(key: K, value: V?, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
        require(shift <= MAX_SHIFT) { "$shift > $MAX_SHIFT" }
        val childIndex = indexFromHash(config.keyConfig.hashCode64(key), shift)
        return getChild(childIndex).orNull().flatMapZeroOrOne { child ->
            if (child == null) {
                setChild(childIndex, HamtLeafNode.create(config, key, value), shift, graph)
            } else {
                child.put(key, value, shift + BITS_PER_LEVEL, graph).orNull().flatMapZeroOrOne {
                    setChild(childIndex, it, shift, graph)
                }
            }
        }
    }

    override fun putAll(entries: List<Pair<K, V?>>, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
        val groups = entries.groupBy { indexFromHash(config.keyConfig.hashCode64(it.first), shift) }
        val logicalIndices = groups.keys.toIntArray()
        val newChildrenLists = groups.values.toList()
        return getChildren(logicalIndices).flatMap { children: List<HamtNode<K, V>?> ->
            IStream.many(children.withIndex()).flatMap { (i, oldChild) ->
                val newChildren = newChildrenLists[i]
                if (oldChild == null) {
                    val nonNullChildren = newChildren.filter { it.second != null }
                    when (nonNullChildren.size) {
                        0 -> IStream.of(null)
                        1 -> {
                            val singleChild = nonNullChildren.single()
                            IStream.of(HamtLeafNode.create(config, singleChild.first, singleChild.second))
                        }
                        else -> {
                            createEmpty(config).putAll(nonNullChildren, shift + BITS_PER_LEVEL, graph).orNull()
                        }
                    }
                } else {
                    oldChild.putAll(newChildren, shift + BITS_PER_LEVEL, graph).orNull()
                }
            }
        }.toList().flatMapZeroOrOne { updatedChildren ->
            setChildren(
                logicalIndices,
                updatedChildren.map { it?.let { graph(it) } },
                shift,
            )
        }
    }

    override fun remove(key: K, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
        require(shift <= MAX_SHIFT) { "$shift > $MAX_SHIFT" }
        val childIndex = indexFromHash(config.keyConfig.hashCode64(key), shift)
        return getChild(childIndex).orNull().flatMapZeroOrOne { child ->
            if (child == null) {
                IStream.of(this)
            } else {
                child.remove(key, shift + BITS_PER_LEVEL, graph).orNull().flatMapZeroOrOne {
                    setChild(childIndex, it, shift, graph)
                }
            }
        }
    }

    override fun get(key: K, shift: Int): IStream.ZeroOrOne<V> {
        require(shift <= MAX_SHIFT) { "$shift > $MAX_SHIFT" }
        val childIndex = indexFromHash(config.keyConfig.hashCode64(key), shift)
        return getChild(childIndex).flatMapZeroOrOne { child ->
            child.get(key, shift + BITS_PER_LEVEL)
        }
    }

    override fun getAll(
        keys: Iterable<K>,
        shift: Int,
    ): IStream.Many<Pair<K, V?>> {
        val groups = keys.groupBy { indexFromHash(config.keyConfig.hashCode64(it), shift) }
        return IStream.many(groups.entries).flatMap { group ->
            getChild(group.key).flatMap { child ->
                child.getAll(group.value, shift + BITS_PER_LEVEL)
            }
        }
    }

    fun getChild(logicalIndex: Int): IStream.ZeroOrOne<HamtNode<K, V>> {
        val childHash = getChildRef(logicalIndex) ?: return IStream.empty()
        return getChild(childHash)
    }

    fun getChildRef(logicalIndex: Int): ObjectReference<HamtNode<K, V>>? {
        if (isBitNotSet(data.bitmap, logicalIndex)) {
            return null
        }
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        return data.children[physicalIndex]
    }

    private fun getChildren(logicalIndices: IntArray): IStream.One<List<HamtNode<K, V>?>> {
        val childHashes = logicalIndices.map { logicalIndex ->
            if (isBitNotSet(data.bitmap, logicalIndex)) {
                null
            } else {
                val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
                data.children[physicalIndex]
            }
        }
        return IStream.many(childHashes).flatMap { it?.resolveData() ?: IStream.of(null) }.toList()
    }

    protected fun getChild(childHash: ObjectReference<HamtNode<K, V>>): IStream.One<HamtNode<K, V>> {
        return childHash.resolveData()
    }

    fun setChildren(logicalIndices: IntArray, children: List<ObjectReference<HamtNode<K, V>>?>, shift: Int): IStream.ZeroOrOne<HamtNode<K, V>> {
        var oldBitmap = data.bitmap
        var newBitmap = data.bitmap
        val oldChildren = data.children
        var newChildren = data.children
        for (i in logicalIndices.indices) {
            val logicalIndex = logicalIndices[i]
            val newChild = children[i]
            val oldChild = if (isBitNotSet(oldBitmap, logicalIndex)) {
                null
            } else {
                oldChildren[logicalToPhysicalIndex(oldBitmap, logicalIndex)]
            }
            if (newChild == null) {
                if (oldChild == null) {
                    // nothing changed
                } else {
                    newChildren = COWArrays.removeAt(newChildren, logicalToPhysicalIndex(newBitmap, logicalIndex))
                    newBitmap = newBitmap and (1 shl logicalIndex).inv() // clear bit
                }
            } else {
                if (oldChild == null) {
                    newChildren = COWArrays.insert(newChildren, logicalToPhysicalIndex(newBitmap, logicalIndex), newChild)
                    newBitmap = newBitmap or (1 shl logicalIndex) // set bit
                } else {
                    newChildren = COWArrays.set(newChildren, logicalToPhysicalIndex(newBitmap, logicalIndex), newChild)
                }
            }
        }

        val newNode = create(config, newBitmap, newChildren)
        return if (shift < MAX_BITS - BITS_PER_LEVEL) {
            HamtSingleChildNode.replaceIfSingleChild(newNode)
        } else {
            IStream.of(newNode)
        }
    }

    fun setChild(logicalIndex: Int, child: HamtNode<K, V>?, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
        if (child == null) {
            return deleteChild(logicalIndex)
        }
        val childHash = graph(child)
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        val newNode = if (isBitNotSet(data.bitmap, logicalIndex)) {
            create(
                config,
                data.bitmap or (1 shl logicalIndex),
                COWArrays.insert(data.children, physicalIndex, childHash),
            )
        } else {
            create(
                config,
                data.bitmap,
                COWArrays.set(data.children, physicalIndex, childHash),
            )
        }
        return if (shift < MAX_BITS - BITS_PER_LEVEL) {
            HamtSingleChildNode.replaceIfSingleChild(newNode)
        } else {
            IStream.of(newNode)
        }
    }

    fun deleteChild(logicalIndex: Int): IStream.ZeroOrOne<HamtNode<K, V>> {
        if (isBitNotSet(data.bitmap, logicalIndex)) {
            return IStream.of(this)
        }
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        val newBitmap = data.bitmap and (1 shl logicalIndex).inv()
        if (newBitmap == 0) {
            return IStream.empty()
        }
        val newChildren = COWArrays.removeAt(data.children, physicalIndex)
        return if (newChildren.size == 1) {
            getChild(newChildren[0]).map { child0 ->
                if (child0 is HamtLeafNode) {
                    child0
                } else {
                    create(config, newBitmap, newChildren)
                }
            }
        } else {
            IStream.of(create(config, newBitmap, newChildren))
        }
    }

    override fun getEntries(): IStream.Many<Pair<K, V>> {
        return IStream.many(data.children).flatMap { it.resolveData() }.flatMap { it.getEntries() }
    }

    override fun getChanges(oldNode: HamtNode<K, V>?, shift: Int, changesOnly: Boolean): IStream.Many<MapChangeEvent<K, V>> {
        if (oldNode === this) {
            return IStream.empty()
        }
        requireDifferentHash(oldNode)
        return when (oldNode) {
            is HamtInternalNode -> {
                val oldInternalNode: HamtInternalNode<K, V> = oldNode
                if (data.bitmap == oldInternalNode.data.bitmap) {
                    IStream.many(data.children.indices).flatMap { i ->
                        val oldChildRef = oldInternalNode.data.children[i]
                        val newChildRef = data.children[i]
                        if (oldChildRef.getHash() != newChildRef.getHash()) {
                            getChild(newChildRef).zipWith(oldInternalNode.getChild(oldChildRef)) { child, oldChild ->
                                child.getChanges(oldChild, shift + BITS_PER_LEVEL, changesOnly)
                            }.flatten()
                        } else {
                            IStream.empty<MapChangeEvent<K, V>>()
                        }
                    }
                } else {
                    IStream.many(0 until ENTRIES_PER_LEVEL).flatMap { logicalIndex ->
                        val newChildRef = getChildRef(logicalIndex)
                        val oldChildRef = oldInternalNode.getChildRef(logicalIndex)
                        if (newChildRef == null) {
                            if (oldChildRef == null) {
                                // no change
                                IStream.empty<MapChangeEvent<K, V>>()
                            } else {
                                if (!changesOnly) {
                                    oldChildRef.resolve().flatMap { oldChild ->
                                        oldChild.data.getEntries().map { (key, value) ->
                                            EntryRemovedEvent<K, V>(key, value)
                                        }
                                    }
                                } else {
                                    IStream.empty<MapChangeEvent<K, V>>()
                                }
                            }
                        } else {
                            if (oldChildRef == null) {
                                if (!changesOnly) {
                                    newChildRef.resolveData().flatMap { newChild ->
                                        newChild.getEntries().map { (key, value) ->
                                            EntryAddedEvent<K, V>(key, value)
                                        }
                                    }
                                } else {
                                    IStream.empty<MapChangeEvent<K, V>>()
                                }
                            } else {
                                if (newChildRef.getHash() == oldChildRef.getHash()) {
                                    IStream.empty()
                                } else {
                                    newChildRef.requestBoth(oldChildRef) { newChild, oldChild ->
                                        newChild.data.getChanges(oldChild.data, shift + BITS_PER_LEVEL, changesOnly)
                                    }.flatten()
                                }
                            }
                        }
                    }
                }
            }
            is HamtLeafNode -> {
                if (changesOnly) {
                    get(oldNode.key, shift).filter { it != oldNode.value }.map { newValue ->
                        EntryChangedEvent(oldNode.key, oldNode.value, newValue)
                    }
                } else {
                    val entries = getEntries()
                    val newEntry = get(oldNode.key, shift)
                    val changeOrRemoveEvent = newEntry.orNull().flatMapZeroOrOne { newValue ->
                        if (newValue == null) {
                            IStream.of(EntryRemovedEvent(oldNode.key, oldNode.value))
                        } else if (newValue != oldNode.value) {
                            IStream.of(EntryChangedEvent(oldNode.key, oldNode.value, newValue))
                        } else {
                            IStream.empty()
                        }
                    }
                    val entryAddedEvents = entries.filter { it.first != oldNode.key }.map { EntryAddedEvent(it.first, it.second) }
                    IStream.of(changeOrRemoveEvent, entryAddedEvents).flatten()
                }
            }
            is HamtSingleChildNode -> {
                @OptIn(DelicateModelixApi::class) // free floating objects are not returned
                getChanges(replace(oldNode, IObjectGraph.FREE_FLOATING), shift, changesOnly)
            }
            else -> {
                throw RuntimeException("Unknown type: " + oldNode!!::class.simpleName)
            }
        }
    }

    override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
        shift: Int,
    ): IStream.Many<Object<*>> {
        val oldData = oldObject?.data
        return when (oldData) {
            is HamtInternalNode<*, *> -> {
                requireDifferentHash(oldObject)
                IStream.of(self) + diffChildren(oldData, shift)
            }
            is HamtSingleChildNode<*, *> -> {
                @OptIn(DelicateModelixApi::class) // free floating objects are filtered out
                IStream.of(self) + diffChildren(replace(oldData, IObjectGraph.FREE_FLOATING), shift)
                    .filter { it.graph != IObjectGraph.FREE_FLOATING }
            }
            is HamtLeafNode<*, *> -> {
                IStream.of(self) +
                    getDescendantRefs()
                        .filter { it.getHash() != oldObject.ref.getHash() }
                        .flatMap { it.resolve() }
            }
            else -> self.getDescendantsAndSelf()
        }
    }

    fun diffChildren(oldObject: HamtInternalNode<*, *>, shift: Int): IStream.Many<Object<*>> {
        val changedChildren = (0 until ENTRIES_PER_LEVEL)
            .mapNotNull { logicalIndex ->
                (getChildRef(logicalIndex) ?: return@mapNotNull null) to oldObject.getChildRef(logicalIndex)
            }
            .filter { it.first.getHash() != it.second?.getHash() }
        return IStream.many(changedChildren).flatMap {
            val newChild = it.first
            val oldChild = it.second
            if (oldChild == null) {
                newChild.resolve().flatMap { it.getDescendantsAndSelf() }
            } else {
                newChild.customDiff(oldChild) { n, o ->
                    n.data.objectDiff(n, o, shift + BITS_PER_LEVEL)
                }
            }
        }
    }

    private fun isBitNotSet(bitmap: Int, logicalIndex: Int): Boolean {
        return bitmap and (1 shl logicalIndex) == 0
    }

    private fun logicalToPhysicalIndex(bitmap: Int, logicalIndex: Int): Int {
        return bitCount(bitmap and (1 shl logicalIndex) - 1)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HamtInternalNode<*, *>

        if (bitmap != other.bitmap) return false
        if (config != other.config) return false
        if (!children.contentEquals(other.children)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bitmap
        result = 31 * result + config.hashCode()
        result = 31 * result + children.contentHashCode()
        return result
    }
}

package org.modelix.model.persistent

import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.bitCount
import org.modelix.model.lazy.COWArrays
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectGraph
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.customDiff
import org.modelix.model.objects.getDescendantRefs
import org.modelix.model.objects.getDescendantsAndSelf
import org.modelix.model.objects.getHashString
import org.modelix.model.objects.requestBoth
import org.modelix.model.objects.requireDifferentHash
import org.modelix.model.persistent.SerializationUtil.intToHex
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.plus

class CPHamtInternal(
    val bitmap: Int,
    val children: Array<ObjectReference<CPHamtNode>>,
) : CPHamtNode() {
    val data get() = this

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> = children.asList()

    override fun serialize(): String {
        return "I" +
            Separators.LEVEL1 +
            intToHex(bitmap) +
            Separators.LEVEL1 +
            (if (children.isEmpty()) "" else children.joinToString(Separators.LEVEL2) { it.getHashString() })
    }

    companion object {
        fun createEmpty() = create(0, arrayOf())

        fun create(bitmap: Int, childHashes: Array<ObjectReference<CPHamtNode>>): CPHamtInternal {
            return CPHamtInternal(bitmap, childHashes)
        }

        fun create(key: Long, childHash: ObjectReference<CPNode>, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
            return createEmpty().put(key, childHash, shift, graph)
        }

        fun replace(single: CPHamtSingle, graph: IObjectGraph): CPHamtInternal {
            if (single.numLevels != 1) return replace(single.splitOneLevel(graph), graph)
            val data: CPHamtSingle = single
            val logicalIndex: Int = data.bits.toInt()
            return create(1 shl logicalIndex, arrayOf(data.child))
        }
    }

    override fun put(key: Long, value: ObjectReference<CPNode>?, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex).orNull().flatMapZeroOrOne { child ->
            if (child == null) {
                setChild(childIndex, CPHamtLeaf.create(key, value), shift, graph)
            } else {
                child.put(key, value, shift + CPHamtNode.BITS_PER_LEVEL, graph).orNull().flatMapZeroOrOne {
                    setChild(childIndex, it, shift, graph)
                }
            }
        }
    }

    override fun putAll(entries: List<Pair<Long, ObjectReference<CPNode>?>>, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        val groups = entries.groupBy { indexFromKey(it.first, shift) }
        val logicalIndices = groups.keys.toIntArray()
        val newChildrenLists = groups.values.toList()
        return getChildren(logicalIndices).flatMap { children: List<CPHamtNode?> ->
            IStream.many(children.withIndex()).flatMap { (i, oldChild) ->
                val newChildren = newChildrenLists[i]
                if (oldChild == null) {
                    val nonNullChildren = newChildren.filter { it.second != null }
                    when (nonNullChildren.size) {
                        0 -> IStream.of(null)
                        1 -> {
                            val singleChild = nonNullChildren.single()
                            IStream.of(CPHamtLeaf.create(singleChild.first, singleChild.second))
                        }
                        else -> {
                            createEmpty().putAll(nonNullChildren, shift + BITS_PER_LEVEL, graph).orNull()
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

    override fun remove(key: Long, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex).orNull().flatMapZeroOrOne { child ->
            if (child == null) {
                IStream.of(this)
            } else {
                child.remove(key, shift + CPHamtNode.BITS_PER_LEVEL, graph).orNull().flatMapZeroOrOne {
                    setChild(childIndex, it, shift, graph)
                }
            }
        }
    }

    override fun get(key: Long, shift: Int): IStream.ZeroOrOne<ObjectReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex).flatMapZeroOrOne { child ->
            child.get(key, shift + BITS_PER_LEVEL)
        }
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
    ): IStream.Many<Pair<Long, ObjectReference<CPNode>?>> {
        val groups = keys.groupBy { indexFromKey(it, shift) }
        return IStream.many(groups.entries).flatMap { group ->
            getChild(group.key).flatMap { child ->
                child.getAll(group.value.toLongArray(), shift + BITS_PER_LEVEL)
            }
        }
    }

    fun getChild(logicalIndex: Int): IStream.ZeroOrOne<CPHamtNode> {
        val childHash = getChildRef(logicalIndex) ?: return IStream.empty()
        return getChild(childHash)
    }

    fun getChildRef(logicalIndex: Int): ObjectReference<CPHamtNode>? {
        if (isBitNotSet(data.bitmap, logicalIndex)) {
            return null
        }
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        return data.children[physicalIndex]
    }

    private fun getChildren(logicalIndices: IntArray): IStream.One<List<CPHamtNode?>> {
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

    protected fun getChild(childHash: ObjectReference<CPHamtNode>): IStream.One<CPHamtNode> {
        return childHash.resolveData()
    }

    fun setChildren(logicalIndices: IntArray, children: List<ObjectReference<CPHamtNode>?>, shift: Int): IStream.ZeroOrOne<CPHamtNode> {
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

        val newNode = create(newBitmap, newChildren)
        return if (shift < MAX_BITS - BITS_PER_LEVEL) {
            CPHamtSingle.replaceIfSingleChild(newNode)
        } else {
            IStream.of(newNode)
        }
    }

    fun setChild(logicalIndex: Int, child: CPHamtNode?, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        if (child == null) {
            return deleteChild(logicalIndex)
        }
        val childHash = graph(child)
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        val newNode = if (isBitNotSet(data.bitmap, logicalIndex)) {
            create(
                data.bitmap or (1 shl logicalIndex),
                COWArrays.insert(data.children, physicalIndex, childHash),
            )
        } else {
            create(
                data.bitmap,
                COWArrays.set(data.children, physicalIndex, childHash),
            )
        }
        return if (shift < MAX_BITS - BITS_PER_LEVEL) {
            CPHamtSingle.replaceIfSingleChild(newNode)
        } else {
            IStream.of(newNode)
        }
    }

    fun deleteChild(logicalIndex: Int): IStream.ZeroOrOne<CPHamtNode> {
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
                if (child0 is CPHamtLeaf) {
                    child0
                } else {
                    create(newBitmap, newChildren)
                }
            }
        } else {
            IStream.of(create(newBitmap, newChildren))
        }
    }

    override fun getEntries(): IStream.Many<Pair<Long, ObjectReference<CPNode>>> {
        return IStream.many(data.children).flatMap { it.resolveData() }.flatMap { it.getEntries() }
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, changesOnly: Boolean): IStream.Many<MapChangeEvent> {
        if (oldNode === this) {
            return IStream.empty()
        }
        requireDifferentHash(oldNode)
        return when (oldNode) {
            is CPHamtInternal -> {
                val oldInternalNode: CPHamtInternal = oldNode
                if (data.bitmap == oldInternalNode.data.bitmap) {
                    IStream.many(data.children.indices).flatMap { i ->
                        val oldChildRef = oldInternalNode.data.children[i]
                        val newChildRef = data.children[i]
                        if (oldChildRef.getHash() != newChildRef.getHash()) {
                            getChild(newChildRef).zipWith(oldInternalNode.getChild(oldChildRef)) { child, oldChild ->
                                child.getChanges(oldChild, shift + BITS_PER_LEVEL, changesOnly)
                            }.flatten()
                        } else {
                            IStream.empty<MapChangeEvent>()
                        }
                    }
                } else {
                    IStream.many(0 until ENTRIES_PER_LEVEL).flatMap { logicalIndex ->
                        val newChildRef = getChildRef(logicalIndex)
                        val oldChildRef = oldInternalNode.getChildRef(logicalIndex)
                        if (newChildRef == null) {
                            if (oldChildRef == null) {
                                // no change
                                IStream.empty<MapChangeEvent>()
                            } else {
                                if (!changesOnly) {
                                    oldChildRef.resolve().flatMap { oldChild ->
                                        oldChild.data.getEntries().map { (key, value) ->
                                            EntryRemovedEvent(key, value)
                                        }
                                    }
                                } else {
                                    IStream.empty<MapChangeEvent>()
                                }
                            }
                        } else {
                            if (oldChildRef == null) {
                                if (!changesOnly) {
                                    newChildRef.resolveData().flatMap { newChild ->
                                        newChild.getEntries().map { (key, value) ->
                                            EntryAddedEvent(key, value)
                                        }
                                    }
                                } else {
                                    IStream.empty<MapChangeEvent>()
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
            is CPHamtLeaf -> {
                if (changesOnly) {
                    get(oldNode.key, shift).filter { it.getHash() != oldNode.value.getHash() }.map { newValue ->
                        EntryChangedEvent(oldNode.key, oldNode.value, newValue)
                    }
                } else {
                    val entries = getEntries()
                    val newEntry = get(oldNode.key, shift)
                    val changeOrRemoveEvent = newEntry.orNull().flatMapZeroOrOne { newValue ->
                        if (newValue == null) {
                            IStream.of(EntryRemovedEvent(oldNode.key, oldNode.value))
                        } else if (newValue.getHash() != oldNode.value.getHash()) {
                            IStream.of(EntryChangedEvent(oldNode.key, oldNode.value, newValue))
                        } else {
                            IStream.empty()
                        }
                    }
                    val entryAddedEvents = entries.filter { it.first != oldNode.key }.map { EntryAddedEvent(it.first, it.second) }
                    IStream.of(changeOrRemoveEvent, entryAddedEvents).flatten()
                }
            }
            is CPHamtSingle -> {
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
        return when (oldObject?.data) {
            is CPHamtInternal -> {
                requireDifferentHash(oldObject)
                IStream.of(self) + diffChildren(oldObject.data, shift)
            }
            is CPHamtSingle -> {
                @OptIn(DelicateModelixApi::class) // free floating objects are filtered out
                IStream.of(self) + diffChildren(replace(oldObject.data, IObjectGraph.FREE_FLOATING), shift)
                    .filter { it.graph != IObjectGraph.FREE_FLOATING }
            }
            is CPHamtLeaf -> {
                IStream.of(self) +
                    getDescendantRefs()
                        .filter { it.getHash() != oldObject.ref.getHash() }
                        .flatMap { it.resolve() }
            }
            else -> self.getDescendantsAndSelf()
        }
    }

    fun diffChildren(oldObject: CPHamtInternal, shift: Int): IStream.Many<Object<*>> {
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
}

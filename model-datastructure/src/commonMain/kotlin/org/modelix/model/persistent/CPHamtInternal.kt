package org.modelix.model.persistent

import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.bitCount
import org.modelix.model.lazy.COWArrays
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.SerializationUtil.intToHex
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.plus

class CPHamtInternal(
    val bitmap: Int,
    val children: Array<KVEntryReference<CPHamtNode>>,
) : CPHamtNode() {
    val data get() = this

    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = children.asList()

    override fun serialize(): String {
        return "I" +
            Separators.LEVEL1 +
            intToHex(bitmap) +
            Separators.LEVEL1 +
            (if (children.isEmpty()) "" else children.joinToString(Separators.LEVEL2) { it.getHash() })
    }

    companion object {
        fun createEmpty() = create(0, arrayOf())

        fun create(bitmap: Int, childHashes: Array<KVEntryReference<CPHamtNode>>): CPHamtInternal {
            return CPHamtInternal(bitmap, childHashes)
        }

        fun create(key: Long, childHash: KVEntryReference<CPNode>, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
            return createEmpty().put(key, childHash, shift, store)
        }

        fun replace(single: CPHamtSingle): CPHamtInternal {
            if (single.numLevels != 1) return replace(single.splitOneLevel())
            val data: CPHamtSingle = single
            val logicalIndex: Int = data.bits.toInt()
            return create(1 shl logicalIndex, arrayOf(data.child))
        }
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex, store).orNull().flatMapZeroOrOne { child ->
            if (child == null) {
                setChild(childIndex, CPHamtLeaf.create(key, value), shift, store)
            } else {
                child.put(key, value, shift + CPHamtNode.BITS_PER_LEVEL, store).orNull().flatMapZeroOrOne {
                    setChild(childIndex, it, shift, store)
                }
            }
        }
    }

    override fun putAll(entries: List<Pair<Long, KVEntryReference<CPNode>?>>, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        val groups = entries.groupBy { indexFromKey(it.first, shift) }
        val logicalIndices = groups.keys.toIntArray()
        val newChildrenLists = groups.values.toList()
        return getChildren(logicalIndices, store).flatMap { children: List<CPHamtNode?> ->
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
                            createEmpty().putAll(nonNullChildren, shift + BITS_PER_LEVEL, store).orNull()
                        }
                    }
                } else {
                    oldChild.putAll(newChildren, shift + BITS_PER_LEVEL, store).orNull()
                }
            }
        }.toList().flatMapZeroOrOne { updatedChildren ->
            setChildren(
                logicalIndices,
                updatedChildren.map { it?.let { KVEntryReference(it) } },
                shift,
                store,
            )
        }
    }

    override fun remove(key: Long, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex, store).orNull().flatMapZeroOrOne { child ->
            if (child == null) {
                IStream.of(this)
            } else {
                child.remove(key, shift + CPHamtNode.BITS_PER_LEVEL, store).orNull().flatMapZeroOrOne {
                    setChild(childIndex, it, shift, store)
                }
            }
        }
    }

    override fun get(key: Long, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<KVEntryReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex, store).flatMapZeroOrOne { child ->
            child.get(key, shift + CPHamtNode.BITS_PER_LEVEL, store)
        }
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
        store: IAsyncObjectStore,
    ): IStream.Many<Pair<Long, KVEntryReference<CPNode>?>> {
        val groups = keys.groupBy { indexFromKey(it, shift) }
        return IStream.many(groups.entries).flatMap { group ->
            getChild(group.key, store).flatMap { child ->
                child.getAll(group.value.toLongArray(), shift + BITS_PER_LEVEL, store)
            }
        }
    }

    fun getChild(logicalIndex: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        val childHash = getChildHash(logicalIndex) ?: return IStream.empty()
        return getChild(childHash, store)
    }

    private fun getChildHash(logicalIndex: Int): KVEntryReference<CPHamtNode>? {
        if (isBitNotSet(data.bitmap, logicalIndex)) {
            return null
        }
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        return data.children[physicalIndex]
    }

    private fun getChildren(logicalIndices: IntArray, store: IAsyncObjectStore): IStream.One<List<CPHamtNode?>> {
        val childHashes = logicalIndices.map { logicalIndex ->
            if (isBitNotSet(data.bitmap, logicalIndex)) {
                null
            } else {
                val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
                data.children[physicalIndex]
            }
        }
        return IStream.many(childHashes).flatMap { it?.getValue(store) ?: IStream.of(null) }.toList()
    }

    protected fun getChild(childHash: KVEntryReference<CPHamtNode>, store: IAsyncObjectStore): IStream.One<CPHamtNode> {
        return childHash.getValue(store)
    }

    fun setChildren(logicalIndices: IntArray, children: List<KVEntryReference<CPHamtNode>?>, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
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
            CPHamtSingle.replaceIfSingleChild(newNode, store)
        } else {
            IStream.of(newNode)
        }
    }

    fun setChild(logicalIndex: Int, child: CPHamtNode?, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        if (child == null) {
            return deleteChild(logicalIndex, store)
        }
        val childHash = KVEntryReference(child)
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
        return if (shift < CPHamtNode.MAX_BITS - CPHamtNode.BITS_PER_LEVEL) {
            CPHamtSingle.replaceIfSingleChild(newNode, store)
        } else {
            IStream.of(newNode)
        }
    }

    fun deleteChild(logicalIndex: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
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
            getChild(newChildren[0], store).map { child0 ->
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

    override fun getEntries(store: IAsyncObjectStore): IStream.Many<Pair<Long, KVEntryReference<CPNode>>> {
        return IStream.many(data.children).flatMap { it.getValue(store) }.flatMap { it.getEntries(store) }
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IAsyncObjectStore, changesOnly: Boolean): IStream.Many<MapChangeEvent> {
        if (oldNode === this || data.hash == oldNode?.hash) {
            return IStream.empty()
        }
        return when (oldNode) {
            is CPHamtInternal -> {
                val oldInternalNode: CPHamtInternal = oldNode
                if (data.bitmap == oldInternalNode.data.bitmap) {
                    IStream.many(data.children.indices).flatMap { i ->
                        val oldChildHash = oldInternalNode.data.children[i]
                        val newChildHash = data.children[i]
                        if (oldChildHash != newChildHash) {
                            getChild(newChildHash, store).zipWith(oldInternalNode.getChild(oldChildHash, store)) { child, oldChild ->
                                child.getChanges(oldChild, shift + CPHamtNode.BITS_PER_LEVEL, store, changesOnly)
                            }.flatten()
                        } else {
                            IStream.empty<MapChangeEvent>()
                        }
                    }
                } else {
                    IStream.many(0 until CPHamtNode.ENTRIES_PER_LEVEL).flatMap { logicalIndex ->
                        getChild(logicalIndex, store).orNull().zipWith(oldInternalNode.getChild(logicalIndex, store).orNull()) { child, oldChild ->
                            if (child == null) {
                                if (oldChild == null) {
                                    // no change
                                    IStream.empty<MapChangeEvent>()
                                } else {
                                    if (!changesOnly) {
                                        oldChild.getEntries(store).map { (key, value) ->
                                            EntryRemovedEvent(key, value)
                                        }
                                    } else {
                                        IStream.empty<MapChangeEvent>()
                                    }
                                }
                            } else {
                                if (oldChild == null) {
                                    if (!changesOnly) {
                                        child.getEntries(store).map { (key, value) ->
                                            EntryAddedEvent(key, value)
                                        }
                                    } else {
                                        IStream.empty<MapChangeEvent>()
                                    }
                                } else {
                                    child.getChanges(oldChild, shift + CPHamtNode.BITS_PER_LEVEL, store, changesOnly)
                                }
                            }
                        }.flatten()
                    }
                }
            }
            is CPHamtLeaf -> {
                if (changesOnly) {
                    get(oldNode.key, shift, store).filter { it != oldNode.value }.map { newValue ->
                        EntryChangedEvent(oldNode.key, oldNode.value, newValue)
                    }
                } else {
                    val entries = getEntries(store)
                    val newEntry = get(oldNode.key, shift, store)
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
            is CPHamtSingle -> {
                getChanges(replace(oldNode), shift, store, changesOnly)
            }
            else -> {
                throw RuntimeException("Unknown type: " + oldNode!!::class.simpleName)
            }
        }
    }

    override fun objectDiff(oldObject: IKVValue?, shift: Int, store: IAsyncObjectStore): IStream.Many<IKVValue> {
        return when (oldObject) {
            is CPHamtInternal -> {
                if (oldObject.hash == this.hash) return IStream.empty()
                IStream.of(this) + diffChildren(oldObject, shift, store)
            }
            is CPHamtSingle -> {
                IStream.of(this) + diffChildren(replace(oldObject), shift, store)
            }
            is CPHamtLeaf -> {
                getAllObjects(store).filter { it.hash != oldObject.hash }
            }
            else -> getAllObjects(store)
        }
    }

    fun diffChildren(oldObject: CPHamtInternal, shift: Int, store: IAsyncObjectStore): IStream.Many<IKVValue> {
        val changedChildren = (0 until ENTRIES_PER_LEVEL)
            .mapNotNull { logicalIndex ->
                (getChildHash(logicalIndex) ?: return@mapNotNull null) to oldObject.getChildHash(logicalIndex)
            }
            .filter { it.first.getHash() != it.second?.getHash() }
        return IStream.many(changedChildren).flatMap {
            val newChild = it.first
            val oldChild = it.second
            if (oldChild == null) {
                newChild.getValue(store).flatMap { it.getAllObjects(store) }
            } else {
                newChild.getValue(store).zipWith(oldChild.getValue(store)) { n, o ->
                    n.objectDiff(o, shift + BITS_PER_LEVEL, store)
                }.flatten()
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

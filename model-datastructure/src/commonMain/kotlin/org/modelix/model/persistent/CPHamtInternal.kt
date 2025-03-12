package org.modelix.model.persistent

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.asObservable
import com.badoo.reaktive.maybe.filter
import com.badoo.reaktive.maybe.flatMap
import com.badoo.reaktive.maybe.flatMapObservable
import com.badoo.reaktive.maybe.map
import com.badoo.reaktive.maybe.maybeOf
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.maybe.toMaybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.filter
import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.flatMapSingle
import com.badoo.reaktive.observable.flatten
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.observableOfEmpty
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asMaybe
import com.badoo.reaktive.single.flatMapMaybe
import com.badoo.reaktive.single.flatMapObservable
import com.badoo.reaktive.single.flatten
import com.badoo.reaktive.single.singleOf
import com.badoo.reaktive.single.toSingle
import com.badoo.reaktive.single.zipWith
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.bitCount
import org.modelix.model.lazy.COWArrays
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.SerializationUtil.intToHex
import org.modelix.streams.asObservable
import org.modelix.streams.getSynchronous
import org.modelix.streams.orNull

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

        fun create(key: Long, childHash: KVEntryReference<CPNode>, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
            return createEmpty().put(key, childHash, shift, store)
        }

        fun replace(single: CPHamtSingle): CPHamtInternal {
            if (single.numLevels != 1) throw RuntimeException("Can only replace single level nodes")
            val data: CPHamtSingle = single
            val logicalIndex: Int = data.bits.toInt()
            return create(1 shl logicalIndex, arrayOf(data.child))
        }
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex, store).orNull().flatMapMaybe { child ->
            if (child == null) {
                setChild(childIndex, CPHamtLeaf.create(key, value), shift, store)
            } else {
                child.put(key, value, shift + CPHamtNode.BITS_PER_LEVEL, store).orNull().flatMapMaybe {
                    setChild(childIndex, it, shift, store)
                }
            }
        }
    }

    override fun putAll(entries: List<Pair<Long, KVEntryReference<CPNode>?>>, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        val groups = entries.groupBy { indexFromKey(it.first, shift) }
        val logicalIndices = groups.keys.toIntArray()
        val newChildrenLists = groups.values.toList()
        return getChildren(logicalIndices, store).flatMapObservable { children: List<CPHamtNode?> ->
            children.withIndex().asObservable().flatMapSingle { (i, oldChild) ->
                val newChildren = newChildrenLists[i]
                if (oldChild == null) {
                    val nonNullChildren = newChildren.filter { it.second != null }
                    when (nonNullChildren.size) {
                        0 -> null.toSingle()
                        1 -> {
                            val singleChild = nonNullChildren.single()
                            CPHamtLeaf.create(singleChild.first, singleChild.second).toSingle()
                        }
                        else -> {
                            createEmpty().putAll(nonNullChildren, shift + BITS_PER_LEVEL, store).orNull()
                        }
                    }
                } else {
                    oldChild.putAll(newChildren, shift + BITS_PER_LEVEL, store).orNull()
                }
            }
        }.toList().flatMapMaybe { updatedChildren ->
            setChildren(
                logicalIndices,
                updatedChildren.map { it?.let { KVEntryReference(it) } },
                shift,
                store,
            )
        }
    }

    override fun remove(key: Long, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex, store).orNull().flatMapMaybe { child ->
            if (child == null) {
                this.toMaybe()
            } else {
                child.remove(key, shift + CPHamtNode.BITS_PER_LEVEL, store).orNull().flatMapMaybe {
                    setChild(childIndex, it, shift, store)
                }
            }
        }
    }

    override fun get(key: Long, shift: Int, store: IAsyncObjectStore): Maybe<KVEntryReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT) { "$shift > ${CPHamtNode.MAX_SHIFT}" }
        val childIndex = CPHamtNode.indexFromKey(key, shift)
        return getChild(childIndex, store).flatMap { child ->
            child.get(key, shift + CPHamtNode.BITS_PER_LEVEL, store)
        }
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
        store: IAsyncObjectStore,
    ): Observable<Pair<Long, KVEntryReference<CPNode>?>> {
        val groups = keys.groupBy { indexFromKey(it, shift) }
        return groups.entries.asObservable().flatMap { group ->
            getChild(group.key, store).flatMapObservable { child ->
                child.getAll(group.value.toLongArray(), shift + BITS_PER_LEVEL, store)
            }
        }
    }

    protected fun getChild(logicalIndex: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        if (isBitNotSet(data.bitmap, logicalIndex)) {
            return maybeOfEmpty()
        }
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        require(physicalIndex < data.children.size) { "Invalid physical index ($physicalIndex). N. children: ${data.children.size}. Logical index: $logicalIndex" }
        val childHash = data.children[physicalIndex]
        return getChild(childHash, store).asMaybe()
    }

    private fun getChildren(logicalIndices: IntArray, store: IAsyncObjectStore): Single<List<CPHamtNode?>> {
        val childHashes = logicalIndices.map { logicalIndex ->
            if (isBitNotSet(data.bitmap, logicalIndex)) {
                null
            } else {
                val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
                data.children[physicalIndex]
            }
        }
        return childHashes.asObservable().flatMapSingle { it?.getValue(store) ?: singleOf(null) }.toList()
    }

    protected fun getChild(childHash: KVEntryReference<CPHamtNode>, store: IAsyncObjectStore): Single<CPHamtNode> {
        return childHash.getValue(store)
    }

    fun setChildren(logicalIndices: IntArray, children: List<KVEntryReference<CPHamtNode>?>, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
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
            CPHamtSingle.replaceIfSingleChild(newNode, store).asMaybe()
        } else {
            newNode.toMaybe()
        }
    }

    fun setChild(logicalIndex: Int, child: CPHamtNode?, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
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
            CPHamtSingle.replaceIfSingleChild(newNode, store).asMaybe()
        } else {
            newNode.toMaybe()
        }
    }

    fun deleteChild(logicalIndex: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        if (isBitNotSet(data.bitmap, logicalIndex)) {
            return this.toMaybe()
        }
        val physicalIndex = logicalToPhysicalIndex(data.bitmap, logicalIndex)
        val newBitmap = data.bitmap and (1 shl logicalIndex).inv()
        if (newBitmap == 0) {
            return maybeOfEmpty()
        }
        val newChildren = COWArrays.removeAt(data.children, physicalIndex)
        if (newChildren.size == 1) {
            val child0 = getChild(newChildren[0], store).getSynchronous()
            if (child0 is CPHamtLeaf) {
                return child0.toMaybe()
            }
        }
        return create(newBitmap, newChildren).toMaybe()
    }

    override fun getEntries(store: IAsyncObjectStore): Observable<Pair<Long, KVEntryReference<CPNode>>> {
        return data.children.asObservable().flatMapSingle { it.getValue(store) }.flatMap { it.getEntries(store) }
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IAsyncObjectStore, changesOnly: Boolean): Observable<MapChangeEvent> {
        if (oldNode === this || data.hash == oldNode?.hash) {
            return observableOfEmpty()
        }
        return when (oldNode) {
            is CPHamtInternal -> {
                val oldInternalNode: CPHamtInternal = oldNode
                if (data.bitmap == oldInternalNode.data.bitmap) {
                    data.children.indices.asObservable().flatMap { i ->
                        val oldChildHash = oldInternalNode.data.children[i]
                        val newChildHash = data.children[i]
                        if (oldChildHash != newChildHash) {
                            getChild(newChildHash, store).zipWith(oldInternalNode.getChild(oldChildHash, store)) { child, oldChild ->
                                child.getChanges(oldChild, shift + CPHamtNode.BITS_PER_LEVEL, store, changesOnly)
                            }.flatten()
                        } else {
                            observableOfEmpty<MapChangeEvent>()
                        }
                    }
                } else {
                    (0 until CPHamtNode.ENTRIES_PER_LEVEL).asObservable().flatMap<Int, MapChangeEvent> { logicalIndex ->
                        getChild(logicalIndex, store).orNull().zipWith(oldInternalNode.getChild(logicalIndex, store).orNull()) { child, oldChild ->
                            if (child == null) {
                                if (oldChild == null) {
                                    // no change
                                    observableOfEmpty<MapChangeEvent>()
                                } else {
                                    if (!changesOnly) {
                                        oldChild.getEntries(store).map { (key, value) ->
                                            EntryRemovedEvent(key, value)
                                        }
                                    } else {
                                        observableOfEmpty<MapChangeEvent>()
                                    }
                                }
                            } else {
                                if (oldChild == null) {
                                    if (!changesOnly) {
                                        child.getEntries(store).map { (key, value) ->
                                            EntryAddedEvent(key, value)
                                        }
                                    } else {
                                        observableOfEmpty<MapChangeEvent>()
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
                    }.asObservable()
                } else {
                    val entries = getEntries(store)
                    val newEntry = get(oldNode.key, shift, store)
                    val changeOrRemoveEvent = newEntry.orNull().flatMapMaybe { newValue ->
                        if (newValue == null) {
                            maybeOf(EntryRemovedEvent(oldNode.key, oldNode.value))
                        } else if (newValue != oldNode.value) {
                            maybeOf(EntryChangedEvent(oldNode.key, oldNode.value, newValue))
                        } else {
                            maybeOfEmpty()
                        }
                    }.asObservable()
                    val entryAddedEvents = entries.filter { it.first != oldNode.key }.map { EntryAddedEvent(it.first, it.second) }
                    observableOf(changeOrRemoveEvent, entryAddedEvents).flatten()
                }
            }
            is CPHamtSingle -> {
                if (oldNode.numLevels == 1) {
                    getChanges(CPHamtInternal.replace(oldNode), shift, store, changesOnly)
                } else {
                    getChanges(CPHamtInternal.replace(oldNode.splitOneLevel()), shift, store, changesOnly)
                }
            }
            else -> {
                throw RuntimeException("Unknown type: " + oldNode!!::class.simpleName)
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

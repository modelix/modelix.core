package org.modelix.model.persistent

import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.streams.IStream
import org.modelix.streams.ifEmpty
import org.modelix.streams.plus

class CPHamtLeaf(
    val key: Long,
    val value: KVEntryReference<CPNode>,
) : CPHamtNode() {
    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf(value)

    override fun serialize(): String {
        return """L/${longToHex(key)}/${value.getHash()}"""
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) {
            if (value?.getHash() == this.value.getHash()) {
                IStream.of(this)
            } else {
                IStream.ofNotNull(create(key, value))
            }
        } else {
            createEmptyNode()
                .put(this.key, this.value, shift, store)
                .ifEmpty { createEmptyNode() }
                .flatMapZeroOrOne { it.put(key, value, shift, store) }
        }
    }

    override fun putAll(entries: List<Pair<Long, KVEntryReference<CPNode>?>>, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        return if (entries.size == 1) {
            val entry = entries.single()
            put(entry.first, entry.second, shift, store)
        } else {
            val newEntries = if (entries.any { it.first == this.key }) entries else entries + (this.key to this.value)
            createEmptyNode().putAll(newEntries, shift, store)
        }
    }

    override fun remove(key: Long, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) {
            IStream.empty()
        } else {
            IStream.of(this)
        }
    }

    override fun get(key: Long, shift: Int, store: IAsyncObjectStore): IStream.ZeroOrOne<KVEntryReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) IStream.of(value) else IStream.empty()
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
        store: IAsyncObjectStore,
    ): IStream.Many<Pair<Long, KVEntryReference<CPNode>?>> {
        return if (keys.contains(this.key)) IStream.of(key to value) else IStream.empty()
    }

    override fun getEntries(store: IAsyncObjectStore): IStream.Many<Pair<Long, KVEntryReference<CPNode>>> {
        return IStream.of(key to value)
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IAsyncObjectStore, changesOnly: Boolean): IStream.Many<MapChangeEvent> {
        return if (oldNode === this || hash == oldNode?.hash) {
            IStream.empty()
        } else if (changesOnly) {
            if (oldNode != null) {
                oldNode.get(key, shift, store).orNull().flatMapZeroOrOne { oldValue ->
                    if (oldValue != null && value != oldValue) {
                        IStream.of(EntryChangedEvent(key, oldValue, value))
                    } else {
                        IStream.empty()
                    }
                }
            } else {
                IStream.empty()
            }
        } else {
            var oldValue: KVEntryReference<CPNode>? = null

            oldNode!!.getEntries(store).flatMap { (k: Long, v: KVEntryReference<CPNode>) ->
                if (k == key) {
                    oldValue = v
                    IStream.empty<EntryRemovedEvent>()
                } else {
                    IStream.of(EntryRemovedEvent(k, v))
                }
            }.plus(
                IStream.deferZeroOrOne {
                    val oldValue = oldValue
                    if (oldValue == null) {
                        IStream.of(EntryAddedEvent(key, value))
                    } else if (oldValue != value) {
                        IStream.of(EntryChangedEvent(key, oldValue, value))
                    } else {
                        IStream.empty()
                    }
                },
            )
        }
    }

    override fun objectDiff(oldObject: IKVValue?, shift: Int, store: IAsyncObjectStore): IStream.Many<IKVValue> {
        return when (oldObject) {
            is CPHamtLeaf -> {
                if (this.hash == oldObject.hash) {
                    IStream.empty()
                } else {
                    IStream.of(this) + value.getValue(store)
                }
            }
            is CPHamtInternal, is CPHamtSingle -> {
                oldObject.get(key, shift, store).orNull().flatMapZeroOrOne { oldValue ->
                    if (oldValue?.getHash() == value.getHash()) {
                        IStream.empty()
                    } else {
                        IStream.of(this)
                    }
                }
            }
            else -> IStream.of(this)
        }
    }

    companion object {
        fun create(key: Long, value: KVEntryReference<CPNode>?): CPHamtLeaf? {
            if (value == null) return null
            return CPHamtLeaf(key, value)
        }
    }
}

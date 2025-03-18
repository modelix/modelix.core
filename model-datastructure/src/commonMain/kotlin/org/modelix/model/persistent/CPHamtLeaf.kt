package org.modelix.model.persistent

import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectLoader
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.requireDifferentHash
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.streams.IStream
import org.modelix.streams.ifEmpty
import org.modelix.streams.plus

data class CPHamtLeaf(
    val key: Long,
    val value: ObjectReference<CPNode>,
) : CPHamtNode() {
    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> = listOf(value)

    override fun serialize(): String {
        return """L/${longToHex(key)}/${value.getHash()}"""
    }

    override fun put(key: Long, value: ObjectReference<CPNode>?, shift: Int, store: IObjectLoader): IStream.ZeroOrOne<CPHamtNode> {
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

    override fun putAll(entries: List<Pair<Long, ObjectReference<CPNode>?>>, shift: Int, store: IObjectLoader): IStream.ZeroOrOne<CPHamtNode> {
        return if (entries.size == 1) {
            val entry = entries.single()
            put(entry.first, entry.second, shift, store)
        } else {
            val newEntries = if (entries.any { it.first == this.key }) entries else entries + (this.key to this.value)
            createEmptyNode().putAll(newEntries, shift, store)
        }
    }

    override fun remove(key: Long, shift: Int, store: IObjectLoader): IStream.ZeroOrOne<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) {
            IStream.empty()
        } else {
            IStream.of(this)
        }
    }

    override fun get(key: Long, shift: Int, store: IObjectLoader): IStream.ZeroOrOne<ObjectReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) IStream.of(value) else IStream.empty()
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
        store: IObjectLoader,
    ): IStream.Many<Pair<Long, ObjectReference<CPNode>?>> {
        return if (keys.contains(this.key)) IStream.of(key to value) else IStream.empty()
    }

    override fun getEntries(store: IObjectLoader): IStream.Many<Pair<Long, ObjectReference<CPNode>>> {
        return IStream.of(key to value)
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IObjectLoader, changesOnly: Boolean): IStream.Many<MapChangeEvent> {
        requireDifferentHash(oldNode)
        return if (oldNode === this) {
            IStream.empty()
        } else if (changesOnly) {
            if (oldNode != null) {
                oldNode.get(key, shift, store).orNull().flatMapZeroOrOne { oldValue ->
                    if (oldValue != null && value.getHash() != oldValue.getHash()) {
                        IStream.of(EntryChangedEvent(key, oldValue, value))
                    } else {
                        IStream.empty()
                    }
                }
            } else {
                IStream.empty()
            }
        } else {
            var oldValue: ObjectReference<CPNode>? = null

            oldNode!!.getEntries(store).flatMap { (k: Long, v: ObjectReference<CPNode>) ->
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
                    } else if (oldValue.getHash() != value.getHash()) {
                        IStream.of(EntryChangedEvent(key, oldValue, value))
                    } else {
                        IStream.empty()
                    }
                },
            )
        }
    }

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?, shift: Int, loader: IObjectLoader): IStream.Many<Object<*>> {
        return when (oldObject?.data) {
            is CPHamtLeaf -> {
                requireDifferentHash(oldObject.data)
                IStream.of(self) + value.resolve(loader)
            }
            is CPHamtInternal, is CPHamtSingle -> {
                oldObject.data.get(key, shift, loader).orNull().flatMapZeroOrOne { oldValue ->
                    if (oldValue?.getHash() == value.getHash()) {
                        IStream.empty()
                    } else {
                        IStream.of(self)
                    }
                }
            }
            else -> IStream.of(self)
        }
    }

    companion object {
        fun create(key: Long, value: ObjectReference<CPNode>?): CPHamtLeaf? {
            if (value == null) return null
            return CPHamtLeaf(key, value)
        }
    }
}

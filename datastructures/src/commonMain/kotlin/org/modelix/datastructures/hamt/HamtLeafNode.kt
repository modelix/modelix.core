package org.modelix.datastructures.hamt

import org.modelix.datastructures.EntryAddedEvent
import org.modelix.datastructures.EntryChangedEvent
import org.modelix.datastructures.EntryRemovedEvent
import org.modelix.datastructures.MapChangeEvent
import org.modelix.datastructures.btree.BTree
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.requireDifferentHash
import org.modelix.datastructures.objects.upcast
import org.modelix.streams.IStream
import org.modelix.streams.plus

data class HamtLeafNode<K, V : Any>(
    override val config: Config<K, V>,
    val key: K,
    val value: V,
) : HamtNode<K, V>() {
    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> =
        config.valueConfig.getContainmentReferences(value)

    override fun serialize(): String {
        return """L/${config.keyConfig.serialize(key)}/${config.valueConfig.serialize(value)}"""
    }

    override fun put(key: K, value: V, shift: Int, graph: IObjectGraph): IStream.One<HamtNode<K, V>> {
        require(shift <= MAX_SHIFT + BITS_PER_LEVEL) { "$shift > ${MAX_SHIFT + BITS_PER_LEVEL}" }
        return if (config.equal(key, this.key)) {
            if (config.equal(value, this.value)) {
                IStream.of(this)
            } else {
                IStream.of(create(config, key, value))
            }
        } else {
            if (shift > MAX_SHIFT) {
                IStream.of(
                    HamtCollisionNode(
                        config,
                        BTree(config.btreeConfig).put(this.key, this.value).put(key, value),
                    ),
                )
            } else {
                createEmptyNode()
                    .put(this.key, this.value, shift, graph)
                    .flatMapOne { it.put(key, value, shift, graph) }
            }
        }
    }

    override fun putAll(entries: List<Pair<K, V>>, shift: Int, graph: IObjectGraph): IStream.One<HamtNode<K, V>> {
        require(shift <= MAX_SHIFT + BITS_PER_LEVEL) { "$shift > ${MAX_SHIFT + BITS_PER_LEVEL}" }
        return if (entries.size == 1) {
            val entry = entries.single()
            put(entry.first, entry.second, shift, graph)
        } else {
            if (shift + BITS_PER_LEVEL > MAX_SHIFT) {
                entries.fold<_, IStream.One<HamtNode<K, V>>>(IStream.of(this)) { acc, entry ->
                    acc.flatMapOne { it.put(entry.first, entry.second, shift, graph) }
                }
            } else {
                val newEntries = if (entries.any { it.first == this.key }) entries else entries + (this.key to this.value)
                createEmptyNode().putAll(newEntries, shift, graph)
            }
        }
    }

    override fun remove(key: K, shift: Int, store: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
        require(shift <= MAX_SHIFT + BITS_PER_LEVEL) { "$shift > ${MAX_SHIFT + BITS_PER_LEVEL}" }
        return if (key == this.key) {
            IStream.empty()
        } else {
            IStream.of(this)
        }
    }

    override fun get(key: K, shift: Int): IStream.ZeroOrOne<V> {
        require(shift <= MAX_SHIFT + BITS_PER_LEVEL) { "$shift > ${MAX_SHIFT + BITS_PER_LEVEL}" }
        return if (key == this.key) IStream.of(value) else IStream.empty()
    }

    override fun getAll(
        keys: Iterable<K>,
        shift: Int,
    ): IStream.Many<Pair<K, V?>> {
        return if (keys.contains(this.key)) IStream.of(key to value) else IStream.empty()
    }

    override fun getEntries(): IStream.Many<Pair<K, V>> {
        return IStream.of(key to value)
    }

    override fun getChanges(oldNode: HamtNode<K, V>?, shift: Int, changesOnly: Boolean): IStream.Many<MapChangeEvent<K, V>> {
        requireDifferentHash(oldNode)
        return if (oldNode === this) {
            IStream.empty()
        } else if (changesOnly) {
            if (oldNode != null) {
                oldNode.get(key, shift).orNull().flatMapZeroOrOne { oldValue ->
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
            var oldValue: V? = null

            oldNode!!.getEntries().flatMap { (k: K, v: V) ->
                if (k == key) {
                    oldValue = v
                    IStream.empty<EntryRemovedEvent<K, V>>()
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

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?, shift: Int): IStream.Many<Object<*>> {
        val oldData = oldObject?.data?.upcast<HamtNode<K, V>>()
        return when (oldData) {
            is HamtLeafNode<*, *> -> {
                IStream.of(self) + IStream.many(config.valueConfig.getContainmentReferences(value))
                    .flatMap { it.resolve() }
            }
            is HamtInternalNode<*, *>, is HamtSingleChildNode<*, *> -> {
                oldData.get(key, shift).orNull().flatMapZeroOrOne { oldValue ->
                    if (oldValue == value) {
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
        fun <K, V : Any> create(config: Config<K, V>, key: K, value: V): HamtLeafNode<K, V> {
            return HamtLeafNode<K, V>(config, key, value)
        }
    }
}

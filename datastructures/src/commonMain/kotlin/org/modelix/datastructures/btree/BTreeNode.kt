package org.modelix.datastructures.btree

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.datastructures.serialization.SerializationSeparators
import org.modelix.streams.IStream

sealed class BTreeNode<K, V> : IObjectData {
    abstract val config: BTreeConfig<K, V>
    protected operator fun K.compareTo(other: K): Int = config.keyConfiguration.compare(this, other)

    abstract fun validate(isRoot: Boolean)

    abstract fun put(key: K, value: V): Replacement<K, V>
    abstract fun get(key: K): V?
    abstract fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>>
    abstract fun remove(key: K): Replacement<K, V>

    abstract fun getEntries(): IStream.Many<BTreeEntry<K, V>>

    abstract fun getFirstEntry(): BTreeEntry<K, V>
    abstract fun getLastEntry(): BTreeEntry<K, V>
    abstract fun removeFirstOrLastEntry(first: Boolean): RemovedEntry<K, V>
    fun removeFirstEntry(): RemovedEntry<K, V> = removeFirstOrLastEntry(true)
    fun removeLastEntry(): RemovedEntry<K, V> = removeFirstOrLastEntry(false)

    abstract fun size(): Int
    abstract fun hasMinSize(): Boolean
    abstract fun hasMaxSize(): Boolean
    abstract fun isOverfilled(): Boolean
    abstract fun split(): Replacement.Splitted<K, V>
    fun splitIfNecessary(): Replacement<K, V> = if (isOverfilled()) split() else Replacement.Single(this)
    abstract fun mergeWithSibling(knownSeparator: K, right: BTreeNode<K, V>): BTreeNode<K, V>

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?): IStream.Many<Object<*>> {
        // TODO performance
        return self.getDescendantsAndSelf()
    }

    class Deserializer<K, V>(val config: BTreeConfig<K, V>) : IObjectDeserializer<BTreeNode<K, V>> {
        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): BTreeNode<K, V> {
            val parts = serialized.split(SerializationSeparators.LEVEL1)
            return when (parts[0]) {
                "L" -> {
                    val entries = parts[1].split(SerializationSeparators.LEVEL2).map {
                        val entryParts = it.split(SerializationSeparators.MAPPING, limit = 2)
                        BTreeEntry(
                            config.keyConfiguration.deserialize(entryParts[0]),
                            config.valueConfiguration.deserialize(entryParts[1]),
                        )
                    }
                    BTreeNodeLeaf(config, entries)
                }
                "I" -> {
                    val keys = parts[1].split(SerializationSeparators.LEVEL2).map {
                        config.keyConfiguration.deserialize(it)
                    }
                    val children = parts[2].split(SerializationSeparators.LEVEL2)
                        .map { referenceFactory.fromHashString(it, this) }
                    BTreeNodeInternal(config, keys, children)
                }
                else -> error("unknown node type ${parts[0]}: $serialized")
            }
        }
    }
}

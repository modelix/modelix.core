package org.modelix.datastructures.btree

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.serialization.SerializationSeparators
import org.modelix.streams.IStream

data class BTreeNodeLeaf<K, V>(
    override val config: BTreeConfig<K, V>,
    val entries: List<BTreeEntry<K, V>>,
) : BTreeNode<K, V>() {

    override fun validate(isRoot: Boolean) {
        check(entries.size == entries.map { it.key }.toSet().size) {
            "duplicate entries: $entries"
        }
        check(entries.map { it.key }.sortedWith(config.keyConfiguration) == entries.map { it.key }) {
            "entries not sorted: $entries"
        }
        check(entries.size <= config.maxEntries) {
            "overfilled: $this"
        }
        if (!isRoot) {
            check(entries.size >= config.minEntries) {
                "underfilled: $this"
            }
        }
    }

    override fun getEntries(): IStream.Many<BTreeEntry<K, V>> {
        return IStream.many(entries)
    }

    override fun size() = entries.size
    override fun hasMinSize() = entries.size <= config.minEntries
    override fun hasMaxSize() = entries.size >= config.maxEntries
    override fun isOverfilled(): Boolean = entries.size > config.maxEntries

    override fun split(): Replacement.Splitted<K, V> {
        check(!hasMinSize())

        // left gets one more if odd number of entries
        val leftSize = (entries.size + 1) / 2

        val left = BTreeNodeLeaf<K, V>(config, entries.take(leftSize))
        val right = BTreeNodeLeaf<K, V>(config, entries.drop(leftSize))

        return Replacement.Splitted(left, right.entries.first().key, right)
    }

    override fun mergeWithSibling(knownSeparator: K, right: BTreeNode<K, V>): BTreeNode<K, V> {
        right as BTreeNodeLeaf<K, V>
        return copy(entries = entries + right.entries)
    }

    override fun put(key: K, value: V): Replacement<K, V> {
        return insertEntry(BTreeEntry(key, value)).splitIfNecessary()
    }

    override fun get(key: K): V? {
        val index = entries.binarySearch { it.key.compareTo(key) }
        return if (index >= 0) {
            entries[index].value
        } else {
            null
        }
    }

    override fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>> {
        return IStream.many(
            keys.mapNotNull { key ->
                val index = entries.binarySearch { it.key.compareTo(key) }
                if (index >= 0) {
                    key to entries[index].value
                } else {
                    null
                }
            },
        )
    }

    override fun remove(key: K): Replacement<K, V> {
        val index = entries.binarySearch { it.key.compareTo(key) }
        return if (index >= 0) {
            Replacement.Single(copy(entries = entries.take(index) + entries.drop(index + 1)))
        } else {
            Replacement.Single(this)
        }
    }

    override fun getFirstEntry(): BTreeEntry<K, V> {
        return entries.first()
    }

    override fun getLastEntry(): BTreeEntry<K, V> {
        return entries.last()
    }

    override fun removeFirstOrLastEntry(first: Boolean): RemovedEntry<K, V> {
        check(size() > config.minEntries)
        return RemovedEntry(
            if (first) entries.first() else entries.last(),
            Replacement.Single(
                copy(
                    entries = if (first) entries.drop(1) else entries.dropLast(1),
                ),
            ),
        )
    }

    private fun insertEntry(newEntry: BTreeEntry<K, V>): BTreeNodeLeaf<K, V> {
        val index = entries.binarySearch(newEntry, config.entryComparatorForInsertion)
        return if (index >= 0) {
            if (config.multimap) {
                // In case of multimaps, the comparator also compares the values, meaning the value of the newEntry
                // already exists in the tree.
                this
            } else {
                // In case of non-multimaps, the comparator only compares the key. Check the value to avoid unnecessary
                // changes to the tree.
                if (config.valueConfiguration.equal(entries[index].value, newEntry.value)) {
                    this
                } else {
                    copy(entries = entries.take(index) + newEntry + entries.drop(index + 1))
                }
            }
        } else {
            val insertionIndex = if (index >= 0) index else (-index) - 1
            copy(entries = entries.take(insertionIndex) + newEntry + entries.drop(insertionIndex))
        }
    }

    override fun getDeserializer(): IObjectDeserializer<*> {
        return config.nodeDeserializer
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return emptyList()
    }

    override fun serialize(): String {
        return "L" + SerializationSeparators.LEVEL1 + entries.joinToString(SerializationSeparators.LEVEL2) {
            config.keyConfiguration.serialize(it.key) +
                SerializationSeparators.MAPPING +
                config.valueConfiguration.serialize(it.value)
        }
    }
}

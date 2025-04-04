package org.modelix.datastructures.patricia

import org.modelix.datastructures.IPersistentMap
import org.modelix.datastructures.IPersistentMapRootData
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.getHashString
import org.modelix.datastructures.objects.upcast
import org.modelix.datastructures.serialization.SplitJoinSerializer
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode
import org.modelix.streams.IStream
import org.modelix.streams.plus

data class PatriciaNode<K, V : Any>(
    val config: PatriciaTrieConfig<K, V>,
    val ownPrefix: String,
    val firstChars: String,
    val children: List<ObjectReference<PatriciaNode<K, V>>>,

    /**
     * [ownPrefix] is part of the key for entry that s stored in this node
     */
    val value: V?,
) : IPersistentMapRootData<K, V> {
    constructor(config: PatriciaTrieConfig<K, V>) : this(config, "", "", emptyList(), null)

    constructor(config: PatriciaTrieConfig<K, V>, key: String, value: V) :
        this(config = config, ownPrefix = key, value = value, firstChars = "", children = emptyList())

    override fun createMapInstance(self: Object<IPersistentMapRootData<K, V>>): IPersistentMap<K, V> {
        return PatriciaTrie(self.upcast<PatriciaNode<K, V>>())
    }

    fun calculateDepth(): Int = (children.maxOfOrNull { it.resolveNow().data.calculateDepth() } ?: 0) + 1

    fun withChildInserted(index: Int, firstChar: Char, child: PatriciaNode<K, V>): PatriciaNode<K, V> {
        return copy(
            firstChars = firstChars.take(index) + firstChar + firstChars.drop(index),
            children = children.take(index) + config.graph.fromCreated(child) + children.drop(index),
        )
    }

    private fun withChildReplacedNullable(index: Int, child: PatriciaNode<K, V>?): PatriciaNode<K, V>? {
        return if (child == null) withoutChild(index) else withChildReplaced(index, config.graph.fromCreated(child))
    }

    private fun withChildReplaced(index: Int, child: ObjectReference<PatriciaNode<K, V>>): PatriciaNode<K, V> {
        if (children[index] === child) return this
        return copy(children = children.take(index) + child + children.drop(index + 1))
    }

    fun withoutChild(index: Int): PatriciaNode<K, V>? {
        return copy(
            firstChars = firstChars.take(index) + firstChars.drop(index + 1),
            children = children.take(index) + children.drop(index + 1),
        )
    }

    fun tryMerge(): IStream.ZeroOrOne<PatriciaNode<K, V>> {
        if (value != null) return IStream.of(this)
        return when (children.size) {
            0 -> IStream.empty()
            1 -> children.single().resolve().map { it.data.copy(ownPrefix = this.ownPrefix + it.data.ownPrefix) }
            else -> IStream.of(this)
        }
    }

    fun withValue(newValue: V?) = copy(value = newValue)

    fun getEntries(prefix: CharSequence): IStream.Many<Pair<CharSequence, V>> {
        val fullPrefix = prefix + ownPrefix
        val descendants = IStream.Companion.many(children).flatMap { it.resolveData() }
            .flatMap { it.getEntries(fullPrefix) }
        return if (value != null) {
            IStream.Companion.of(fullPrefix to value).plus(descendants)
        } else {
            descendants
        }
    }

    fun getValues(prefix: CharSequence): IStream.Many<V> {
        val fullPrefix = prefix + ownPrefix
        val descendants = IStream.Companion.many(children).flatMap { it.resolve() }
            .flatMap { it.data.getValues(fullPrefix) }
        return if (value != null) {
            IStream.Companion.of(value).plus(descendants)
        } else {
            descendants
        }
    }

    /**
     * Returns a new root that contains only those entries that start with the given prefix.
     */
    fun slice(prefix: CharSequence): IStream.ZeroOrOne<PatriciaNode<K, V>> {
        return getSubtree(prefix).map { it.copy(ownPrefix = prefix.toString() + it.ownPrefix) }
    }

    /**
     * Returns a subtree with all known suffixes of the provided prefix.
     */
    fun getSubtree(prefix: CharSequence): IStream.ZeroOrOne<PatriciaNode<K, V>> {
        if (ownPrefix.startsWith(prefix)) {
            return if (ownPrefix.length == prefix.length) {
                IStream.of(this.copy(ownPrefix = ""))
            } else {
                split(prefix).children.single().resolveData()
            }
        }
        if (!prefix.startsWith(ownPrefix)) return IStream.empty()
        val remainingPrefix = prefix.drop(ownPrefix.length)
        val index = firstChars.binarySearch(remainingPrefix.first())
        if (index >= 0) {
            return children[index].resolveData().flatMapZeroOrOne { it.getSubtree(remainingPrefix) }
        }
        return IStream.empty()
    }

    fun get(partialKey: CharSequence): IStream.ZeroOrOne<V> {
        if (!partialKey.startsWith(ownPrefix)) return IStream.empty()
        if (ownPrefix == partialKey) return IStream.ofNotNull(value)
        val remainingKey = partialKey.drop(ownPrefix.length)
        val index = firstChars.binarySearch(remainingKey.first())
        return if (index >= 0) {
            children[index].resolveData().flatMapZeroOrOne { child ->
                child.get(remainingKey)
            }
        } else {
            IStream.empty()
        }
    }

    fun put(newKey: CharSequence, newValue: V?): IStream.ZeroOrOne<PatriciaNode<K, V>> {
        val commonPrefix = newKey.commonPrefixWith(this.ownPrefix)
        val remainingNewKey = newKey.drop(commonPrefix.length)
        val remainingOwnPrefix = this.ownPrefix.drop(commonPrefix.length)
        return (
            if (remainingOwnPrefix.isEmpty() && remainingNewKey.isEmpty()) {
                // key references this node -> overwrite the value
                IStream.of(withValue(newValue))
            } else if (remainingOwnPrefix.isEmpty()) {
                // key is longer -> insert into children
                val index = firstChars.binarySearch(remainingNewKey.first())
                if (index >= 0) {
                    children[index].resolveData()
                        .flatMapOne { it.put(remainingNewKey, newValue).orNull() }
                        .mapNotNull { withChildReplacedNullable(index, it) }
                } else {
                    val insertionIndex = (-index) - 1
                    val newChild = PatriciaNode<K, V>(
                        config = config,
                        ownPrefix = remainingNewKey.toString(),
                        value = newValue,
                        firstChars = "",
                        children = emptyList(),
                    )
                    IStream.of(withChildInserted(insertionIndex, remainingNewKey.first(), newChild))
                }
            } else {
                // key is shorter -> need to split into a node with a shorter prefix
                split(commonPrefix).put(newKey, newValue)
            }
            ).flatMapZeroOrOne { it.tryMerge() }
    }

    fun split(commonPrefix: CharSequence): PatriciaNode<K, V> {
        val remainingPrefix = this.ownPrefix.drop(commonPrefix.length)
        return PatriciaNode<K, V>(
            config = config,
            ownPrefix = commonPrefix.toString(),
            value = null,
            firstChars = remainingPrefix.take(1),
            children = listOf(
                config.graph.fromCreated(
                    PatriciaNode<K, V>(
                        config = config,
                        ownPrefix = remainingPrefix,
                        firstChars = this.firstChars,
                        children = this.children,
                        value = this.value,
                    ),
                ),
            ),
        )
    }

    override fun serialize(): String {
        val S1 = SplitJoinSerializer.SEPARATORS[0]
        val S2 = SplitJoinSerializer.SEPARATORS[1]
        return ownPrefix.urlEncode() +
            S1 + firstChars.urlEncode() +
            S1 + children.joinToString(S2.toString()) { it.getHashString() } +
            S1 + value
    }

    override fun getDeserializer(): IObjectDeserializer<*> {
        return Deserializer<K, V>(config)
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return children + (value?.let { config.valueConfig.getContainmentReferences(it) } ?: emptyList())
    }

    class Deserializer<K, V : Any>(val config: (IObjectGraph) -> PatriciaTrieConfig<K, V>) : IObjectDeserializer<PatriciaNode<K, V>> {
        constructor(config: PatriciaTrieConfig<K, V>) : this({ config })
        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): PatriciaNode<K, V> {
            val S1 = SplitJoinSerializer.SEPARATORS[0]
            val S2 = SplitJoinSerializer.SEPARATORS[1]
            val parts = serialized.split(S1, limit = 4)
            val config = config(referenceFactory as IObjectGraph)
            return PatriciaNode<K, V>(
                config,
                parts[0].urlDecode()!!,
                parts[1].urlDecode()!!,
                parts[2].split(S2).filter { it.isNotEmpty() }.map { referenceFactory.fromHashString(it, this) },
                config.valueConfig.deserialize(parts[3]),
            )
        }
    }
}

private class CharSequenceConcatenation(val a: CharSequence, val b: CharSequence) : CharSequence {
    override fun get(index: Int): Char {
        return if (index < a.length) a.get(index) else b.get(index + a.length)
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        // Let's not build an unlimited nested structure of CharSequences.
        // That's probably the reason why there is no existing implementation of a concatenation.
        return toString().subSequence(startIndex, endIndex)
    }

    override val length: Int
        get() = a.length + b.length

    override fun toString(): String {
        return "$a$b"
    }
}

private operator fun CharSequence.plus(other: CharSequence): CharSequence = CharSequenceConcatenation(this, other)

private fun String.binarySearch(element: Char): Int {
    var low = 0
    var high = length - 1

    while (low <= high) {
        val mid = (low + high).ushr(1) // safe from overflows
        val midVal = get(mid)
        val cmp = compareValues(midVal, element)

        if (cmp < 0) {
            low = mid + 1
        } else if (cmp > 0) {
            high = mid - 1
        } else {
            return mid // key found
        }
    }
    return -(low + 1) // key not found
}

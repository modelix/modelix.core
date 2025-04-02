package org.modelix.datastructures.hamt

import org.modelix.datastructures.IPersistentMap
import org.modelix.datastructures.IPersistentMapRootData
import org.modelix.datastructures.MapChangeEvent
import org.modelix.datastructures.btree.BTree
import org.modelix.datastructures.btree.BTreeConfig
import org.modelix.datastructures.btree.BTreeNode
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.hash
import org.modelix.datastructures.objects.upcast
import org.modelix.streams.IStream
import kotlin.jvm.JvmName

/**
 * Implementation of a hash array mapped trie.
 */
sealed class HamtNode<K, V : Any> : IPersistentMapRootData<K, V> {
    abstract val config: Config<K, V>

    override fun createMapInstance(self: Object<IPersistentMapRootData<K, V>>): IPersistentMap<K, V> {
        return HamtTree(self.upcast<HamtNode<K, V>>())
    }

    protected operator fun V.compareTo(other: V): Int = config.valueConfig.compare(this, other)

    override fun getDeserializer() = config.deserializer

    protected fun createEmptyNode(): HamtNode<K, V> {
        return HamtInternalNode(config, 0, arrayOf())
    }

    fun getAll(keys: Iterable<K>): IStream.One<List<V?>> {
        return getAll(keys, 0).toList().map {
            val entries = it.associateBy { it.first }
            keys.map { entries[it]?.second }
        }
    }

    fun put(key: K, value: V?, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
        return if (value == null) remove(key, 0, graph) else put(key, value, 0, graph)
    }

    fun remove(key: K, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>> {
        return remove(key, 0, graph)
    }

    fun get(key: K): IStream.ZeroOrOne<V> = get(key, 0)

    abstract fun get(key: K, shift: Int): IStream.ZeroOrOne<V>
    abstract fun put(key: K, value: V, shift: Int, graph: IObjectGraph): IStream.One<HamtNode<K, V>>
    abstract fun putAll(entries: List<Pair<K, V>>, shift: Int, graph: IObjectGraph): IStream.One<HamtNode<K, V>>
    abstract fun getAll(keys: Iterable<K>, shift: Int): IStream.Many<Pair<K, V?>>
    abstract fun remove(key: K, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<HamtNode<K, V>>
    abstract fun getEntries(): IStream.Many<Pair<K, V>>
    abstract fun getChanges(oldNode: HamtNode<K, V>?, shift: Int, changesOnly: Boolean): IStream.Many<MapChangeEvent<K, V>>
    fun getChanges(oldNode: HamtNode<K, V>?, changesOnly: Boolean) = getChanges(oldNode, 0, changesOnly)

    abstract fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
        shift: Int,
    ): IStream.Many<Object<*>>

    final override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
    ): IStream.Many<Object<*>> {
        return if (self.getHash() == oldObject?.getHash()) {
            IStream.empty()
        } else {
            objectDiff(self, oldObject, 0)
        }
    }

    override fun toString(): String {
        return "$hash -> ${serialize()}"
    }

    companion object {
        const val BITS_PER_LEVEL = 5
        const val ENTRIES_PER_LEVEL = 1 shl BITS_PER_LEVEL
        const val LEVEL_MASK: Long = (-0x1 ushr 32 - BITS_PER_LEVEL).toLong()
        const val MAX_BITS = 64
        const val MAX_SHIFT = MAX_BITS - 1
        const val MAX_LEVELS = (MAX_BITS + BITS_PER_LEVEL - 1) / BITS_PER_LEVEL
        const val SEPARATOR = "/"
        const val SEPARATOR2 = ","

        fun indexFromHash(hash: Long, shift: Int): Int = levelBits(hash, shift)

        fun intToHex(value: Int): String = value.toUInt().toString(16)
        fun intFromHex(hex: String): Int = hex.toLong(16).toInt()
        fun longFromHex(hex: String): Long = hex.toULong(16).toLong()
        fun longToHex(value: Long): String = value.toULong().toString(16)

        fun bitCount(bits: Int): Int {
            var i = bits
            i -= (i ushr 1 and 0x55555555)
            i = (i and 0x33333333) + (i ushr 2 and 0x33333333)
            i = i + (i ushr 4) and 0x0f0f0f0f
            i += (i ushr 8)
            i += (i ushr 16)
            return i and 0x3f
        }

        fun levelBits(hash: Long, shift: Int): Int {
            val s = MAX_BITS - BITS_PER_LEVEL - shift
            return if (s >= 0) {
                ((hash ushr s) and LEVEL_MASK).toInt()
            } else {
                ((hash shl -s) and LEVEL_MASK).toInt()
            }
        }
    }

    class Deserializer<K, V : Any> internal constructor(val config: Config<K, V>) : IObjectDeserializer<HamtNode<K, V>> {
        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): HamtNode<K, V> {
            val parts = serialized.split(SEPARATOR)
            val data = when (parts[0]) {
                "L" -> HamtLeafNode<K, V>(
                    config,
                    config.keyConfig.deserialize(parts[1]),
                    config.valueConfig.deserialize(parts[2]),
                )
                "I" -> HamtInternalNode<K, V>(
                    config,
                    intFromHex(parts[1]),
                    parts[2].split(SEPARATOR2)
                        .filter { it.isNotEmpty() }
                        .map { referenceFactory(it, this) }
                        .toTypedArray(),
                )
                "S" -> HamtSingleChildNode<K, V>(
                    config,
                    parts[1].toInt(),
                    longFromHex(parts[2]),
                    referenceFactory(parts[3], this),
                )
                "C" -> HamtCollisionNode(
                    config,
                    BTree(config.btreeDeserializer.deserialize(serialized.substring(2), referenceFactory)),
                )
                else -> throw RuntimeException("Unknown type: " + parts[0] + ", input: " + serialized)
            }
            return data
        }
    }

    data class Config<K, V : Any>(
        val graph: IObjectGraph,
        val keyConfig: IDataTypeConfiguration<K>,
        val valueConfig: IDataTypeConfiguration<V>,
    ) {
        val btreeConfig = BTreeConfig.builder()
            .graph(graph)
            .keyConfiguration(keyConfig)
            .valueConfiguration(valueConfig)
            .build()
        val deserializer: IObjectDeserializer<HamtNode<K, V>> = Deserializer<K, V>(this)
        val btreeDeserializer = BTreeNode.Deserializer(btreeConfig)

        @JvmName("keysEqual")
        fun equal(a: K, b: K): Boolean {
            return if (a == null) {
                b == null
            } else {
                b != null && keyConfig.equal(a, b)
            }
        }

        @JvmName("valuesEqual")
        fun equal(a: V?, b: V?): Boolean {
            return if (a == null) {
                b == null
            } else {
                b != null && valueConfig.equal(a, b)
            }
        }
    }
}

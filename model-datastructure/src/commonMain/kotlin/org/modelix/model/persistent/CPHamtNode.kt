package org.modelix.model.persistent

import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.hash
import org.modelix.model.persistent.SerializationUtil.intFromHex
import org.modelix.model.persistent.SerializationUtil.longFromHex
import org.modelix.streams.IStream

/**
 * Implementation of a hash array mapped trie.
 */
sealed class CPHamtNode : ITreeData {
    override fun getDeserializer() = DESERIALIZER

    protected fun createEmptyNode(): CPHamtNode {
        return CPHamtInternal(0, arrayOf())
    }

    fun getAll(keys: LongArray): IStream.One<List<ObjectReference<CPNode>?>> {
        return getAll(keys, 0).toList().map {
            val entries = it.associateBy { it.first }
            keys.map { entries[it]?.second }
        }
    }

    fun put(key: Long, value: ObjectReference<CPNode>?, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        return put(key, value, 0, graph)
    }

    fun put(data: CPNode, graph: IObjectGraph): IStream.One<CPHamtNode> {
        return put(data.id, graph(data), graph)
            .exceptionIfEmpty { RuntimeException("Map should not be empty after putting a non-null value") }
    }

    fun remove(key: Long, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        return remove(key, 0, graph)
    }

    fun remove(element: CPNode, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode> {
        return remove(element.id, graph)
    }

    fun get(key: Long): IStream.ZeroOrOne<ObjectReference<CPNode>> = get(key, 0)

    abstract fun get(key: Long, shift: Int): IStream.ZeroOrOne<ObjectReference<CPNode>>
    abstract fun put(key: Long, value: ObjectReference<CPNode>?, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode>
    abstract fun putAll(entries: List<Pair<Long, ObjectReference<CPNode>?>>, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode>
    abstract fun getAll(keys: LongArray, shift: Int): IStream.Many<Pair<Long, ObjectReference<CPNode>?>>
    abstract fun remove(key: Long, shift: Int, graph: IObjectGraph): IStream.ZeroOrOne<CPHamtNode>
    abstract fun getEntries(): IStream.Many<Pair<Long, ObjectReference<CPNode>>>
    abstract fun getChanges(oldNode: CPHamtNode?, shift: Int, changesOnly: Boolean): IStream.Many<MapChangeEvent>
    fun getChanges(oldNode: CPHamtNode?, changesOnly: Boolean) = getChanges(oldNode, 0, changesOnly)

    abstract fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
        shift: Int,
    ): IStream.Many<Object<*>>

    final override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
    ): IStream.Many<Object<*>> {
        return objectDiff(self, oldObject, 0)
    }

    override fun toString(): String {
        return "$hash -> ${serialize()}"
    }

    companion object : ITreeRelatedDeserializer<CPHamtNode> {
        const val BITS_PER_LEVEL = 5
        const val ENTRIES_PER_LEVEL = 1 shl BITS_PER_LEVEL
        const val LEVEL_MASK: Long = (-0x1 ushr 32 - BITS_PER_LEVEL).toLong()
        const val MAX_BITS = 64
        const val MAX_SHIFT = MAX_BITS - 1
        const val MAX_LEVELS = (MAX_BITS + BITS_PER_LEVEL - 1) / BITS_PER_LEVEL

        fun indexFromKey(key: Long, shift: Int): Int = levelBits(key, shift)

        fun levelBits(key: Long, shift: Int): Int {
            val s = MAX_BITS - BITS_PER_LEVEL - shift
            return if (s >= 0) {
                ((key ushr s) and LEVEL_MASK).toInt()
            } else {
                ((key shl -s) and LEVEL_MASK).toInt()
            }
        }

        val DESERIALIZER: IObjectDeserializer<CPHamtNode> = this

        override fun deserialize(input: String, referenceFactory: IObjectReferenceFactory): CPHamtNode {
            val parts = input.split(Separators.LEVEL1)
            val data = when (parts[0]) {
                "L" -> CPHamtLeaf(longFromHex(parts[1]), referenceFactory(parts[2], CPNode))
                "I" -> CPHamtInternal(
                    intFromHex(parts[1]),
                    parts[2].split(Separators.LEVEL2)
                        .filter { it.isNotEmpty() }
                        .map { referenceFactory(it, DESERIALIZER) }
                        .toTypedArray(),
                )
                "S" -> CPHamtSingle(
                    parts[1].toInt(),
                    longFromHex(parts[2]),
                    referenceFactory(parts[3], DESERIALIZER),
                )
                else -> throw RuntimeException("Unknown type: " + parts[0] + ", input: " + input)
            }
            return data
        }
    }
}

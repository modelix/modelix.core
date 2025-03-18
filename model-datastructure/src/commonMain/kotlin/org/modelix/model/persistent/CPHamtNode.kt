package org.modelix.model.persistent

import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectLoader
import org.modelix.model.objects.Object
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.hash
import org.modelix.model.persistent.SerializationUtil.intFromHex
import org.modelix.model.persistent.SerializationUtil.longFromHex
import org.modelix.streams.IStream
import kotlin.jvm.JvmStatic

/**
 * Implementation of a hash array mapped trie.
 */
abstract class CPHamtNode : IObjectData {
    override fun getDeserializer(): (String) -> CPHamtNode = DESERIALIZER

    protected fun createEmptyNode(): CPHamtNode {
        return CPHamtInternal(0, arrayOf())
    }

    fun getAll(keys: LongArray, loader: IObjectLoader): IStream.One<List<ObjectReference<CPNode>?>> {
        return getAll(keys, 0, loader).toList().map {
            val entries = it.associateBy { it.first }
            keys.map { entries[it]?.second }
        }
    }

    fun put(key: Long, value: ObjectReference<CPNode>?, loader: IObjectLoader): IStream.ZeroOrOne<CPHamtNode> {
        return put(key, value, 0, loader)
    }

    fun put(data: CPNode, loader: IObjectLoader): IStream.One<CPHamtNode> {
        return put(data.id, ObjectReference(data), loader)
            .exceptionIfEmpty { RuntimeException("Map should not be empty after putting a non-null value") }
    }

    fun remove(key: Long, loader: IObjectLoader): IStream.ZeroOrOne<CPHamtNode> {
        return remove(key, 0, loader)
    }

    fun remove(element: CPNode, loader: IObjectLoader): IStream.ZeroOrOne<CPHamtNode> {
        return remove(element.id, loader)
    }

    fun get(key: Long, loader: IObjectLoader): IStream.ZeroOrOne<ObjectReference<CPNode>> = get(key, 0, loader)

    abstract fun get(key: Long, shift: Int, loader: IObjectLoader): IStream.ZeroOrOne<ObjectReference<CPNode>>
    abstract fun put(key: Long, value: ObjectReference<CPNode>?, shift: Int, loader: IObjectLoader): IStream.ZeroOrOne<CPHamtNode>
    abstract fun putAll(entries: List<Pair<Long, ObjectReference<CPNode>?>>, shift: Int, loader: IObjectLoader): IStream.ZeroOrOne<CPHamtNode>
    abstract fun getAll(keys: LongArray, shift: Int, loader: IObjectLoader): IStream.Many<Pair<Long, ObjectReference<CPNode>?>>
    abstract fun remove(key: Long, shift: Int, loader: IObjectLoader): IStream.ZeroOrOne<CPHamtNode>
    abstract fun getEntries(loader: IObjectLoader): IStream.Many<Pair<Long, ObjectReference<CPNode>>>
    abstract fun getChanges(oldNode: CPHamtNode?, shift: Int, loader: IObjectLoader, changesOnly: Boolean): IStream.Many<MapChangeEvent>
    fun getChanges(oldNode: CPHamtNode?, loader: IObjectLoader, changesOnly: Boolean) = getChanges(oldNode, 0, loader, changesOnly)

    abstract fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
        shift: Int,
        loader: IObjectLoader,
    ): IStream.Many<Object<*>>

    final override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
        loader: IObjectLoader,
    ): IStream.Many<Object<*>> {
        return objectDiff(self, oldObject, 0, loader)
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

        fun indexFromKey(key: Long, shift: Int): Int = levelBits(key, shift)

        fun levelBits(key: Long, shift: Int): Int {
            val s = MAX_BITS - BITS_PER_LEVEL - shift
            return if (s >= 0) {
                ((key ushr s) and LEVEL_MASK).toInt()
            } else {
                ((key shl -s) and LEVEL_MASK).toInt()
            }
        }

        val DESERIALIZER = { s: String -> deserialize(s) }

        @JvmStatic
        fun deserialize(input: String): CPHamtNode {
            val parts = input.split(Separators.LEVEL1)
            val data = when (parts[0]) {
                "L" -> CPHamtLeaf(longFromHex(parts[1]), ObjectReference(parts[2], CPNode.DESERIALIZER))
                "I" -> CPHamtInternal(
                    intFromHex(parts[1]),
                    parts[2].split(Separators.LEVEL2)
                        .filter { it.isNotEmpty() }
                        .map { ObjectReference(it, DESERIALIZER) }
                        .toTypedArray(),
                )
                "S" -> CPHamtSingle(
                    parts[1].toInt(),
                    longFromHex(parts[2]),
                    ObjectReference(parts[3], DESERIALIZER),
                )
                else -> throw RuntimeException("Unknown type: " + parts[0] + ", input: " + input)
            }
            return data
        }
    }
}

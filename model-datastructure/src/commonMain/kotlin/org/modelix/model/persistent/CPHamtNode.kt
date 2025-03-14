package org.modelix.model.persistent

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.asSingleOrError
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.map
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.SerializationUtil.intFromHex
import org.modelix.model.persistent.SerializationUtil.longFromHex
import kotlin.jvm.JvmStatic

/**
 * Implementation of a hash array mapped trie.
 */
abstract class CPHamtNode : IKVValue {
    override var isWritten: Boolean = false

    override val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) { HashUtil.sha256(serialize()) }

    override fun getDeserializer(): (String) -> IKVValue = DESERIALIZER

    protected fun createEmptyNode(): CPHamtNode {
        return CPHamtInternal(0, arrayOf())
    }

    fun getAll(keys: LongArray, store: IAsyncObjectStore): Single<List<KVEntryReference<CPNode>?>> {
        return getAll(keys, 0, store).toList().map {
            val entries = it.associateBy { it.first }
            keys.map { entries[it]?.second }
        }
    }

    fun put(key: Long, value: KVEntryReference<CPNode>?, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        return put(key, value, 0, store)
    }

    fun put(data: CPNode, store: IAsyncObjectStore): Single<CPHamtNode> {
        return put(data.id, KVEntryReference(data), store)
            .asSingleOrError { RuntimeException("Map should not be empty after putting a non-null value") }
    }

    fun remove(key: Long, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        return remove(key, 0, store)
    }

    fun remove(element: CPNode, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        return remove(element.id, store)
    }

    fun get(key: Long, store: IAsyncObjectStore): Maybe<KVEntryReference<CPNode>> = get(key, 0, store)

    abstract fun get(key: Long, shift: Int, store: IAsyncObjectStore): Maybe<KVEntryReference<CPNode>>
    abstract fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode>
    abstract fun putAll(entries: List<Pair<Long, KVEntryReference<CPNode>?>>, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode>
    abstract fun getAll(keys: LongArray, shift: Int, store: IAsyncObjectStore): Observable<Pair<Long, KVEntryReference<CPNode>?>>
    abstract fun remove(key: Long, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode>
    abstract fun getEntries(store: IAsyncObjectStore): Observable<Pair<Long, KVEntryReference<CPNode>>>
    abstract fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IAsyncObjectStore, changesOnly: Boolean): Observable<MapChangeEvent>
    fun getChanges(oldNode: CPHamtNode?, store: IAsyncObjectStore, changesOnly: Boolean) = getChanges(oldNode, 0, store, changesOnly)

    abstract fun objectDiff(oldObject: IKVValue?, shift: Int, store: IAsyncObjectStore): Observable<IKVValue>
    final override fun objectDiff(oldObject: IKVValue?, store: IAsyncObjectStore): Observable<IKVValue> {
        return objectDiff(oldObject, 0, store)
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
                "L" -> CPHamtLeaf(longFromHex(parts[1]), KVEntryReference(parts[2], CPNode.DESERIALIZER))
                "I" -> CPHamtInternal(
                    intFromHex(parts[1]),
                    parts[2].split(Separators.LEVEL2)
                        .filter { it.isNotEmpty() }
                        .map { KVEntryReference(it, DESERIALIZER) }
                        .toTypedArray(),
                )
                "S" -> CPHamtSingle(
                    parts[1].toInt(),
                    longFromHex(parts[2]),
                    KVEntryReference(parts[3], DESERIALIZER),
                )
                else -> throw RuntimeException("Unknown type: " + parts[0] + ", input: " + input)
            }
            data.isWritten = true
            return data
        }
    }
}

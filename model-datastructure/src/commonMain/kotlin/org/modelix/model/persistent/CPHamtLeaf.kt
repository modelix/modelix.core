package org.modelix.model.persistent

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.asObservable
import com.badoo.reaktive.maybe.asSingle
import com.badoo.reaktive.maybe.maybeOf
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.maybe.toMaybe
import com.badoo.reaktive.maybe.toMaybeNotNull
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.concatWith
import com.badoo.reaktive.observable.flatMapMaybe
import com.badoo.reaktive.observable.observableDefer
import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.observableOfEmpty
import com.badoo.reaktive.single.asObservable
import com.badoo.reaktive.single.flatMapMaybe
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.streams.orNull

class CPHamtLeaf(
    val key: Long,
    val value: KVEntryReference<CPNode>,
) : CPHamtNode() {
    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf(value)

    override fun serialize(): String {
        return """L/${longToHex(key)}/${value.getHash()}"""
    }

    override fun put(key: Long, value: KVEntryReference<CPNode>?, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) {
            if (value?.getHash() == this.value.getHash()) {
                this.toMaybe()
            } else {
                create(key, value).toMaybeNotNull()
            }
        } else {
            createEmptyNode()
                .put(this.key, this.value, shift, store)
                .asSingle { createEmptyNode() }
                .flatMapMaybe { it.put(key, value, shift, store) }
        }
    }

    override fun putAll(entries: List<Pair<Long, KVEntryReference<CPNode>?>>, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        return if (entries.size == 1) {
            val entry = entries.single()
            put(entry.first, entry.second, shift, store)
        } else {
            val newEntries = if (entries.any { it.first == this.key }) entries else entries + (this.key to this.value)
            createEmptyNode().putAll(newEntries, shift, store)
        }
    }

    override fun remove(key: Long, shift: Int, store: IAsyncObjectStore): Maybe<CPHamtNode> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) {
            maybeOfEmpty()
        } else {
            this.toMaybe()
        }
    }

    override fun get(key: Long, shift: Int, store: IAsyncObjectStore): Maybe<KVEntryReference<CPNode>> {
        require(shift <= CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL) { "$shift > ${CPHamtNode.MAX_SHIFT + CPHamtNode.BITS_PER_LEVEL}" }
        return if (key == this.key) maybeOf(value) else maybeOfEmpty()
    }

    override fun getAll(
        keys: LongArray,
        shift: Int,
        store: IAsyncObjectStore,
    ): Observable<Pair<Long, KVEntryReference<CPNode>?>> {
        return if (keys.contains(this.key)) observableOf(key to value) else observableOfEmpty()
    }

    override fun getEntries(store: IAsyncObjectStore): Observable<Pair<Long, KVEntryReference<CPNode>>> {
        return observableOf(key to value)
    }

    override fun getChanges(oldNode: CPHamtNode?, shift: Int, store: IAsyncObjectStore, changesOnly: Boolean): Observable<MapChangeEvent> {
        return if (oldNode === this || hash == oldNode?.hash) {
            observableOfEmpty()
        } else if (changesOnly) {
            if (oldNode != null) {
                oldNode.get(key, shift, store).orNull().flatMapMaybe { oldValue ->
                    if (oldValue != null && value != oldValue) maybeOf(EntryChangedEvent(key, oldValue, value)) else maybeOfEmpty()
                }.asObservable()
            } else {
                observableOfEmpty()
            }
        } else {
            var oldValue: KVEntryReference<CPNode>? = null

            oldNode!!.getEntries(store).flatMapMaybe { (k: Long, v: KVEntryReference<CPNode>) ->
                if (k == key) {
                    oldValue = v
                    maybeOfEmpty<EntryRemovedEvent>()
                } else {
                    maybeOf(EntryRemovedEvent(k, v))
                }
            }.concatWith(
                observableDefer {
                    val oldValue = oldValue
                    if (oldValue == null) {
                        observableOf(EntryAddedEvent(key, value))
                    } else if (oldValue != value) {
                        observableOf(EntryChangedEvent(key, oldValue, value))
                    } else {
                        observableOfEmpty()
                    }
                },
            )
        }
    }

    override fun objectDiff(oldObject: IKVValue?, shift: Int, store: IAsyncObjectStore): Observable<IKVValue> {
        return when (oldObject) {
            is CPHamtLeaf -> {
                if (this.hash == oldObject.hash) {
                    observableOfEmpty()
                } else {
                    observableOf(this).concatWith(value.getValue(store).asObservable())
                }
            }
            is CPHamtInternal, is CPHamtSingle -> {
                oldObject.get(key, shift, store).orNull().flatMapMaybe { oldValue ->
                    if (oldValue?.getHash() == value.getHash()) {
                        maybeOfEmpty()
                    } else {
                        maybeOf(this)
                    }
                }.asObservable()
            }
            else -> observableOf(this)
        }
    }

    companion object {
        fun create(key: Long, value: KVEntryReference<CPNode>?): CPHamtLeaf? {
            if (value == null) return null
            return CPHamtLeaf(key, value)
        }
    }
}

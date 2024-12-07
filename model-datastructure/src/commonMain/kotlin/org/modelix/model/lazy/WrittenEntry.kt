package org.modelix.model.lazy

import com.badoo.reaktive.maybe.asSingleOrError
import com.badoo.reaktive.single.Single
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.toObjectHash
import org.modelix.model.persistent.IKVValue

class WrittenEntry<E : IKVValue>(
    private val hash: String,
    private val deserializer: (String) -> E,
) : IKVEntryReference<E> {
    override fun getHash(): String = hash

    override fun getValue(store: IDeserializingKeyValueStore): E {
        return store.get(hash, deserializer)
            ?: throw RuntimeException("Entry $hash not found")
    }

    override fun getValue(store: IAsyncObjectStore): Single<E> {
        return store.get(toObjectHash()).asSingleOrError { NoSuchElementException("Entry not found: $this") }
    }

    override fun getUnwrittenValue(): E {
        throw UnsupportedOperationException("Value is already written to the server: $hash")
    }

    override fun isWritten(): Boolean = true

    override fun write(store: IDeserializingKeyValueStore) {}

    override fun getDeserializer(): (String) -> E = deserializer
}

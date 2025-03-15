package org.modelix.model.lazy

import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.toObjectHash
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream

class WrittenEntry<E : IKVValue>(
    private val hash: String,
    private val deserializer: (String) -> E,
) : IKVEntryReference<E> {
    override fun getHash(): String = hash

    override fun getValue(store: IDeserializingKeyValueStore): E {
        return store.get(hash, deserializer)
            ?: throw MissingEntryException("Entry $hash not found")
    }

    override fun getValue(store: IAsyncObjectStore): IStream.One<E> {
        return store.get(toObjectHash()).exceptionIfEmpty { MissingEntryException("Entry not found: $this") }
    }

    override fun getUnwrittenValue(): E {
        throw UnsupportedOperationException("Value is already written to the server: $hash")
    }

    override fun isWritten(): Boolean = true

    override fun write(store: IDeserializingKeyValueStore) {}

    override fun getDeserializer(): (String) -> E = deserializer

    override fun toString(): String {
        return hash
    }
}

class MissingEntryException(val hash: String) : NoSuchElementException("Entry not found: $hash")

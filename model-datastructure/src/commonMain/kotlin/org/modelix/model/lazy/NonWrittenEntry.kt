package org.modelix.model.lazy

import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.IStream

class NonWrittenEntry<E : IKVValue> : IKVEntryReference<E> {
    private val hash: String
    private val deserialized: E

    private constructor(hash: String, deserialized: E) {
        this.hash = hash
        this.deserialized = deserialized
    }

    constructor(deserialized: E) : this(deserialized.hash, deserialized)

    override fun isWritten() = deserialized.isWritten

    override fun getHash(): String = hash

    override fun getValue(store: IDeserializingKeyValueStore): E = getDeserialized()
    override fun getValue(store: IAsyncObjectStore): IStream.One<E> = IStream.of(getDeserialized())
    override fun tryGetValue(store: IAsyncObjectStore): IStream.One<E> = IStream.of(getDeserialized())
    override fun getUnwrittenValue(): E = getDeserialized()

    fun getSerialized(): String = getDeserialized().serialize()

    fun getDeserialized(): E = deserialized

    override fun getDeserializer(): (String) -> E = getDeserialized().getDeserializer() as (String) -> E

    override fun write(store: IDeserializingKeyValueStore) {
        if (!deserialized.isWritten) {
            deserialized.getReferencedEntries().forEach { it.write(store) }
            store.put(hash, deserialized, getSerialized())
            deserialized.isWritten = true
        }
    }

    override fun toString(): String {
        return hash
    }
}

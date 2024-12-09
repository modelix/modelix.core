package org.modelix.model.lazy

import com.badoo.reaktive.single.Single
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.persistent.IKVValue

class KVEntryReference<out E : IKVValue>(private var writtenOrUnwrittenReference: IKVEntryReference<E>) : IKVEntryReference<E> {

    init {
        if (writtenOrUnwrittenReference is KVEntryReference) throw IllegalArgumentException()
    }

    constructor(hash: String, deserializer: (String) -> E) : this(WrittenEntry(hash, deserializer))
    constructor(deserialized: E) : this(if (deserialized.isWritten) WrittenEntry(deserialized.hash, deserialized.getDeserializer() as (String) -> E) else NonWrittenEntry(deserialized))

    override fun isWritten(): Boolean {
        val r = writtenOrUnwrittenReference
        return !(r is NonWrittenEntry && !r.isWritten())
    }

    override fun write(store: IDeserializingKeyValueStore) {
        val currentRef = writtenOrUnwrittenReference
        if (currentRef is NonWrittenEntry) {
            val deserializer = currentRef.getDeserializer()
            val hash = currentRef.getHash()
            currentRef.write(store)
            writtenOrUnwrittenReference = WrittenEntry(hash, deserializer)
        }
    }

    override fun getHash(): String = writtenOrUnwrittenReference.getHash()
    override fun getValue(store: IDeserializingKeyValueStore): E = writtenOrUnwrittenReference.getValue(store)
    override fun getValue(store: IAsyncObjectStore): Single<E> = writtenOrUnwrittenReference.getValue(store)
    override fun getUnwrittenValue(): E = writtenOrUnwrittenReference.getUnwrittenValue()
    override fun getDeserializer(): (String) -> E = writtenOrUnwrittenReference.getDeserializer()

    override fun toString(): String {
        return getHash()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is KVEntryReference<*>) return false
        return other.getHash() == getHash()
    }

    override fun hashCode(): Int {
        return getHash().hashCode()
    }
}

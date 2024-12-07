package org.modelix.model.persistent

import org.modelix.model.lazy.KVEntryReference

/**
 * Serializable object that can be stored in a key value store
 */
interface IKVValue {
    var isWritten: Boolean
    fun serialize(): String
    val hash: String
    fun getDeserializer(): (String) -> IKVValue
    fun getReferencedEntries(): List<KVEntryReference<IKVValue>>
}

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.persistent.IKVValue
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.persistent.ReadOnlyMapBasedStore
import org.modelix.model.persistent.getUpcastedDeserializer
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * This class enables a persistent data structure to be replicated and partially loaded.
 *
 * A persistent variant of a data structure is usually implemented by organising the data in a tree shaped object graph.
 * A change is applied by changing the necessary nodes and all its ancestor nodes.
 * The new root node of that tree represents the new version of the data structure and reusing/sharing the unchanged
 * subtrees of the old version makes these logical copies memory efficient.
 *
 * Storing such a persistent data structure is done by serializing it into hash tree (merkle tree).
 * IKVValue are the objects of the data structure. KVEntryReference is used to reference other objects.
 * Each object is identified by the SHA-256 hash over its serialized form.
 *
 * Each object can be loaded and unloaded individually.
 */
class KVEntryReference<out E : IKVValue> private constructor(
    private val hash: String,
    private val deserializer: IDeserializer<E>,
    private var loadedObject: E?,
    private var written: Boolean,
) : IKVEntryReference<E> {

    @Deprecated("", ReplaceWith("KVEntryReference.fromHash(hash, deserializer)"))
    constructor(hash: String, deserializer: IDeserializer<E>) : this(hash, deserializer, null, true)

    @Deprecated("", ReplaceWith("deserialized.ref()"))
    constructor(deserialized: E) : this(deserialized.hash, deserialized.getUpcastedDeserializer(), deserialized, false)

    fun isWritten(): Boolean {
        return written
    }

    @Deprecated("use write(IKeyValueStore)")
    override fun write(store: IDeserializingKeyValueStore) {
        write(store.keyValueStore)
    }

    fun write(store: IKeyValueStore) {
        if (written) return
        val obj = loadedObject ?: return
        // if (obj.isWritten) return
        obj.getReferencedEntries().forEach { it.write(store) }
        store.put(hash, obj.serialize())
        written = true
    }

    fun load(bulkQuery: IBulkQuery, reusableObject: KVEntryReference<*>?) {
        if (reusableObject != null && reusableObject.hash == hash) {
            loadedObject = reusableObject.getValueIfLoaded()
                ?.let { deserializer.type.safeCast(it) }
                ?.also { written = true }
        } else {
            loadRecursive(bulkQuery)
        }
    }

    fun loadRecursive(bulkQuery: IBulkQuery) {
        loadObject(bulkQuery).onSuccess { obj ->
            obj?.getReferencedEntries()?.forEach { it.loadRecursive(bulkQuery) }
        }
    }

    fun loadObject(bulkQuery: IBulkQuery): IBulkQuery.Value<E?> {
        return loadedObject?.let { bulkQuery.constant(it) } ?: bulkQuery.get(this).also {
            it.onSuccess { obj ->
                loadedObject = obj ?: throw NoSuchElementException("Entry not found for hash: $hash")
                written = true
            }
        }
    }

    fun unload() {
        // TODO this can cause unexpected ObjectNotLoadedException
        check(written) { "Call write() first" }
        loadedObject = null
    }

    override fun getHash(): String = hash

    fun getValueIfLoaded(): E? {
        return loadedObject
    }

    override fun getValue(store: IDeserializingKeyValueStore): E {
        return loadedObject ?: store.get(hash, deserializer.asFunction()) ?: throw ObjectNotLoadedException(hash)
    }
    override fun getDeserializer(): (String) -> E = deserializer.asFunction()

    override fun visitAll(bulkQuery: IBulkQuery, visitor: (String, IKVValue) -> Unit) {
        fun visitObject(obj: E) {
            visitor(hash, obj)
            obj.getReferencedEntries().forEach { it.visitAll(bulkQuery, visitor) }
        }

        val obj = loadedObject
        if (obj == null) {
            bulkQuery.get(this).onSuccess {
                if (it == null) throw ObjectNotLoadedException(hash)
                visitObject(it)
            }
        } else {
            visitObject(obj)
        }
    }

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

    interface IDeserializer<out E : IKVValue> {
        val type: KClass<out E>
        fun deserialize(serialized: String): E
        fun asFunction(): (String) -> E

        companion object {
            fun <T : IKVValue> create(type: KClass<T>, deserializer: (String) -> T): IDeserializer<T> {
                return DeserializerFromLambda(type, deserializer)
            }
        }

        private class DeserializerFromLambda<E : IKVValue>(override val type: KClass<E>, val deserializer: (String) -> E) : IDeserializer<E> {
            override fun deserialize(serialized: String): E = deserializer(serialized)
            override fun asFunction(): (String) -> E = deserializer
        }
    }

    companion object {
        fun <T : IKVValue> forObject(obj: T): KVEntryReference<T> {
            return obj.ref as KVEntryReference<T>? ?: create(obj, false)
        }

        private fun <T : IKVValue> create(obj: T, isWritten: Boolean): KVEntryReference<T> {
            check(obj.ref == null)
            val ref = KVEntryReference(obj.hash, obj.getUpcastedDeserializer(), obj, isWritten)
            obj.ref = ref
            return ref
        }

        fun <T : IKVValue> fromHash(hash: String, deserializer: IDeserializer<T>): KVEntryReference<T> {
            return KVEntryReference(hash, deserializer, null, true)
        }

        fun <T : IKVValue> wasDeserialized(obj: T) {
            obj.ref = create(obj, true)
        }
    }
}

@Suppress("DEPRECATION", "UNCHECKED_CAST")
fun <T : IKVValue> T.ref(): KVEntryReference<T> = KVEntryReference.forObject(this)

fun <T : IKVValue> T.wasDeserialized() = also {
    KVEntryReference.wasDeserialized(it)
}

fun KVEntryReference<*>.load(objects: Map<String, String>) {
    loadRecursive(ReadOnlyMapBasedStore(objects).let { it.newBulkQuery(NonCachingObjectStore(it)) })
}

fun KVEntryReference<*>.writeToMap(): Map<String, String> {
    return MapBasedStore().also { write(it) }.entries.associate { it.key to it.value!! }
}

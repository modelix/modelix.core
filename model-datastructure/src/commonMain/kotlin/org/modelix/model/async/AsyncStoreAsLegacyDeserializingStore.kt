/*
 * Copyright (c) 2024.
 *
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

package org.modelix.model.async

import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.toList
import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.IKVEntryReference
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.executeSynchronous
import org.modelix.streams.getSynchronous
import org.modelix.streams.orNull

private val ILLEGAL_DESERIALIZER: (String) -> Any = { error("deserialization not expected") }

@Deprecated("use IAsyncStore")
class AsyncStoreAsLegacyDeserializingStore(val store: IAsyncObjectStore) : IDeserializingKeyValueStore {

    override fun getAsyncStore(): IAsyncObjectStore {
        return store
    }

    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        val ref = ObjectHash(hash, deserializer as ((String) -> IKVValue))
        return store.get(ref).orNull().getSynchronous() as T?
    }

    override val keyValueStore: IKeyValueStore
        get() = store.getLegacyKeyValueStore()

    override fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T? {
        return store.getIfCached(ObjectHash(hash, deserializer as ((String) -> IKVValue))) as T?
    }

    override fun <T> getAll(hash: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        return store.getAllAsStream(hash.asObservable().map { hash -> ObjectHash(hash, { deserializer(hash, it) as Any }) })
            .map { it.second as T }.toList().getSynchronous()
    }

    override fun put(hash: String, deserialized: Any, serialized: String) {
        store.putAll(mapOf(ObjectHash(hash, ILLEGAL_DESERIALIZER) to deserialized as IKVValue)).executeSynchronous()
    }

    override fun <T : IKVValue> getAll(
        regular: List<IKVEntryReference<T>>,
        prefetch: List<IKVEntryReference<T>>,
    ): Map<String, T?> {
        return store.getAllAsMap((regular + prefetch).map { it.toObjectHash() }).getSynchronous().entries.associate { it.key.hash to it.value as T? }
    }

    override fun prefetch(hash: String) {
        throw UnsupportedOperationException("prefetch is deprecated")
    }
}

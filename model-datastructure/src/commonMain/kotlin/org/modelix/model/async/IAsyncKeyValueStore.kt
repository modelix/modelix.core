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

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.defaultIfEmpty
import com.badoo.reaktive.single.blockingGet
import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.BulkQueryConfiguration
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.IKVEntryReference
import org.modelix.model.lazy.IPrefetchGoal
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue

interface IAsyncKeyValueStore {
    suspend fun getAll(keys: List<String>): Map<String, String?>
    suspend fun putAll(entries: Map<String, String?>)
}

interface IAsyncObjectStore {
    fun <T : IKVValue> getIfCached(key: IKVEntryReference<T>): T?
    fun <T : IKVValue> get(key: IKVEntryReference<T>): Maybe<T>
    suspend fun getAll(keys: List<IKVEntryReference<*>>): Map<IKVEntryReference<*>, Any?>
    suspend fun putAll(entries: Map<IKVEntryReference<*>, Any?>)
}

class BulkQueryAsAsyncStore(val bulkQuery: IBulkQuery): IAsyncObjectStore {

    override fun <T : IKVValue> get(key: IKVEntryReference<T>): Maybe<T> {
        return bulkQuery.query(key as KVEntryReference<T>)
    }

    override suspend fun getAll(keys: List<IKVEntryReference<*>>): Map<IKVEntryReference<*>, Any?> {
        TODO("Not yet implemented")
    }

    override suspend fun putAll(entries: Map<IKVEntryReference<*>, Any?>) {
        TODO("Not yet implemented")
    }

    override fun <T : IKVValue> getIfCached(key: IKVEntryReference<T>): T? {
        TODO("Not yet implemented")
    }
}

class AsyncStoreAsBulkQuery(val store: IAsyncObjectStore) : IBulkQuery {
    override fun offerPrefetch(key: IPrefetchGoal) {
        TODO("Not yet implemented")
    }

    override fun executeQuery() {
        TODO("Not yet implemented")
    }

    override fun <T : IKVValue> query(hash: KVEntryReference<T>): Maybe<T> {
        return store.get(hash)
    }
}

fun IBulkQuery.asStore(): IAsyncObjectStore = BulkQueryAsAsyncStore(this)
fun IAsyncObjectStore.asBulkQuery(): IBulkQuery {
    return when (this) {
        is BulkQueryAsAsyncStore -> bulkQuery
        else -> AsyncStoreAsBulkQuery(this)
    }
}

class SynchronousStoreAsAsyncStore(val store: IDeserializingKeyValueStore, val bulkQuery: IBulkQuery): IAsyncObjectStore {

    override fun <T : IKVValue> get(key: IKVEntryReference<T>): Maybe<T> {
        return bulkQuery.query(key as KVEntryReference<T>)
    }

    override fun <T : IKVValue> getIfCached(key: IKVEntryReference<T>): T? {
        return store.getIfCached(key.getHash(), key.getDeserializer(), false)
    }

    override suspend fun getAll(keys: List<IKVEntryReference<*>>): Map<IKVEntryReference<*>, Any?> {
        val refMap = keys.associateBy { it.getHash() }
        return store.getAll(keys, emptyList()).entries.associate { refMap[it.key]!! to it.value }
    }

    override suspend fun putAll(entries: Map<IKVEntryReference<*>, Any?>) {
        TODO("Not yet implemented")
    }
}

class AsyncStoreAsStore(val store: IAsyncObjectStore) : IDeserializingKeyValueStore {
    override fun getAsyncStore(): IAsyncObjectStore {
        return store
    }

    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        deserializer as ((String) -> IKVValue)
        return store.get(KVEntryReference(hash, deserializer)).defaultIfEmpty(null).blockingGet() as T?
    }

    override val keyValueStore: IKeyValueStore
        get() = TODO("Not yet implemented")

    override fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T? {
        TODO("Not yet implemented")
    }

    override fun <T> getAll(hash: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        TODO("Not yet implemented")
    }

    override fun put(hash: String, deserialized: Any, serialized: String) {
        TODO("Not yet implemented")
    }

    override fun prefetch(hash: String) {
        TODO("Not yet implemented")
    }

    override fun <T : IKVValue> getAll(
        regular: List<IKVEntryReference<T>>,
        prefetch: List<IKVEntryReference<T>>,
    ): Map<String, T?> {
        return super.getAll(regular, prefetch)
    }

    override fun newBulkQuery(): IBulkQuery {
        return super.newBulkQuery()
    }

    override fun newBulkQuery(wrapper: IDeserializingKeyValueStore, config: BulkQueryConfiguration?): IBulkQuery {
        return super.newBulkQuery(wrapper, config)
    }
}

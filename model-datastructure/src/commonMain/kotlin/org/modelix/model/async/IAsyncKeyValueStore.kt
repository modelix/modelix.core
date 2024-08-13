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

import org.modelix.kotlin.utils.IMonoFlow
import org.modelix.model.api.async.IAsyncValue
import org.modelix.model.api.async.checkNotNull
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IKVEntryReference
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.IKVValue

interface IAsyncKeyValueStore {
    suspend fun getAll(keys: List<String>): Map<String, String?>
    suspend fun putAll(entries: Map<String, String?>)
}

interface IAsyncObjectStore {
    fun <T : IKVValue> get(key: IKVEntryReference<T>): IAsyncValue<T?>
    fun <T : IKVValue> getAsFlow(key: IKVEntryReference<T>): IMonoFlow<T>
    suspend fun getAll(keys: List<IKVEntryReference<*>>): Map<IKVEntryReference<*>, Any?>
    suspend fun putAll(entries: Map<IKVEntryReference<*>, Any?>)
}

class AsyncObjectStoreAdapter(val bulkQuery: IBulkQuery): IAsyncObjectStore {
    override fun <T : IKVValue> get(key: IKVEntryReference<T>): IAsyncValue<T?> {
        return bulkQuery.query(key as KVEntryReference<T>)
    }

    override fun <T : IKVValue> getAsFlow(key: IKVEntryReference<T>): IMonoFlow<T> {
        return get(key).checkNotNull { "Entry not found: $key" }.asFlow()
    }

    override suspend fun getAll(keys: List<IKVEntryReference<*>>): Map<IKVEntryReference<*>, Any?> {
        TODO("Not yet implemented")
    }

    override suspend fun putAll(entries: Map<IKVEntryReference<*>, Any?>) {
        TODO("Not yet implemented")
    }
}

fun IBulkQuery.asStore(): IAsyncObjectStore = AsyncObjectStoreAdapter(this)
fun IAsyncObjectStore.asBulkQuery(): IBulkQuery = (this as AsyncObjectStoreAdapter).bulkQuery
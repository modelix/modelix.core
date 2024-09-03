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

package org.modelix.model.server.store

import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.completable.completableOfEmpty
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.maybeOfEmpty
import com.badoo.reaktive.maybe.toMaybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.toSingle
import org.modelix.model.IKeyValueStore
import org.modelix.model.async.AsyncStoreAsLegacyDeserializingStore
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.async.ObjectHash
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.persistent.IKVValue
import org.modelix.streams.getSynchronous

class StoreClientAsAsyncStore(val store: IStoreClient) : IAsyncObjectStore {
    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return StoreClientAsKeyValueStore(store)
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return AsyncStoreAsLegacyDeserializingStore(this)
    }

    override fun <T : Any> getIfCached(key: ObjectHash<T>): T? {
        return null
    }

    override fun <T : Any> get(key: ObjectHash<T>): Maybe<T> {
        val value = store.get(key.hash) ?: return maybeOfEmpty()
        return key.deserializer(value).toMaybe()
    }

    override fun getAllAsStream(keys: Observable<ObjectHash<*>>): Observable<Pair<ObjectHash<*>, Any?>> {
        val keysList = keys.toList().getSynchronous()
        val keysMap = keysList.associateBy { it.hash }
        val serializedValues = store.getAll(keysMap.keys)
        return serializedValues.map {
            val ref = keysMap[it.key]!!
            ref to it.value?.let { ref.deserializer(it) }
        }.asObservable()
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): Single<Map<ObjectHash<*>, Any?>> {
        val keysMap = keys.associateBy { it.hash }
        val serializedValues = store.getAll(keysMap.keys)
        return serializedValues.map {
            val ref = keysMap[it.key]!!
            ref to it.value?.let { ref.deserializer(it) }
        }.toMap().toSingle()
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): Completable {
        store.putAll(entries.entries.associate { it.key.hash to it.value.serialize() })
        return completableOfEmpty()
    }
}

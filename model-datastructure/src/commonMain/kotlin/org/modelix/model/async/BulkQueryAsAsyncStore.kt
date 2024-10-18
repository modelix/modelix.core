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

import com.badoo.reaktive.completable.Completable
import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.single.Single
import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.persistent.IKVValue

@Deprecated("Use BulkAsyncStore")
class BulkQueryAsAsyncStore(val store: IDeserializingKeyValueStore, val bulkQuery: IBulkQuery) : IAsyncObjectStore {
    override fun getLegacyKeyValueStore(): IKeyValueStore {
        return store.keyValueStore
    }

    override fun getLegacyObjectStore(): IDeserializingKeyValueStore {
        return store
    }

    override fun <T : Any> get(key: ObjectHash<T>): Maybe<T> {
        return bulkQuery.query(key.toKVEntryReference())
    }

    override fun getAllAsMap(keys: List<ObjectHash<*>>): Single<Map<ObjectHash<*>, Any?>> {
        throw UnsupportedOperationException()
    }

    override fun getAllAsStream(keys: Observable<ObjectHash<*>>): Observable<Pair<ObjectHash<*>, Any?>> {
        throw UnsupportedOperationException()
    }

    override fun putAll(entries: Map<ObjectHash<*>, IKVValue>): Completable {
        throw UnsupportedOperationException()
    }

    override fun <T : Any> getIfCached(key: ObjectHash<T>): T? {
        throw UnsupportedOperationException()
    }
}

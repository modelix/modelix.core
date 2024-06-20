/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore
import org.modelix.model.persistent.IKVValue

interface IDeserializingKeyValueStore {
    fun newBulkQuery(): IBulkQuery = newBulkQuery(this)
    fun newBulkQuery(wrapper: IDeserializingKeyValueStore, config: BulkQueryConfiguration? = null): IBulkQuery = keyValueStore.newBulkQuery(wrapper, config ?: BulkQueryConfiguration())
    val keyValueStore: IKeyValueStore
    operator fun <T> get(hash: String, deserializer: (String) -> T): T?
    fun <T> getIfCached(hash: String, deserializer: (String) -> T, isPrefetch: Boolean): T?
    fun <T> getAll(hash: Iterable<String>, deserializer: (String, String) -> T): Iterable<T>
    fun <T : IKVValue> getAll(regular: List<IKVEntryReference<T>>, prefetch: List<IKVEntryReference<T>>): Map<String, T?> = throw UnsupportedOperationException()
    fun put(hash: String, deserialized: Any, serialized: String)

    @Deprecated("BulkQuery is now responsible for prefetching")
    fun prefetch(hash: String)
}

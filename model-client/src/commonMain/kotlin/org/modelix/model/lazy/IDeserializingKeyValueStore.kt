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

import kotlinx.coroutines.flow.*
import org.modelix.model.IKeyValueStore
import org.modelix.modelql.core.batchTransform

interface IDeserializingKeyValueStore {
    val keyValueStore: IKeyValueStore
    operator fun <T> get(hash: String, deserializer: (String) -> T): T?

    @Deprecated("use getAll with KeyAndDeserializer")
    fun <T> getAll(hash: Iterable<String>, deserializer: (String, String) -> T): Iterable<T>
    fun put(hash: String, deserialized: Any, serialized: String)
    fun prefetch(hash: String)

    fun <T> getAll(hash: List<KeyAndDeserializer<T>>): List<Pair<KeyAndDeserializer<T>, T?>> {
        if (hash.isEmpty()) return emptyList()
        val hash2entry = hash.associateBy { it.key }
        val results = getAll(hash.map { it.key }) { key, value ->
            hash2entry[key]!!.deserializer(value)
        }.toList()
        return hash.zip(results)
    }

    fun <T> get(keys: Flow<KeyAndDeserializer<T>>): Flow<Pair<KeyAndDeserializer<T>, T?>> {
        return keys.batchTransform(5000) { getAll(it) }
    }
}

data class KeyAndDeserializer<E>(val key: String, val deserializer: (String) -> E)

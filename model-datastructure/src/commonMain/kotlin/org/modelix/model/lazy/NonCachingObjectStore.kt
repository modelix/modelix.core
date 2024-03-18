/*
 * Copyright (c) 2023.
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

package org.modelix.model.lazy

import org.modelix.model.IKeyValueStore

class NonCachingObjectStore(override val keyValueStore: IKeyValueStore) : IDeserializingKeyValueStore {

    override fun <T> getAll(hashes_: Iterable<String>, deserializer: (String, String) -> T): Iterable<T> {
        val hashes = hashes_.toList()
        val serialized: Map<String, String?> = keyValueStore.getAll(hashes_)
        return hashes.map { hash ->
            val value = checkNotNull(serialized[hash]) { "Entry not found: $hash" }
            deserializer(hash, value)
        }
    }

    override fun <T> get(hash: String, deserializer: (String) -> T): T? {
        return keyValueStore.get(hash)?.let(deserializer)
    }

    override fun put(hash: String, deserialized: Any, serialized: String) {
        keyValueStore.put(hash, serialized)
    }

    override fun prefetch(hash: String) {
        keyValueStore.prefetch(hash)
    }
}

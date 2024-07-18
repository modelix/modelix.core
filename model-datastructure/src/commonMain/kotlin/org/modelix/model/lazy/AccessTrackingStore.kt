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

package org.modelix.model.lazy

import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore

/**
 * Internal API.
 * Only public for tests.
 */
class AccessTrackingStore(val store: IKeyValueStore) : IKeyValueStore {
    val accessedEntries: MutableMap<String, String?> = LinkedHashMap()

    override fun newBulkQuery(deserializingCache: IDeserializingKeyValueStore, config: BulkQueryConfiguration): IBulkQuery {
        return store.newBulkQuery(deserializingCache, config)
    }

    override fun get(key: String): String? {
        val value = store.get(key)
        accessedEntries.put(key, value)
        return value
    }

    override fun getIfCached(key: String): String? {
        val value = store.getIfCached(key)
        if (value != null) {
            accessedEntries[key] = value
        }
        return value
    }

    override fun put(key: String, value: String?) {
        TODO("Not yet implemented")
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val entries = store.getAll(keys)
        accessedEntries.putAll(entries)
        return entries
    }

    override fun putAll(entries: Map<String, String?>) {
        TODO("Not yet implemented")
    }

    override fun prefetch(key: String) {
        TODO("Not yet implemented")
    }

    override fun listen(key: String, listener: IKeyListener) {
        TODO("Not yet implemented")
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        TODO("Not yet implemented")
    }

    override fun getPendingSize(): Int {
        TODO("Not yet implemented")
    }
}

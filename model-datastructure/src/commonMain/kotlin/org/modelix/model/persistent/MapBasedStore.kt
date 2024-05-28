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

package org.modelix.model.persistent

import org.modelix.kotlin.utils.createMemoryEfficientMap
import org.modelix.kotlin.utils.toSynchronizedMap
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore
import org.modelix.model.lazy.BulkQueryConfiguration
import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.NonBulkQuery

@Deprecated("Use MapBasedStore, without a typo.", ReplaceWith("MapBasedStore"))
open class MapBaseStore : MapBasedStore()

open class MapBasedStore : IKeyValueStore {
    private val map = createMemoryEfficientMap<String?, String?>().toSynchronizedMap()
    override fun get(key: String): String? {
        return map[key]
    }

    override fun getIfCached(key: String): String? {
        return get(key)
    }

    override fun newBulkQuery(deserializingCache: IDeserializingKeyValueStore, config: BulkQueryConfiguration): IBulkQuery {
        // This implementation doesn't benefit from bulk queries. The NonBulkQuery has a lower performance overhead.
        return NonBulkQuery(deserializingCache)
    }

    override fun getPendingSize(): Int = 0

    override fun put(key: String, value: String?) {
        putAll(mapOf(key to value))
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val result: MutableMap<String, String?> = LinkedHashMap()
        for (key in keys) {
            result[key] = map[key]
        }
        return result
    }

    override fun putAll(entries: Map<String, String?>) {
        map.putAll(entries)
    }

    override fun prefetch(key: String) {}
    val entries: Iterable<Map.Entry<String?, String?>>
        get() = map.entries

    override fun listen(key: String, listener: IKeyListener) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        throw UnsupportedOperationException()
    }
}

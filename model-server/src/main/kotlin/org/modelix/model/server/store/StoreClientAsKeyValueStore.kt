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
package org.modelix.model.server.store

import org.apache.commons.collections4.IterableUtils
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore

class StoreClientAsKeyValueStore(val store: IStoreClient) : IKeyValueStore {

    override fun get(key: String): String? {
        return store[key]
    }

    override fun getIfCached(key: String): String? {
        return null
    }

    override fun put(key: String, value: String?) {
        store.put(key, value)
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        val keyList = IterableUtils.toList(keys)
        val values = store.getAll(keyList)
        val result: MutableMap<String, String?> = LinkedHashMap()
        for (i in keyList.indices) {
            result[keyList[i]] = values[i]
        }
        return result
    }

    override fun putAll(entries: Map<String, String?>) {
        store.putAll(entries)
    }

    override fun prefetch(key: String) {
        throw UnsupportedOperationException()
    }

    override fun listen(key: String, listener: IKeyListener) {
        store.listen(key, listener)
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        store.removeListener(key, listener)
    }

    override fun getPendingSize(): Int {
        return 0
    }
}

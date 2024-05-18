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

package org.modelix.model

class LRUCache<K, V>(val maxSize: Int) {
    private val map: MutableMap<K, V> = LinkedHashMap()

    operator fun set(key: K, value: V) {
        map.remove(key)
        map[key] = value
        while (map.size > maxSize) map.remove(map.iterator().next().key)
    }

    operator fun get(key: K, updatePosition: Boolean = true): V? {
        if (!map.containsKey(key)) return null
        return map.get(key).also { value ->
            if (updatePosition) {
                map.remove(key)
                map[key] = value as V
            }
        }
    }

    fun remove(key: K) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }
}
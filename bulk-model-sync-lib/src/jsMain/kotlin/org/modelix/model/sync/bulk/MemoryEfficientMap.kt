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

package org.modelix.model.sync.bulk

actual class MemoryEfficientMap<KeyT, ValueT> {
    private val map: MutableMap<KeyT, ValueT> = mutableMapOf()

    actual operator fun set(key: KeyT, value: ValueT) = map.set(key, value)

    actual operator fun get(key: KeyT) = map[key]
}

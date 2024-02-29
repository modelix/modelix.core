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

/**
 * Built-in maps like [HashMap] are not the most memory efficient way of map.
 * A common issue is that entry objects for every item in the table are created.
 * [MemoryEfficientMap] is an internal implementation that we can use
 * when the memory overhead becomes too big.
 *
 * Java implementation is optimized to not create entry objects by using a map implementation from another library.
 * The JS implementation is not optimized yet because we did not invest time in finding a suitable library.
 *
 * [MemoryEfficientMap] is an internal abstraction.
 * The API is therefore kept minimal
 */
expect class MemoryEfficientMap<KeyT, ValueT>() {
    operator fun set(key: KeyT, value: ValueT)
    operator fun get(key: KeyT): ValueT?
}

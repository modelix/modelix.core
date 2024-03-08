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

package org.modelix.kotlin.utils

/**
 * Creates a mutable map with less memory overhead.
 * This is an internal API.
 *
 * Built-in maps like [HashMap] are not the not very memory efficient.
 * A common issue is that entry objects for every item in the table are created.
 * Java implementation is optimized to not create entry objects by using a map implementation from another library.
 * The JS implementation is not optimized yet because we did not invest time in finding a suitable library.
 *
 * We did not look into performance implications for storing and retrieving data.
 * Therefore, the memory efficient maps are used sparingly for only the very big maps.
 */
expect fun <K, V> createMemoryEfficientMap(): MutableMap<K, V>

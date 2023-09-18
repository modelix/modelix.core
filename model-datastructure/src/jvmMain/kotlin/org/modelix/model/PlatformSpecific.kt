/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model

import org.apache.commons.collections4.map.LRUMap
import java.util.Collections
import java.util.UUID

actual fun sleep(milliseconds: Long) {
    Thread.sleep(milliseconds)
}
actual fun bitCount(bits: Int): Int {
    return Integer.bitCount(bits)
}

actual fun <K, V> createLRUMap(size: Int): MutableMap<K, V> {
    return Collections.synchronizedMap(LRUMap<K, V>(size))
}

actual fun randomUUID(): String {
    return UUID.randomUUID().toString()
}

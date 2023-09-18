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

actual fun sleep(milliseconds: Long) {
    TODO("Not yet implemented")
}

actual fun bitCount(bits: Int): Int {
    var i = bits
    i -= (i ushr 1 and 0x55555555)
    i = (i and 0x33333333) + (i ushr 2 and 0x33333333)
    i = i + (i ushr 4) and 0x0f0f0f0f
    i += (i ushr 8)
    i += (i ushr 16)
    return i and 0x3f
}

actual fun <K, V> createLRUMap(size: Int): MutableMap<K, V> {
    return HashMap()
}

@Suppress("ClassName")
@JsModule("uuid")
@JsNonModule
external object uuid {
    fun v4(): String
}

actual fun randomUUID(): String {
    return uuid.v4()
}

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

import org.modelix.model.persistent.SerializationUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationUtilLongHexTest {

    @Test
    fun longToHex_maxValue() {
        assertEquals("7fffffffffffffff", SerializationUtil.longToHex(Long.MAX_VALUE))
    }

    @Test
    fun longFromHex_maxValue() {
        assertEquals(Long.MAX_VALUE, SerializationUtil.longFromHex("7fffffffffffffff"))
    }

    @Test
    fun longToHex_minValue() {
        assertEquals("8000000000000000", SerializationUtil.longToHex(Long.MIN_VALUE))
    }

    @Test
    fun longFromHex_minValue() {
        assertEquals(Long.MIN_VALUE, SerializationUtil.longFromHex("8000000000000000"))
    }

    @Test
    fun longToHex_minus1() {
        assertEquals("ffffffffffffffff", SerializationUtil.longToHex(-1L))
    }

    @Test
    fun longToHex_minus3() {
        assertEquals("fffffffffffffffd", SerializationUtil.longToHex(-3L))
    }

    @Test
    fun longFromHex_minus1() {
        assertEquals(-1L, SerializationUtil.longFromHex("ffffffffffffffff"))
    }

    @Test
    fun longToHex_0() {
        assertEquals("0", SerializationUtil.longToHex(0))
    }

    @Test
    fun longFromHex_0() {
        assertEquals(0, SerializationUtil.longFromHex("0"))
    }

    @Test
    fun longToHex_1() {
        assertEquals("1", SerializationUtil.longToHex(1))
    }

    @Test
    fun longFromHex_1() {
        assertEquals(1, SerializationUtil.longFromHex("1"))
    }

    @Test
    fun longToHex_msb() {
        assertEquals("8000000000000000", SerializationUtil.longToHex(1L shl 63))
    }

    @Test
    fun longFromHex_msb() {
        assertEquals(1L shl 63, SerializationUtil.longFromHex("8000000000000000"))
    }
}

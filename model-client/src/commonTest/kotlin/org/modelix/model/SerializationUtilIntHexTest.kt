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

class SerializationUtilIntHexTest {

    @Test
    fun intToHex_maxValue() {
        assertEquals("7fffffff", SerializationUtil.intToHex(Int.MAX_VALUE))
    }

    @Test
    fun intFromHex_maxValue() {
        assertEquals(Int.MAX_VALUE, SerializationUtil.intFromHex("7fffffff"))
    }

    @Test
    fun intToHex_minValue() {
        assertEquals("80000000", SerializationUtil.intToHex(Int.MIN_VALUE))
    }

    @Test
    fun intFromHex_minValue() {
        assertEquals(Int.MIN_VALUE, SerializationUtil.intFromHex("80000000"))
    }

    @Test
    fun intToHex_minus1() {
        assertEquals("ffffffff", SerializationUtil.intToHex(-1))
    }

    @Test
    fun intFromHex_minus1() {
        assertEquals(-1, SerializationUtil.intFromHex("ffffffff"))
    }

    @Test
    fun intFromHex_minus3() {
        assertEquals(-3, SerializationUtil.intFromHex("fffffffd"))
    }

    @Test
    fun intToHex_0() {
        assertEquals("0", SerializationUtil.intToHex(0))
    }

    @Test
    fun intFromHex_0() {
        assertEquals(0, SerializationUtil.intFromHex("0"))
    }

    @Test
    fun intToHex_1() {
        assertEquals("1", SerializationUtil.intToHex(1))
    }

    @Test
    fun intFromHex_1() {
        assertEquals(1, SerializationUtil.intFromHex("1"))
    }

    @Test
    fun intToHex_msb() {
        assertEquals("80000000", SerializationUtil.intToHex(1 shl 31))
    }

    @Test
    fun intFromHex_msb() {
        assertEquals(1 shl 31, SerializationUtil.intFromHex("80000000"))
    }

    // This value seems connected to an issue
    @Test
    fun intToHex965() {
        assertEquals("3c5", SerializationUtil.intToHex(965))
    }
}

/*
 * Copyright (c) 2023.
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

import org.modelix.model.bitCount
import org.modelix.model.randomUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun Char.assertIsHexDigit() {
    assertTrue("Character expected to be hexadecimal digit but is '$this'") { this in '0'..'9' || this in 'a'..'f' }
}

class PlatformSpecificTest {
    @Test
    fun test_random_uuid() {
        val res = randomUUID()
        // ex: 5499930c-bfe2-40c5-82d1-a3859a045081
        assertEquals(36, res.count())
        val separators = listOf(8, 13, 18, 23)
        for (i in 0 until res.count()) {
            if (i in separators) {
                assertEquals('-', res[i])
            } else {
                res[i].assertIsHexDigit()
            }
        }
    }

    @Test
    fun testBitCount() {
        fun logicalToPhysicalIndex(bitmap: Int, logicalIndex: Int): Int {
            return bitCount(bitmap and (1 shl logicalIndex) - 1)
        }
        assertEquals(4, logicalToPhysicalIndex(69239088, 21))
        assertEquals(7, logicalToPhysicalIndex(20200000, 21))
    }
}

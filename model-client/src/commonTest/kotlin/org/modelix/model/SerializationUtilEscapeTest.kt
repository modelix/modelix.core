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
import kotlin.test.assertFails

class SerializationUtilEscapeTest {

    @Test
    fun escape_null() {
        assertEquals("%00", SerializationUtil.escape(null))
    }

    @Test
    fun unescape_null() {
        assertEquals(null, SerializationUtil.unescape("%00"))
    }

    @Test
    fun escape_emptyString() {
        assertEquals("", SerializationUtil.escape(""))
    }

    @Test
    fun escape_space() {
        assertEquals("+", SerializationUtil.escape(" "))
    }

    @Test
    fun unescape_space() {
        assertEquals(" ", SerializationUtil.unescape("+"))
    }

    @Test
    fun unescape_emptyString() {
        assertEquals("", SerializationUtil.unescape(""))
    }

    @Test
    fun escape_percent() {
        assertEquals("%25", SerializationUtil.escape("%"))
    }

    @Test
    fun unescape_percent() {
        assertEquals("%", SerializationUtil.unescape("%25"))
    }

    @Test
    fun unescape_singlePercent() {
        assertFails { SerializationUtil.unescape("%") }
    }

    @Test
    fun unescape_doublePercent() {
        assertFails { SerializationUtil.unescape("%%") }
    }

    @Test
    fun escape_ascii() {
        assertEquals(
            "%21%22%23%24%25%26%27%28%29*%2B%2C-.%2F0123456789%3A%3B%3C%3D%3E%3F%40ABCDEFGHIJKLMNOPQRSTUVWXYZ%5B%5C%5D%5E_%60abcdefghijklmnopqrstuvwxyz%7B%7C%7D%7E",
            SerializationUtil.escape("""!"#${'$'}%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~"""),
        )
    }

    @Test
    fun unescape_ascii() {
        assertEquals(
            """!"#${'$'}%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~""",
            SerializationUtil.unescape("%21%22%23%24%25%26%27%28%29*%2B%2C-.%2F0123456789%3A%3B%3C%3D%3E%3F%40ABCDEFGHIJKLMNOPQRSTUVWXYZ%5B%5C%5D%5E_%60abcdefghijklmnopqrstuvwxyz%7B%7C%7D%7E"),
        )
    }

    @Test
    fun escape_unicode() {
        assertEquals("%E2%93%9C%E2%93%9E%E2%93%93%E2%93%94%E2%93%9B%E2%93%98%E2%93%A7", SerializationUtil.escape("ⓜⓞⓓⓔⓛⓘⓧ"))
    }

    @Test
    fun unescape_unicode() {
        assertEquals("ⓜⓞⓓⓔⓛⓘⓧ", SerializationUtil.unescape("%E2%93%9C%E2%93%9E%E2%93%93%E2%93%94%E2%93%9B%E2%93%98%E2%93%A7"))
    }
}

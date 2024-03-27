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

package org.modelix.model.server.api.v2

import io.ktor.util.cio.toByteArray
import io.ktor.util.cio.use
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.modelix.model.server.api.v2.VersionDeltaStreamV2.Companion.decodeVersionDeltaStreamV2
import org.modelix.model.server.api.v2.VersionDeltaStreamV2.Companion.encodeVersionDeltaStreamV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersionDeltaStreamV2Test {

    @Test
    fun parseStreamWithoutObjects() = runTest {
        val channel = ByteChannel()
        channel.use {
            encodeVersionDeltaStreamV2(channel, "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A", emptyFlow())
        }
        val versionDeltaStream = decodeVersionDeltaStreamV2(channel)

        assertEquals("CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A", versionDeltaStream.versionHash)
        assertEquals(emptyList(), versionDeltaStream.deltaObjects.toList())
    }

    @Test
    fun parseStreamWithObjects() = runTest {
        val channel = ByteChannel()
        channel.use {
            encodeVersionDeltaStreamV2(
                channel,
                "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A",
                flowOf(
                    "L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg",
                    "1/%00/0/%00///",
                ),
            )
        }

        val versionDeltaStream = decodeVersionDeltaStreamV2(channel)

        assertEquals("CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A", versionDeltaStream.versionHash)
        val expectedObjects = listOf("L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg", "1/%00/0/%00///")
        assertEquals(expectedObjects, versionDeltaStream.deltaObjects.toList())
    }

    @Test
    fun failToParseAnySubstringOfAValidEncoding() = runTest {
        val channel = ByteChannel()
        channel.use {
            encodeVersionDeltaStreamV2(
                channel,
                "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A",
                flowOf(
                    "L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg",
                    "1/%00/0/%00///",
                ),
            )
        }
        val fullByteArray = channel.toByteArray()

        for (newSize in fullByteArray.indices) {
            val subByteArray = fullByteArray.copyOf(newSize)
            val subChannel = ByteChannel()
            subChannel.use {
                subChannel.writeFully(subByteArray)
            }

            val exception = assertFailsWith<IllegalArgumentException> {
                decodeVersionDeltaStreamV2(subChannel).deltaObjects.toList()
            }
            assertTrue(exception.message == "Version hash missing." || exception.message == "Received incomplete response.")
        }
    }

    @Test
    fun failParsingStreamWithoutVersionHash() = runTest {
        val data = ""
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            decodeVersionDeltaStreamV2(channel).deltaObjects.toList()
        }
        assertEquals("Version hash missing.", exception.message)
    }

    @Test
    fun failParsingStreamWithIncompleteVersionHash() = runTest {
        val data = "CTVRw*a6KXJ4o7uzGlp"
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            decodeVersionDeltaStreamV2(channel).deltaObjects.toList()
        }
        assertEquals("Received incomplete response.", exception.message)
    }

    @Test
    fun failParsingStreamWithMissingDataAfterVersionHash() = runTest {
        val data = "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A\n"
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            decodeVersionDeltaStreamV2(channel).deltaObjects.toList()
        }
        assertEquals("Received incomplete response.", exception.message)
    }

    @Test
    fun failParsingStreamWithIncompleteObject() = runTest {
        val data = "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A\n" +
            "L/100000017/xioDt*mnraICBf48DpWkvvtl2K"
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            decodeVersionDeltaStreamV2(channel).deltaObjects.toList()
        }
        assertEquals("Received incomplete response.", exception.message)
    }

    @Test
    fun failParsingStreamWithMissingDataAfterObject() = runTest {
        val data = "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A\n" +
            "L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg\n"
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            decodeVersionDeltaStreamV2(channel).deltaObjects.toList()
        }
        assertEquals("Received incomplete response.", exception.message)
    }
}

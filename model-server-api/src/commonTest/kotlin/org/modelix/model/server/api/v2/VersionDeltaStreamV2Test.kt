package org.modelix.model.server.api.v2

import io.ktor.util.cio.use
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.modelix.model.server.api.v2.VersionDeltaStreamV2.Companion.IncompleteData
import org.modelix.model.server.api.v2.VersionDeltaStreamV2.Companion.decodeVersionDeltaStreamV2
import org.modelix.model.server.api.v2.VersionDeltaStreamV2.Companion.encodeVersionDeltaStreamV2
import org.modelix.streams.IExecutableStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VersionDeltaStreamV2Test {

    @Test
    fun parsesStreamWithoutObjects() = runTest {
        val channel = ByteChannel()
        channel.use {
            encodeVersionDeltaStreamV2(channel, "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A", IExecutableStream.many())
        }
        val versionDeltaStream = decodeVersionDeltaStreamV2(channel)

        assertEquals("CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A", versionDeltaStream.versionHash)
        assertEquals(emptyList(), versionDeltaStream.hashesWithDeltaObject.toList())
    }

    @Test
    fun parsesStreamWithObjects() = runTest {
        val channel = ByteChannel()
        channel.use {
            encodeVersionDeltaStreamV2(
                channel,
                "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A",
                IExecutableStream.many(
                    "r7k0y*p0mmIhhD46RvqLsmTEGuBQvAf9hw7aN0IzihLc" to "L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg",
                    "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A" to "1/%00/0/%00///",
                ),
            )
        }

        val versionDeltaStream = decodeVersionDeltaStreamV2(channel)

        assertEquals("CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A", versionDeltaStream.versionHash)
        val expectedObjects = listOf(
            "r7k0y*p0mmIhhD46RvqLsmTEGuBQvAf9hw7aN0IzihLc" to "L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg",
            "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A" to "1/%00/0/%00///",
        )
        assertEquals(expectedObjects, versionDeltaStream.hashesWithDeltaObject.toList())
    }

    @Test
    fun failsToParseEmptyStream() = runTest {
        val data = ""
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IncompleteData> {
            decodeVersionDeltaStreamV2(channel).hashesWithDeltaObject.toList()
        }
        assertEquals("Missing data line", exception.message)
    }

    @Test
    fun failsToParseIncompleteLine() = runTest {
        val data = "CTVRw*a6KXJ4o7uzGlp"
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IncompleteData> {
            decodeVersionDeltaStreamV2(channel).hashesWithDeltaObject.toList()
        }
        assertEquals(
            "Missing end of data line [dataLine=`CTVRw*a6KXJ4o7uzGlp`] [endOfDataLine=`null`]",
            exception.message,
        )
    }

    @Test
    fun failsToParseStreamWithWrongEndOfDataLine() = runTest {
        val data = "CTVRw*a6KXJ4o7uzGlp\n%"
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IncompleteData> {
            decodeVersionDeltaStreamV2(channel).hashesWithDeltaObject.toList()
        }
        assertEquals(
            "Missing end of data line [dataLine=`CTVRw*a6KXJ4o7uzGlp`] [endOfDataLine=`%`]",
            exception.message,
        )
    }

    @Test
    fun failsToParseStreamWithoutVersionHash() = runTest {
        val data = "~"
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IncompleteData> {
            decodeVersionDeltaStreamV2(channel).hashesWithDeltaObject.toList()
        }
        assertEquals("Version hash missing.", exception.message)
    }

    @Test
    fun failsParsingStreamWithMissingDataAfterVersionHash() = runTest {
        val data = "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A\n$\n"
        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val exception = assertFailsWith<IncompleteData> {
            decodeVersionDeltaStreamV2(channel).hashesWithDeltaObject.toList()
        }
        assertEquals("Missing data line", exception.message)
    }

    @Test
    fun failsParsingStreamWithMissingObject() = runTest {
        val data = "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A\n$\n" +
            "r7k0y*p0mmIhhD46RvqLsmTEGuBQvAf9hw7aN0IzihLc\n$\n" +
            "L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg\n$\n" +
            "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A\n$\n" +
            "~"

        val channel = ByteChannel()
        channel.use {
            writeStringUtf8(data)
        }

        val emittedObjects = mutableListOf<Pair<String, String>>()
        val exception = assertFailsWith<IncompleteData> {
            decodeVersionDeltaStreamV2(channel).hashesWithDeltaObject.collect(emittedObjects::add)
        }
        assertEquals("Missing delta object.", exception.message)
        assertEquals(
            listOf("r7k0y*p0mmIhhD46RvqLsmTEGuBQvAf9hw7aN0IzihLc" to "L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg"),
            emittedObjects,
        )
    }

    @Test
    fun failsToParseAnySubstringOfAValidEncoding() = runTest {
        val channel = ByteChannel()
        channel.use {
            encodeVersionDeltaStreamV2(
                channel,
                "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A",
                IExecutableStream.many(
                    "r7k0y*p0mmIhhD46RvqLsmTEGuBQvAf9hw7aN0IzihLc" to "L/100000017/xioDt*mnraICBf48DpWkvvtl2KuPixWn1p7yteYQ3XSg",
                    "CTVRw*a6KXJ4o7uzGlp-kUosxpyRf4fUpHnLokG9T86A" to "1/%00/0/%00///",
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

            assertFailsWith<IncompleteData> {
                decodeVersionDeltaStreamV2(subChannel).hashesWithDeltaObject.toList()
            }
        }
    }
}

package org.modelix.model.server.api.v2

import io.ktor.http.ContentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.modelix.streams.IExecutableStream

/**
 * In comparison to the previous format for version deltas,
 * this format is structured so that incompletely sent data can be detected.
 *
 * Detecting incomplete data is a workaround for:
 * - https://youtrack.jetbrains.com/issue/KTOR-6905/The-client-reads-incomplete-streamed-responses-without-failing
 *   In this case partial data from a request might be read
 *   without throwing an exception or indicating it with a correct return value
 * - https://youtrack.jetbrains.com/issue/KTOR-4862/Ktor-hangs-if-exception-occurs-during-write-response-body
 *   Because Ktor server does not close the connection when an exception occurs while writing a body
 *   we always close the connection even if not all data was yet send (see [ByteWriteChannel.useClosingWithoutCause]).
 *
 *  The format sends redundant hashes because of previous bugs encountered with SHA1 calculation.
 *  See https://github.com/modelix/modelix.core/pull/213/commits/a412bc97765426fcc81db0c55516c65b8679641b
 */
class VersionDeltaStreamV2(
    val versionHash: String,
    val hashesWithDeltaObject: Flow<Pair<String, String>>,
) {
    companion object {
        class IncompleteData(message: String) : RuntimeException(message)

        val CONTENT_TYPE = ContentType("application", "x-modelix-objects-v2")
        private const val NEW_LINE_IN_VERSION_DELTA_STREAM_V2 = "\n"

        /**
         * Magic byte (as string) that indicates the end of one data line/all streaming data.
         * Use `$`/`~` because it:
         * (1) encodes as a single byte in UTF8 and therefore cannot be sent partially.
         * (2) is not a first character in serialized value (see. [IKVValue.serialize]).
         * (3) it is a char that would be URL-encoded in object data.
         */
        private const val MAGIC_BYTE_FOR_END_OF_DATA_LINE = "$"
        private const val MAGIC_BYTE_FOR_END_OF_VERSION_DELTA = "~"

        private suspend fun encodeLine(output: ByteWriteChannel, line: String) {
            output.writeStringUtf8(line)
            output.writeStringUtf8(NEW_LINE_IN_VERSION_DELTA_STREAM_V2)
            output.writeStringUtf8(MAGIC_BYTE_FOR_END_OF_DATA_LINE)
            output.writeStringUtf8(NEW_LINE_IN_VERSION_DELTA_STREAM_V2)
        }

        suspend fun encodeVersionDeltaStreamV2(
            output: ByteWriteChannel,
            versionHash: String,
            hashesWithDeltaObject: IExecutableStream.Many<Pair<String, String>>,
        ) {
            encodeLine(output, versionHash)
            hashesWithDeltaObject.iterateSuspending { (hash, deltaObject) ->
                encodeLine(output, hash)
                encodeLine(output, deltaObject)
            }
            output.writeStringUtf8(MAGIC_BYTE_FOR_END_OF_VERSION_DELTA)
        }

        private suspend fun decodeLine(input: ByteReadChannel): String? {
            val dataLine = input.readUTF8Line()
            when (dataLine) {
                null -> throw IncompleteData("Missing data line")
                MAGIC_BYTE_FOR_END_OF_VERSION_DELTA -> return null
            }
            val endOfDataLine = input.readUTF8Line()
            if (endOfDataLine != MAGIC_BYTE_FOR_END_OF_DATA_LINE) {
                throw IncompleteData("Missing end of data line [dataLine=`$dataLine`] [endOfDataLine=`$endOfDataLine`]")
            }
            return dataLine
        }

        /**
         * @throws IncompleteData if data is detected to be incomplete
         */
        suspend fun decodeVersionDeltaStreamV2(input: ByteReadChannel): VersionDeltaStreamV2 {
            val versionHash = decodeLine(input) ?: throw IncompleteData("Version hash missing.")
            val hashesWithDeltaObject = flow {
                while (true) {
                    val hash = decodeLine(input) ?: break
                    val deltaObject = decodeLine(input) ?: throw IncompleteData("Missing delta object.")
                    emit(hash to deltaObject)
                }
            }
            return VersionDeltaStreamV2(versionHash, hashesWithDeltaObject)
        }
    }
}

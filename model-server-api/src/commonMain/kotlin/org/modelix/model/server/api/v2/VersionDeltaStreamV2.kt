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

import io.ktor.http.ContentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class VersionDeltaStreamV2(
    val versionHash: String,
    val deltaObjects: Flow<String>,
) {

    companion object {

        val CONTENT_TYPE = ContentType("application", "x-modelix-objects-v2")
        private const val NEW_LINE_IN_VERSION_DELTA_STREAM_V2 = "\n"

        suspend fun encodeVersionDeltaStreamV2(
            output: ByteWriteChannel,
            versionHash: String,
            objects: Flow<String>,
        ) {
            output.writeStringUtf8(versionHash)
            output.writeStringUtf8(NEW_LINE_IN_VERSION_DELTA_STREAM_V2)
            objects.collect {
                output.writeStringUtf8(it)
                output.writeStringUtf8(NEW_LINE_IN_VERSION_DELTA_STREAM_V2)
            }
            output.writeStringUtf8(NEW_LINE_IN_VERSION_DELTA_STREAM_V2)
        }

        suspend fun decodeVersionDeltaStreamV2(input: ByteReadChannel): VersionDeltaStreamV2 {
            val versionHash = requireNotNull(input.readUTF8Line()) { "Version hash missing." }
            val deltaObjects: Flow<String> = flow {
                var value: String? = input.readUTF8Line()
                while (!value.isNullOrEmpty()) {
                    emit(value)
                    value = input.readUTF8Line()
                }
                val reachedProperEnding = value == ""
                require(reachedProperEnding) {
                    "Received incomplete response."
                }
            }
            return VersionDeltaStreamV2(versionHash, deltaObjects)
        }
    }
}

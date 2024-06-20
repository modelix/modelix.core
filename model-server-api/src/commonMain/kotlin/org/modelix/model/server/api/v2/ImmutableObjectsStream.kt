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
import io.ktor.utils.io.readUTF8Line

object ImmutableObjectsStream {
    val CONTENT_TYPE = ContentType("application", "x-modelix-immutable-objects")

    fun encode(out: Appendable, objects: Map<String, String>) {
        encode(out, objects.asSequence())
    }

    fun encode(out: Appendable, objects: Sequence<Map.Entry<String, String>>) {
        objects.forEach {
            out.append(it.key)
            out.append("\n")
            out.append(it.value)
            out.append("\n")
        }
        // additional empty line indicates end of stream and can be used to verify completeness of data transfer
        out.append("\n")
    }

    suspend fun decode(input: ByteReadChannel): Map<String, String> {
        val objects = LinkedHashMap<String, String>()
        while (true) {
            val key = checkNotNull(input.readUTF8Line()) { "Empty line expected at the end of the stream" }
            if (key == "") {
                check(input.readUTF8Line() == null) { "Empty line is only allowed at the end of the stream" }
                break
            }
            val value = checkNotNull(input.readUTF8Line()) { "Object missing for hash $key" }
            objects[key] = value
        }
        return objects
    }
}

typealias ObjectHash = String
typealias SerializedObject = String
typealias ObjectHashAndSerializedObject = Pair<ObjectHash, SerializedObject>

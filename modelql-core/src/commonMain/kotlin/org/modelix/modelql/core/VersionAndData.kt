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
package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class VersionAndData<E>(val data: E, val version: String?) {
    constructor(data: E) : this(data, modelqlVersion)
    companion object {
        private val LOG = mu.KotlinLogging.logger { }
        fun readVersionOnly(text: String): String? {
            val version = runCatching {
                val deserialized: JsonObject = Json.decodeFromString(text)
                (deserialized["version"] as? JsonPrimitive)?.content
            }
            if (version.isFailure) LOG.warn(version.exceptionOrNull()!!) { "Failed to read version from: $text" }
            return version.getOrNull()
        }

        fun <T> deserialize(serializedJson: String, dataSerializer: KSerializer<T>, json: Json): VersionAndData<T> {
            try {
                return json.decodeFromString(
                    VersionAndData.serializer(dataSerializer),
                    serializedJson,
                )
            } catch (ex: Exception) {
                val actualVersion = readVersionOnly(serializedJson)
                val expectedVersion = modelqlVersion
                if (actualVersion != expectedVersion) {
                    throw RuntimeException("Deserialization failed. Version $expectedVersion expected, but was $actualVersion", ex)
                } else {
                    throw ex
                }
            }
        }
    }
}

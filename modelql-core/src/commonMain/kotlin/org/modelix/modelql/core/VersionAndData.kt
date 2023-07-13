package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class VersionAndData<E>(val data: E, val version: String? = modelqlVersion) {
    companion object {
        fun readVersionOnly(text: String): String? {
            return runCatching {
                val deserialized: JsonObject = Json.decodeFromString(text)
                (deserialized.get("version") as? JsonPrimitive)?.content
            }.getOrNull()?.takeIf { it != "unknown" }
        }

        fun <T> deserialize(serializedJson: String, dataSerializer: KSerializer<T>, json: Json): VersionAndData<T> {
            try {
                return json.decodeFromString(
                    VersionAndData.serializer(dataSerializer),
                    serializedJson
                )
            } catch (ex: Exception) {
                val actualVersion = readVersionOnly(serializedJson)
                val expectedVersion = modelqlVersion
                if (!actualVersion.isNullOrBlank() && actualVersion != expectedVersion) {
                    throw RuntimeException("Deserialization failed. Version $expectedVersion expected, but was $actualVersion", ex)
                } else {
                    throw ex
                }
            }
        }
    }
}

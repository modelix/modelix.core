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
package org.modelix.metamodel.generator

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
@Deprecated("use org.modelix.mode.data.*")
data class LanguageData(
    val uid: String? = null,
    val name: String,
    val concepts: List<ConceptData>,
) {

    fun toYaml(): String = Yaml.default.encodeToString(this)
    fun toJson(): String = prettyJson.encodeToString(this)
    fun toCompactJson(): String = Json.encodeToString(this)

    companion object {
        private val prettyJson = Json { prettyPrint = true }
        fun fromFile(file: File): LanguageData {
            return when (file.extension.lowercase()) {
                "yaml" -> Yaml.default.decodeFromString(file.readText())
                "json" -> Json.decodeFromString(file.readText())
                else -> throw IllegalArgumentException("Unsupported file extension: $file")
            }
        }
    }
}

@Serializable
@Deprecated("use org.modelix.mode.data.*")
data class ConceptData(
    val uid: String? = null,
    val name: String,
    val abstract: Boolean = false,
    val properties: List<PropertyData> = emptyList(),
    val children: List<ChildLinkData> = emptyList(),
    val references: List<ReferenceLinkData> = emptyList(),
    val extends: List<String> = emptyList(),
)

@Deprecated("use org.modelix.mode.data.*")
sealed interface IConceptFeatureData {
    val uid: String?
    val name: String
}

@Serializable
@Deprecated("use org.modelix.mode.data.*")
data class PropertyData(
    override val uid: String? = null,
    override val name: String,
    val type: PropertyType = PropertyType.STRING,
) : IConceptFeatureData

@Deprecated("use org.modelix.mode.data.*")
enum class PropertyType {
    STRING, BOOLEAN, INT
}

@Serializable
@Deprecated("use org.modelix.mode.data.*")
data class ChildLinkData(
    override val uid: String? = null,
    override val name: String,
    val type: String,
    val multiple: Boolean = false,
    val optional: Boolean = true,
) : IConceptFeatureData

@Serializable
@Deprecated("use org.modelix.mode.data.*")
data class ReferenceLinkData(
    override val uid: String? = null,
    override val name: String,
    val type: String,
    val optional: Boolean = true,
) : IConceptFeatureData

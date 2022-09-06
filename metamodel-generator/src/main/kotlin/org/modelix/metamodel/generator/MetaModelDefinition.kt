package org.modelix.metamodel.generator

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LanguageData(
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
data class ConceptData(
    val name: String,
    val abstract: Boolean = false,
    val properties: List<PropertyData> = emptyList(),
    val children: List<ChildLinkData> = emptyList(),
    val references: List<ReferenceLinkData> = emptyList(),
    val extends: List<String> = emptyList(),
)

interface IConceptFeatureData {
    val name: String
}

@Serializable
data class PropertyData (
    override val name: String,
    val type: PropertyType = PropertyType.STRING
) : IConceptFeatureData

enum class PropertyType {
    STRING,
}

@Serializable
data class ChildLinkData(
    override val name: String,
    val type: String,
    val multiple: Boolean = false,
    val optional: Boolean = true,
) : IConceptFeatureData

@Serializable
data class ReferenceLinkData(
    override val name: String,
    val type: String,
    val optional: Boolean = true,
) : IConceptFeatureData

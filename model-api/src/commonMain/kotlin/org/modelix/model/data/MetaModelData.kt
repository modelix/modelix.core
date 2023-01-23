package org.modelix.model.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LanguageData(
    val uid: String? = null,
    val name: String,
    val concepts: List<ConceptData>,
) {

    fun toJson(): String = prettyJson.encodeToString(this)
    fun toCompactJson(): String = Json.encodeToString(this)

    companion object {
        private val prettyJson = Json { prettyPrint = true }
        fun fromJson(json: String): LanguageData {
            return Json.decodeFromString(json)
        }
    }
}

@Serializable
data class ConceptData(
    val uid: String? = null,
    val name: String,
    val abstract: Boolean = false,
    val properties: List<PropertyData> = emptyList(),
    val children: List<ChildLinkData> = emptyList(),
    val references: List<ReferenceLinkData> = emptyList(),
    val extends: List<String> = emptyList(),
)

sealed interface IConceptFeatureData {
    val uid: String?
    val name: String
}

@Serializable
data class PropertyData(
    override val uid: String? = null,
    override val name: String,
    val type: PropertyType = PropertyType.STRING,
    val optional: Boolean = true
) : IConceptFeatureData

enum class PropertyType {
    STRING, BOOLEAN, INT
}

@Serializable
data class ChildLinkData(
    override val uid: String? = null,
    override val name: String,
    val type: String,
    val multiple: Boolean = false,
    val optional: Boolean = true,
) : IConceptFeatureData

@Serializable
data class ReferenceLinkData(
    override val uid: String? = null,
    override val name: String,
    val type: String,
    val optional: Boolean = true,
) : IConceptFeatureData

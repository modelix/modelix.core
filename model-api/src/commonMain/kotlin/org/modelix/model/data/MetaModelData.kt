package org.modelix.model.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

@Serializable
data class LanguageData(
    val uid: String? = null,
    val name: String,
    val concepts: List<ConceptData>,
    val enums: List<EnumData> = emptyList(),
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

sealed interface IDeprecatable {
    val deprecationMessage: String?
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
    override val deprecationMessage: String? = null,
    val smodelAttributes: List<AnnotationData> = emptyList(),
    val metaProperties: MutableMap<String, String> = mutableMapOf(),
) : IDeprecatable {
    companion object {
        const val ALIAS_KEY = "alias"
    }
}

@Serializable
data class EnumData(
    val uid: String? = null,
    val name: String,
    val members: List<EnumMemberData> = emptyList(),
    val defaultIndex: Int,
    override val deprecationMessage: String? = null,
) : IDeprecatable

@Serializable
data class EnumMemberData(
    val uid: String,
    val name: String,
    val presentation: String? = null,
)

sealed interface IConceptFeatureData {
    val uid: String?
    val name: String
}

@Serializable
data class PropertyData(
    override val uid: String? = null,
    override val name: String,
    val type: PropertyType = PrimitivePropertyType(Primitive.STRING),
    val optional: Boolean = true,
    override val deprecationMessage: String? = null,
) : IConceptFeatureData, IDeprecatable

class PropertyTypeSerializer : KSerializer<PropertyType> {
    override fun deserialize(decoder: Decoder): PropertyType {
        val serialized = decoder.decodeString()
        return try {
            PrimitivePropertyType(Primitive.valueOf(serialized))
        } catch (ex: Exception) {
            EnumPropertyType(serialized.substringBeforeLast("."), serialized.substringAfterLast("."))
        }
    }

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("modelix.metamodel.PropertyType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: PropertyType) {
        val serialized = when (value) {
            is EnumPropertyType -> value.pckg + "." + value.enumName
            is PrimitivePropertyType -> value.primitive.name
        }
        encoder.encodeString(serialized)
    }
}

@Serializable(with = PropertyTypeSerializer::class)
sealed interface PropertyType

enum class Primitive {
    STRING, BOOLEAN, INT
}

@Serializable
data class PrimitivePropertyType(val primitive: Primitive) : PropertyType

@Serializable
data class EnumPropertyType(val pckg: String, val enumName: String) : PropertyType

@Serializable
data class ChildLinkData(
    override val uid: String? = null,
    override val name: String,
    val type: String,
    val multiple: Boolean = false,
    val optional: Boolean = true,
    override val deprecationMessage: String? = null,
) : IConceptFeatureData, IDeprecatable

@Serializable
data class ReferenceLinkData(
    override val uid: String? = null,
    override val name: String,
    val type: String,
    val optional: Boolean = true,
    override val deprecationMessage: String? = null,
) : IConceptFeatureData, IDeprecatable

@Serializable
data class AnnotationData(
    val uid: String? = null,
    val type: String,
    val role: String? = null,
    val children: List<NodeData> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
    val references: Map<String, String> = emptyMap(),
)

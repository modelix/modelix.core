package org.modelix.model.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.modelix.model.area.IArea

@Serializable(with = ConceptReferenceKSerializer::class)
data class ConceptReference(val uid: String) : IConceptReference {
    @Deprecated("use ILanguageRepository.resolveConcept")
    override fun resolve(area: IArea?): IConcept? {
        return area?.resolveConcept(this)
    }

    override fun getUID(): String {
        return uid
    }

    @Deprecated("use getUID()", ReplaceWith("getUID()"))
    override fun serialize(): String {
        return uid
    }

    override fun toString(): String {
        return uid
    }
}

class ConceptReferenceKSerializer : KSerializer<ConceptReference> {
    override fun deserialize(decoder: Decoder): ConceptReference {
        val serialized = decoder.decodeString()
        return ConceptReference(serialized)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("modelix.ConceptReference", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ConceptReference) {
        encoder.encodeString(value.uid)
    }
}

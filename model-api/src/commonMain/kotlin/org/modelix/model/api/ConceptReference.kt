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

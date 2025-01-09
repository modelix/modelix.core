package org.modelix.model.api

import org.modelix.model.api.meta.NullConcept
import org.modelix.model.area.IArea

/**
 * Reference to an [IConcept].
 */
@Deprecated("use ConceptReference")
interface IConceptReference {
    companion object {
        private var deserializers: Map<Any, ((String) -> IConceptReference?)> = LinkedHashMap()

        @Deprecated("use ConceptReference()")
        fun deserialize(serialized: String?): ConceptReference? {
            if (serialized == null) return null
            val refs = deserializers.values.mapNotNull { deserialize(serialized) }
            return when (refs.size) {
                0 -> ConceptReference(serialized)
                1 -> refs.first()
                else -> throw RuntimeException("Multiple deserializers applicable to $serialized")
            }
        }

        @Deprecated("use ILanguageRepository.register")
        fun registerDeserializer(key: Any, deserializer: ((String) -> IConceptReference?)) {
            deserializers = deserializers + (key to deserializer)
        }

        @Deprecated("use ILanguageRepository.unregister")
        fun unregisterSerializer(key: Any) {
            deserializers = deserializers - key
        }
    }

    /**
     * Returns the unique id of this concept reference.
     *
     * @return uid of this concept reference
     */
    fun getUID(): String

    @Deprecated("use ILanguageRepository.resolveConcept")
    fun resolve(area: IArea?): IConcept?

    @Deprecated("use getUID()")
    fun serialize(): String
}

fun IConceptReference?.upcast(): ConceptReference = this?.let { it as ConceptReference } ?: NullConcept.getReference()

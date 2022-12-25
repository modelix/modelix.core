/*
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
package org.modelix.model.api

import org.modelix.model.area.IArea

interface IConceptReference {
    companion object {
        private var deserializers: Map<Any, ((String)->IConceptReference?)> = LinkedHashMap()
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
    fun getUID(): String
    @Deprecated("use ILanguageRepository.resolveConcept")
    fun resolve(area: IArea?): IConcept?
    @Deprecated("use getUID()")
    fun serialize(): String
}

fun IConceptReference.resolve(): IConcept = ILanguageRepository.resolveConcept(this)
fun IConceptReference.tryResolve(): IConcept? = ILanguageRepository.tryResolveConcept(this)

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

import kotlinx.serialization.Serializable
import org.modelix.model.area.IArea

@Serializable
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

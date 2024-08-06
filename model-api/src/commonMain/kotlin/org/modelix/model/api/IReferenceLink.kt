/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.api

/**
 * Representation of a non-containment reference link between [IConcept]s.
 */
interface IReferenceLink : ILink, IReferenceLinkReference {
    companion object {
        fun fromName(name: String): IReferenceLink = ReferenceLinkFromName(name)
    }
}

@Deprecated("For compatibility with methods that still require an IReferenceLink instead of just an IReferenceLinkReference")
fun IReferenceLinkReference.asLink() = this as IReferenceLink

interface IReferenceLinkReference {

    fun getSimpleName(): String?
    fun getUID(): String?

    companion object {
        /**
         * Can be a name or UID or anything else. INode will decide how to resolve it.
         */
        fun fromString(value: String): IReferenceLinkReference = UnclassifiedReferenceLinkReference(value)
        fun fromName(value: String): IReferenceLinkReference = ReferenceLinkReferenceByName(value)
        fun fromUID(value: String): IReferenceLinkReference = ReferenceLinkReferenceByUID(value)
    }
}

abstract class AbstractReferenceLinkReference : IReferenceLinkReference, IReferenceLink {
    override fun getConcept(): IConcept = throw UnsupportedOperationException()
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
    override val isOptional: Boolean get() = throw UnsupportedOperationException()
    override val targetConcept: IConcept get() = throw UnsupportedOperationException()
}

data class UnclassifiedReferenceLinkReference(val value: String) : AbstractReferenceLinkReference()
data class ReferenceLinkReferenceByName(override val name: String) : AbstractReferenceLinkReference() {
    override fun getSimpleName(): String = name
}
data class ReferenceLinkReferenceByUID(val uid: String) : AbstractReferenceLinkReference() {
    override fun getUID(): String = uid
}

data class ReferenceLinkFromName(override val name: String) : LinkFromName(), IReferenceLink

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

import kotlinx.serialization.Serializable

/**
 * Representation of a non-containment reference link between [IConcept]s.
 */
interface IReferenceLink : ILink, IReferenceLinkReference {
    companion object {
        fun fromName(name: String): IReferenceLink = ReferenceLinkFromName(name)
    }
}

@Deprecated("For compatibility with methods that still require an IReferenceLink instead of just an IReferenceLinkReference")
fun IReferenceLinkReference.toLink() = this as IReferenceLink

@Serializable
sealed interface IReferenceLinkReference : IRoleReference {
    companion object {
        /**
         * Can be a name or UID or anything else. INode will decide how to resolve it.
         */
        fun fromString(value: String): IReferenceLinkReference = UnclassifiedReferenceLinkReference(value)
        fun fromName(value: String): IReferenceLinkReference = ReferenceLinkReferenceByName(value)
        fun fromUID(value: String): IReferenceLinkReference = ReferenceLinkReferenceByUID(value)
        fun fromIdAndName(id: String, name: String): IReferenceLinkReference = ReferenceLinkReferenceByIdAndName(id, name)
    }
}

@Serializable
sealed class AbstractReferenceLinkReference : AbstractRoleReference(), IReferenceLinkReference, IReferenceLink {
    override fun getConcept(): IConcept = throw UnsupportedOperationException()
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
    override val isOptional: Boolean get() = throw UnsupportedOperationException()
    override val targetConcept: IConcept get() = throw UnsupportedOperationException()
}

@Serializable
data class UnclassifiedReferenceLinkReference(val value: String) : AbstractReferenceLinkReference(), IUnclassifiedRoleReference {
    override fun getStringValue(): String = value
    override fun getIdOrName(): String = value
    override fun getNameOrId(): String = value
}

@Serializable
data class ReferenceLinkReferenceByName(override val name: String) : AbstractReferenceLinkReference(), IRoleReferenceByName {
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = name
    override fun getNameOrId(): String = name
}

@Serializable
data class ReferenceLinkReferenceByUID(val uid: String) : AbstractReferenceLinkReference(), IRoleReferenceByUID {
    override fun getUID(): String = uid
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = uid
}

@Serializable
data class ReferenceLinkReferenceByIdAndName(val uid: String, override val name: String) : AbstractReferenceLinkReference(), IRoleReferenceByUID, IRoleReferenceByName {
    override fun getUID(): String = uid
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = name
}

data class ReferenceLinkFromName(override val name: String) : LinkFromName(), IReferenceLink {
    override fun getIdOrName(): String = name
    override fun getNameOrId(): String = name
}

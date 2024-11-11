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
 * Representation of a parent-child relationship between [IConcept]s.
 */
@Deprecated("Use IChildLinkReference or IChildLinkDefinition")
interface IChildLink : ILink, IChildLinkDefinition {
    /**
     * Specifies if the parent-child relation ship is 1:n.
     */
    override val isMultiple: Boolean

    @Deprecated("use .targetConcept")
    val childConcept: IConcept

    companion object {
        @Deprecated("Use IChildLinkReference.fromName/.fromUID/.fromString")
        fun fromName(name: String): IChildLink = ChildLinkFromName(name)
    }

    /**
     * Whether children with this role are returned in a meaningful order and whether they are allowed to be reordered.
     *
     * Children returned for an unordered role might be returned in a different order in subsequent request.
     * If a child role is not ordered, implementations of [[INode.moveChild]] are allowed to fail
     * when instructed to move a node in between existing nodes.
     */
    override val isOrdered
        get() = true

    override fun toReference(): IChildLinkReference = IChildLinkReference.fromIdAndName(getUID(), getSimpleName())
}

sealed interface IChildLinkDefinition : ILinkDefinition {
    val isMultiple: Boolean
    val isOrdered: Boolean
    override fun toReference(): IChildLinkReference
}

fun IChildLink?.toReference(): IChildLinkReference = this?.toReference() ?: NullChildLinkReference

@Serializable
sealed interface IChildLinkReference : ILinkReference {

    override fun toLegacy(): IChildLink

    fun getIdOrNameOrNull(): String? = getIdOrName()

    fun getNameOrIdOrNull(): String? = getNameOrId()

    companion object {
        /**
         * Can be a name or UID or anything else. INode will decide how to resolve it.
         */
        fun fromUnclassifiedString(value: String?): IChildLinkReference {
            return if (value == null) NullChildLinkReference else UnclassifiedChildLinkReference(value)
        }
        fun fromName(value: String): IChildLinkReference = ChildLinkReferenceByName(value)
        fun fromId(value: String): IChildLinkReference = ChildLinkReferenceByUID(value)
        fun fromIdAndName(id: String?, name: String?): IChildLinkReference {
            return if (id == null) {
                if (name == null) {
                    throw IllegalArgumentException("Both 'id' and 'name' are null")
                } else {
                    ChildLinkReferenceByName(name)
                }
            } else {
                if (name == null) {
                    ChildLinkReferenceByUID(id)
                } else {
                    ChildLinkReferenceByIdAndName(id, name)
                }
            }
        }
    }
}

@Serializable
sealed class AbstractChildLinkReference : AbstractRoleReference(), IChildLinkReference, IChildLink {
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
    override val childConcept: IConcept get() = throw UnsupportedOperationException()
    override fun getConcept(): IConcept = throw UnsupportedOperationException()
    override val isMultiple: Boolean get() = throw UnsupportedOperationException()
    override val isOptional: Boolean get() = throw UnsupportedOperationException()
    override val targetConcept: IConcept get() = throw UnsupportedOperationException()
    override fun toLegacy(): IChildLink = this
    override fun toReference(): IChildLinkReference = this
}

@Serializable
object NullChildLinkReference : AbstractChildLinkReference() {
    override fun getIdOrName(): String {
        throw NullPointerException("link is a NullChildLinkReference")
    }

    override fun getNameOrId(): String {
        throw NullPointerException("link is a NullChildLinkReference")
    }

    override fun getIdOrNameOrNull(): String? = null

    override fun getNameOrIdOrNull(): String? = null
}

@Serializable
data class UnclassifiedChildLinkReference(val value: String) : AbstractChildLinkReference(), IUnclassifiedRoleReference {
    override fun getStringValue(): String = value
    override fun getIdOrName(): String = value
    override fun getNameOrId(): String = value
}

@Serializable
data class ChildLinkReferenceByName(override val name: String) : AbstractChildLinkReference(), IRoleReferenceByName {
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = name
    override fun getNameOrId(): String = name
}

@Serializable
data class ChildLinkReferenceByUID(val uid: String) : AbstractChildLinkReference(), IRoleReferenceByUID {
    override fun getUID(): String = uid
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = uid
}

@Serializable
data class ChildLinkReferenceByIdAndName(val uid: String, override val name: String) : AbstractChildLinkReference(), IRoleReferenceByUID, IRoleReferenceByName {
    override fun getUID(): String = uid
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = name
}

@Deprecated("Use ChildLinkReferenceByName")
data class ChildLinkFromName(override val name: String) : LinkFromName(), IChildLink {
    override val isMultiple: Boolean
        get() = throw UnsupportedOperationException()
    override val childConcept: IConcept
        get() = throw UnsupportedOperationException()

    override fun toReference(): IChildLinkReference = UnclassifiedChildLinkReference(name)
}

@Deprecated("Use NullChildLinkReference")
object NullChildLink : IChildLink {
    override val isMultiple: Boolean
        get() = true
    override val childConcept: IConcept
        get() = throw UnsupportedOperationException()
    override val targetConcept: IConcept
        get() = throw UnsupportedOperationException()

    override fun getConcept(): IConcept {
        throw UnsupportedOperationException()
    }

    override fun getUID(): String {
        throw UnsupportedOperationException()
    }

    override fun getSimpleName(): String {
        throw UnsupportedOperationException()
    }

    override val isOptional: Boolean
        get() = true

    override fun toReference(): IChildLinkReference = NullChildLinkReference
}

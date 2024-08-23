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
 * Representation of a property within an [IConcept].
 */
@Deprecated("Use IPropertyReference or IPropertyDefinition")
interface IProperty : IRole {

    override fun toReference(): IPropertyReference = IPropertyReference.fromIdAndName(getUID(), getSimpleName())

    companion object {
        fun fromName(name: String): IProperty = PropertyFromName(name)
    }
}

@Serializable
sealed interface IPropertyReference : IRoleReference {

    override fun toLegacy(): IProperty

    companion object {
        /**
         * Can be a name or UID or anything else. INode will decide how to resolve it.
         */
        fun fromUnclassifiedString(value: String): IPropertyReference = UnclassifiedPropertyReference(value)
        fun fromName(value: String): IPropertyReference = PropertyReferenceByName(value)
        fun fromId(value: String): IPropertyReference = PropertyReferenceByUID(value)
        fun fromIdAndName(id: String?, name: String?): IPropertyReference {
            return if (id == null) {
                if (name == null) {
                    throw IllegalArgumentException("Both 'id' and 'name' are null")
                } else {
                    PropertyReferenceByName(name)
                }
            } else {
                if (name == null) {
                    PropertyReferenceByUID(id)
                } else {
                    PropertyReferenceByIdAndName(id, name)
                }
            }
        }
    }
}

@Deprecated("For compatibility with methods that still require an IProperty instead of just an IPropertyReference")
fun IPropertyReference.asProperty() = this as IProperty

@Serializable
sealed class AbstractPropertyReference : AbstractRoleReference(), IPropertyReference, IProperty {
    override fun getConcept(): IConcept = throw UnsupportedOperationException()
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
    override val isOptional: Boolean get() = throw UnsupportedOperationException()
    override fun toLegacy(): IProperty = this
    override fun toReference(): IPropertyReference = this
}

@Serializable
data class UnclassifiedPropertyReference(val value: String) : AbstractPropertyReference(), IUnclassifiedRoleReference {
    override fun getStringValue(): String = value
    override fun getIdOrName(): String = value
    override fun getNameOrId(): String = value
}

@Serializable
data class PropertyReferenceByName(override val name: String) : AbstractPropertyReference(), IRoleReferenceByName {
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = name
    override fun getNameOrId(): String = name
}

@Serializable
data class PropertyReferenceByUID(val uid: String) : AbstractPropertyReference(), IRoleReferenceByUID {
    override fun getUID(): String = uid
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = uid
}

@Serializable
data class PropertyReferenceByIdAndName(val uid: String, override val name: String) : AbstractPropertyReference(), IRoleReferenceByUID, IRoleReferenceByName {
    override fun getUID(): String = uid
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = name
}

/**
 * Legacy. It's not guaranteed that name is actually a name. Could also be a UID.
 */
@Deprecated("use PropertyReferenceByName")
data class PropertyFromName(override val name: String) : RoleFromName(), IProperty {
    override val isOptional: Boolean
        get() = throw UnsupportedOperationException()
    override fun toReference(): IPropertyReference = UnclassifiedPropertyReference(name)
}

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
 * Representation of a property within an [IConcept].
 */
@Deprecated("Use IPropertyReference or IPropertyDefinition")
interface IProperty : IRole, IPropertyReference {
    companion object {
        fun fromName(name: String): IProperty = PropertyFromName(name)
    }
}

interface IPropertyReference {

    fun getSimpleName(): String?
    fun getUID(): String?

    companion object {
        /**
         * Can be a name or UID or anything else. INode will decide how to resolve it.
         */
        fun fromString(value: String): IPropertyReference = UnclassifiedPropertyReference(value)
        fun fromName(value: String): IPropertyReference = PropertyReferenceByName(value)
        fun fromUID(value: String): IPropertyReference = PropertyReferenceByUID(value)
    }
}

@Deprecated("For compatibility with methods that still require an IProperty instead of just an IPropertyReference")
fun IPropertyReference.asProperty() = this as IProperty

abstract class AbstractPropertyReference : IPropertyReference, IProperty {
    override fun getConcept(): IConcept = throw UnsupportedOperationException()
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
    override val isOptional: Boolean get() = throw UnsupportedOperationException()
}

data class UnclassifiedPropertyReference(val value: String) : AbstractPropertyReference()
data class PropertyReferenceByName(override val name: String) : AbstractPropertyReference() {
    override fun getSimpleName(): String = name
}
data class PropertyReferenceByUID(val uid: String) : AbstractPropertyReference() {
    override fun getUID(): String = uid
}

/**
 * Legacy. It's not guaranteed that name is actually a name. Could also be a UID.
 */
data class PropertyFromName(override val name: String) : RoleFromName(), IProperty {
    override val isOptional: Boolean
        get() = throw UnsupportedOperationException()
}

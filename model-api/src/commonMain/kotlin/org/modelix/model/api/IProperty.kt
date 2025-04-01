package org.modelix.model.api

import kotlinx.serialization.Serializable

/**
 * Representation of a property within an [IConcept].
 */
@Deprecated("Use IPropertyReference or IPropertyDefinition")
interface IProperty : IRole, IPropertyDefinition {

    override fun toReference(): IPropertyReference = IPropertyReference.fromIdAndName(getUID(), getSimpleName())

    override fun toLegacy(): IProperty = this

    companion object {
        fun fromName(name: String): IProperty = PropertyFromName(name)
    }
}

interface IPropertyDefinition : IRoleDefinition {
    override fun toReference(): IPropertyReference

    override fun toLegacy(): IProperty
}

@Serializable
sealed interface IPropertyReference : IRoleReference {

    override fun toLegacy(): IProperty

    fun matches(other: IPropertyReference): Boolean

    override fun matches(unclassified: String?) = unclassified != null && matches(fromString(unclassified))

    companion object : IRoleReferenceFactory<IPropertyReference> {
        /**
         * Can be a name or UID or anything else. INode will decide how to resolve it.
         */
        override fun fromUnclassifiedString(value: String): IPropertyReference {
            IRoleReference.requireNotForLegacyApi(value)
            return UnclassifiedPropertyReference(value)
        }
        override fun fromName(value: String): IPropertyReference = PropertyReferenceByName(value)
        override fun fromId(value: String): IPropertyReference = PropertyReferenceByUID(value)
        override fun fromIdAndName(id: String?, name: String?): IPropertyReference {
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
    override fun getUID(): String = value
    override fun getSimpleName(): String = value
    override fun matches(other: IPropertyReference): Boolean {
        return when (other) {
            is PropertyReferenceByIdAndName -> value == other.uid || value == other.name
            is PropertyReferenceByName -> value == other.name
            is PropertyReferenceByUID -> value == other.uid
            is UnclassifiedPropertyReference -> value == other.value
        }
    }
}

@Serializable
data class PropertyReferenceByName(override val name: String) : AbstractPropertyReference(), IRoleReferenceByName {
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = name
    override fun getNameOrId(): String = name
    override fun matches(other: IPropertyReference): Boolean {
        return when (other) {
            is PropertyReferenceByIdAndName -> name == other.name
            is PropertyReferenceByName -> name == other.name
            is PropertyReferenceByUID -> false
            is UnclassifiedPropertyReference -> name == other.value
        }
    }
}

@Serializable
data class PropertyReferenceByUID(val uid: String) : AbstractPropertyReference(), IRoleReferenceByUID {
    override fun getUID(): String = uid
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = uid
    override fun matches(other: IPropertyReference): Boolean {
        return when (other) {
            is PropertyReferenceByIdAndName -> uid == other.uid
            is PropertyReferenceByName -> false
            is PropertyReferenceByUID -> uid == other.uid
            is UnclassifiedPropertyReference -> uid == other.value
        }
    }
}

@Serializable
data class PropertyReferenceByIdAndName(val uid: String, override val name: String) : AbstractPropertyReference(), IRoleReferenceByUID, IRoleReferenceByName {
    override fun getUID(): String = uid
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = name
    override fun matches(other: IPropertyReference): Boolean {
        return when (other) {
            is PropertyReferenceByIdAndName -> uid == other.uid
            is PropertyReferenceByName -> name == other.name
            is PropertyReferenceByUID -> uid == other.uid
            is UnclassifiedPropertyReference -> uid == other.value || name == other.value
        }
    }
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

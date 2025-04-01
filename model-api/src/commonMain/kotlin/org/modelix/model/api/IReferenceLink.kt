package org.modelix.model.api

import kotlinx.serialization.Serializable

/**
 * Representation of a non-containment reference link between [IConcept]s.
 */
@Deprecated("Use IReferenceLinkReference or IReferenceLinkDefinition")
interface IReferenceLink : ILink, IReferenceLinkDefinition {

    override fun toReference(): IReferenceLinkReference = IReferenceLinkReference.fromIdAndName(getUID(), getSimpleName())

    override fun toLegacy(): IReferenceLink = this

    companion object {
        fun fromName(name: String): IReferenceLink = ReferenceLinkFromName(name)
    }
}

sealed interface IReferenceLinkDefinition : ILinkDefinition {
    override fun toReference(): IReferenceLinkReference

    @Deprecated("use IReferenceLinkReference or IReferenceLinkDefinition instead of IReferenceLink")
    override fun toLegacy(): IReferenceLink
}

@Serializable
sealed interface IReferenceLinkReference : ILinkReference {

    override fun toLegacy(): IReferenceLink

    fun matches(other: IReferenceLinkReference): Boolean

    override fun matches(unclassified: String?) = unclassified != null && matches(fromString(unclassified))

    override fun stringForLegacyApi(): String

    companion object : IRoleReferenceFactory<IReferenceLinkReference> {
        /**
         * Can be a name or UID or anything else. INode will decide how to resolve it.
         */
        override fun fromUnclassifiedString(value: String): IReferenceLinkReference {
            IRoleReference.requireNotForLegacyApi(value)
            return UnclassifiedReferenceLinkReference(value)
        }
        override fun fromName(value: String): IReferenceLinkReference = ReferenceLinkReferenceByName(value)
        override fun fromId(value: String): IReferenceLinkReference = ReferenceLinkReferenceByUID(value)
        override fun fromIdAndName(id: String?, name: String?): IReferenceLinkReference {
            return if (id == null) {
                if (name == null) {
                    throw IllegalArgumentException("Both 'id' and 'name' are null")
                } else {
                    ReferenceLinkReferenceByName(name)
                }
            } else {
                if (name == null) {
                    ReferenceLinkReferenceByUID(id)
                } else {
                    ReferenceLinkReferenceByIdAndName(id, name)
                }
            }
        }
    }
}

@Serializable
sealed class AbstractReferenceLinkReference : AbstractRoleReference(), IReferenceLinkReference, IReferenceLink {
    override fun getConcept(): IConcept = throw UnsupportedOperationException()
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
    override val isOptional: Boolean get() = throw UnsupportedOperationException()
    override val targetConcept: IConcept get() = throw UnsupportedOperationException()
    override fun toLegacy(): IReferenceLink = this
    override fun toReference(): IReferenceLinkReference = this
}

@Serializable
data class UnclassifiedReferenceLinkReference(val value: String) : AbstractReferenceLinkReference(), IUnclassifiedRoleReference {
    override fun stringForLegacyApi() = value
    override fun getStringValue(): String = value
    override fun getIdOrName(): String = value
    override fun getNameOrId(): String = value
    override fun getUID(): String = value
    override fun getSimpleName(): String = value
    override fun matches(other: IReferenceLinkReference): Boolean {
        return when (other) {
            is ReferenceLinkReferenceByIdAndName -> value == other.uid || value == other.name
            is ReferenceLinkReferenceByName -> value == other.name
            is ReferenceLinkReferenceByUID -> value == other.uid
            is UnclassifiedReferenceLinkReference -> value == other.value
        }
    }
}

@Serializable
data class ReferenceLinkReferenceByName(override val name: String) : AbstractReferenceLinkReference(), IRoleReferenceByName {
    override fun stringForLegacyApi() = IRoleReference.encodeStringForLegacyApi(null, name)
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = name
    override fun getNameOrId(): String = name
    override fun matches(other: IReferenceLinkReference): Boolean {
        return when (other) {
            is ReferenceLinkReferenceByIdAndName -> name == other.name
            is ReferenceLinkReferenceByName -> name == other.name
            is ReferenceLinkReferenceByUID -> false
            is UnclassifiedReferenceLinkReference -> name == other.value
        }
    }
}

@Serializable
data class ReferenceLinkReferenceByUID(val uid: String) : AbstractReferenceLinkReference(), IRoleReferenceByUID {
    override fun stringForLegacyApi() = IRoleReference.encodeStringForLegacyApi(uid, null)
    override fun getUID(): String = uid
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = uid
    override fun matches(other: IReferenceLinkReference): Boolean {
        return when (other) {
            is ReferenceLinkReferenceByIdAndName -> uid == other.uid
            is ReferenceLinkReferenceByName -> false
            is ReferenceLinkReferenceByUID -> uid == other.uid
            is UnclassifiedReferenceLinkReference -> uid == other.value
        }
    }
}

@Serializable
data class ReferenceLinkReferenceByIdAndName(val uid: String, override val name: String) : AbstractReferenceLinkReference(), IRoleReferenceByUID, IRoleReferenceByName {
    override fun stringForLegacyApi() = IRoleReference.encodeStringForLegacyApi(uid, name)
    override fun getUID(): String = uid
    override fun getSimpleName(): String = name
    override fun getIdOrName(): String = uid
    override fun getNameOrId(): String = name
    override fun matches(other: IReferenceLinkReference): Boolean {
        return when (other) {
            is ReferenceLinkReferenceByIdAndName -> uid == other.uid
            is ReferenceLinkReferenceByName -> name == other.name
            is ReferenceLinkReferenceByUID -> uid == other.uid
            is UnclassifiedReferenceLinkReference -> uid == other.value || name == other.value
        }
    }
}

data class ReferenceLinkFromName(override val name: String) : LinkFromName(), IReferenceLink {
    override fun toReference(): IReferenceLinkReference = UnclassifiedReferenceLinkReference(name)
}

package org.modelix.model.api

import kotlinx.serialization.Serializable

/**
 * Representation of a non-containment reference link between [IConcept]s.
 */
@Deprecated("Use IReferenceLinkReference or IReferenceLinkDefinition")
interface IReferenceLink : ILink, IReferenceLinkDefinition {

    override fun toReference(): IReferenceLinkReference = IReferenceLinkReference.fromIdAndName(getUID(), getSimpleName())

    companion object {
        fun fromName(name: String): IReferenceLink = ReferenceLinkFromName(name)
    }
}

sealed interface IReferenceLinkDefinition : ILinkDefinition {
    override fun toReference(): IReferenceLinkReference
}

@Serializable
sealed interface IReferenceLinkReference : ILinkReference {

    override fun toLegacy(): IReferenceLink

    companion object {
        /**
         * Can be a name or UID or anything else. INode will decide how to resolve it.
         */
        fun fromUnclassifiedString(value: String): IReferenceLinkReference = UnclassifiedReferenceLinkReference(value)
        fun fromName(value: String): IReferenceLinkReference = ReferenceLinkReferenceByName(value)
        fun fromId(value: String): IReferenceLinkReference = ReferenceLinkReferenceByUID(value)
        fun fromIdAndName(id: String?, name: String?): IReferenceLinkReference {
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
    override fun toReference(): IReferenceLinkReference = UnclassifiedReferenceLinkReference(name)
}

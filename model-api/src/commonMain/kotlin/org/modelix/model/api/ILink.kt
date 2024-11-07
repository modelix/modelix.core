package org.modelix.model.api

/**
 * Representation of a link between two [IConcept]s.
 * A link belongs to one concept and targets another.
 * It can be an [IChildLink] or an [IReferenceLink].
 */
@Deprecated("Use ILinkReference or ILinkDefinition")
interface ILink : IRole, ILinkDefinition {
    /**
     * The concept targeted by this link.
     */
    override val targetConcept: IConcept
}

sealed interface ILinkDefinition : IRoleDefinition {
    val targetConcept: IConcept
    override fun toReference(): ILinkReference
}

sealed interface ILinkReference : IRoleReference {
    override fun toLegacy(): ILink
}

abstract class LinkFromName : RoleFromName(), ILink {
    override val targetConcept: IConcept
        get() = throw UnsupportedOperationException()
}

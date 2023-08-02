package org.modelix.model.api

/**
 * Representation of a link between two [IConcept]s.
 * A link belongs to one concept and targets another.
 * It can be an [IChildLink] or an [IReferenceLink].
 */
interface ILink : IRole {
    /**
     * The concept targeted by this link.
     */
    val targetConcept: IConcept
}

abstract class LinkFromName : RoleFromName(), ILink {
    override val targetConcept: IConcept
        get() = throw UnsupportedOperationException()
}

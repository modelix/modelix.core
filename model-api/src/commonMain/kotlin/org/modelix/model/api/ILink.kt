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

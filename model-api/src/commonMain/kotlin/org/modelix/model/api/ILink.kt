package org.modelix.model.api

interface ILink : IRole {
    val isOptional: Boolean
    val targetConcept: IConcept
}

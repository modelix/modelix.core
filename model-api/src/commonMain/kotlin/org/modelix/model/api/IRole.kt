package org.modelix.model.api

interface IRole {
    fun getConcept(): IConcept
    fun getUID(): String
    val name: String
}

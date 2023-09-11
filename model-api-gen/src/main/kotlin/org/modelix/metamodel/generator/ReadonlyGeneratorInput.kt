package org.modelix.metamodel.generator

import org.modelix.model.data.PropertyType

@RequiresOptIn(message = "This API is experimental. It may be changed in the future without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class UnstableGeneratorFeature
interface ReadonlyProcessedLanguageSet {
    fun getLanguages(): List<ReadonlyProcessedLanguage>
}

interface ReadonlyProcessedLanguage {
    val name: String
    val languageSet: ReadonlyProcessedLanguageSet
    fun getConcepts(): List<ReadonlyProcessedConcept>
}

interface ReadonlyProcessedConcept : ReadonlyProcessedDeprecatable {
    val name: String
    val uid: String?
    val language: ReadonlyProcessedLanguage
    fun fqName(): String

    fun getDirectSuperConcepts(): Sequence<ReadonlyProcessedConcept>
    fun getAllSuperConceptsAndSelf(): Sequence<ReadonlyProcessedConcept>
    fun getDuplicateSuperConcepts(): List<ReadonlyProcessedConcept>

    fun getOwnRoles(): List<ReadonlyProcessedRole>
}

sealed interface ReadonlyProcessedRole : ReadonlyProcessedDeprecatable {
    val originalName: String
    val uid: String?
    val optional: Boolean
    val generatedName: String
}

sealed interface ReadonlyProcessedProperty : ReadonlyProcessedRole {
    val type: PropertyType
}

sealed interface ReadonlyProcessedLink :
    ReadonlyProcessedRole {
    val type: ReadonlyProcessedConceptReference
}

sealed interface ReadonlyProcessedChildLink : ReadonlyProcessedLink {
    val multiple: Boolean
}

sealed interface ReadonlyProcessedReferenceLink :
    ReadonlyProcessedLink

sealed interface ReadonlyProcessedConceptReference {
    val resolved: ReadonlyProcessedConcept
    val name: String
}

interface ReadonlyProcessedDeprecatable {
    val deprecationMessage: String?
}

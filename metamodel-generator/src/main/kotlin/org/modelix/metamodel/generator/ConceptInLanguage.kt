package org.modelix.metamodel.generator

class ConceptInLanguage(val concept: ConceptData, val language: LanguageData) {
    val fqName: String get() = language.name + "." + concept.name
    fun getConceptFqName() = fqName
}

fun LanguageData.getConceptsInLanguage() = concepts.map { ConceptInLanguage(it, this) }
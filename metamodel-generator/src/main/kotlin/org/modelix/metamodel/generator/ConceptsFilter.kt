package org.modelix.metamodel.generator

class ConceptsFilter(allLanguages_: List<LanguageData>) {
    private val allLanguages: Map<String, LanguageData> = allLanguages_.associateBy { it.name }
    private val allConcepts: Map<String, ConceptInLanguage> = allLanguages_.flatMap { lang -> lang.concepts.map { ConceptInLanguage(it, lang) } }.associateBy { it.fqName }
    private val includedConcepts: MutableSet<String> = HashSet()
    private val includedLanguages: MutableSet<String> = HashSet()

    fun includeConcept(fqName: String) {
        if (includedConcepts.contains(fqName)) return
        includedConcepts.add(fqName)
        includedLanguages.add(fqName.substringBeforeLast("."))
        val concept = allConcepts[fqName] ?: return
        concept.concept.extends.forEach { includeConcept(resolveRelativeConcept(concept.language, it)) }
        concept.concept.children.forEach { includeConcept(resolveRelativeConcept(concept.language, it.type)) }
        concept.concept.references.forEach { includeConcept(resolveRelativeConcept(concept.language, it.type)) }
    }

    fun isConceptIncluded(conceptFqName: String): Boolean = includedConcepts.contains(conceptFqName)
    fun isLanguageIncluded(langName: String): Boolean = includedLanguages.contains(langName)

    private fun resolveRelativeConcept(contextLanguage: LanguageData, conceptName: String): String {
        return if (conceptName.contains(".")) conceptName else contextLanguage.name + "." + conceptName
    }
}

private class ConceptInLanguage(val concept: ConceptData, val language: LanguageData) {
    val fqName: String get() = language.name + "." + concept.name
}
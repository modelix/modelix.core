package org.modelix.metamodel.generator

import org.modelix.model.api.IConcept

class LanguageSet(languages: List<LanguageData>) {
    private val languagesMap = HashMap<String, LanguageInSet>()
    private val conceptsMap = HashMap<String, ConceptInLanguage>()

    init {
        languages.forEach { lang ->
            languagesMap[lang.name] = LanguageInSet(lang)
            lang.concepts.map { ConceptInLanguage(it, lang) }.forEach { conceptsMap[it.fqName] = it }
        }

    }

    fun filter(body: ConceptsFilter.()->Unit): LanguageSet {
        return ConceptsFilter(this).also { body(it) }.apply()
    }

    fun resolveConcept(fqName: String): ConceptInLanguage? = conceptsMap[fqName]

    fun getLanguages(): List<LanguageInSet> = languagesMap.values.toList()

    inner class LanguageInSet(val language: LanguageData) {
        val name: String get() = language.name
        fun getConceptsInLanguage() = language.concepts.map { ConceptInLanguage(it, language) }
        fun getLanguageSet() = this@LanguageSet
    }

    inner class ConceptInLanguage(val concept: ConceptData, val language: LanguageData) {
        val fqName: String get() = language.name + "." + concept.name
        val simpleName: String get() = concept.name
        fun getConceptFqName() = fqName
        val uid = concept.uid ?: fqName

        /**
         * Unknown concepts are not included!
         */
        private val resolvedDirectSuperConcepts: List<ConceptInLanguage> by lazy {
            concept.extends.map { it.parseConceptRef(language) }.mapNotNull { conceptsMap[it.toString()] }
        }

        fun extended(): List<ConceptRef> = concept.extends.map { it.parseConceptRef(language) }
        fun extends() = extended()
        fun resolveMultipleInheritanceConflicts(): Map<ConceptInLanguage, ConceptInLanguage> {
            val inheritedFrom = LinkedHashMap<ConceptInLanguage, MutableSet<ConceptInLanguage>>()
            for (superConcept in resolvedDirectSuperConcepts) {
                loadInheritance(superConcept, inheritedFrom)
            }
            return inheritedFrom.filter { it.value.size > 1 }.map { it.key to it.value.first() }.toMap()
        }

        fun allSuperConcepts(): List<ConceptInLanguage> =
            resolvedDirectSuperConcepts.flatMap { listOf(it) + it.allSuperConcepts() }.distinct()

        fun directFeatures(): List<FeatureInConcept> = (concept.properties + concept.children + concept.references)
            .map { FeatureInConcept(this, it) }

        fun allFeatures(): List<FeatureInConcept> = allSuperConcepts().flatMap { it.directFeatures() }.distinct()
        fun directFeaturesAndConflicts(): List<FeatureInConcept> =
            (directFeatures() + resolveMultipleInheritanceConflicts().flatMap { it.key.allFeatures() })
                .distinct().groupBy { it.validName }.values.map { it.first() }

        fun ref() = ConceptRef(language.name, concept.name)
        fun loadInheritance(
            directSuperConcept: ConceptInLanguage,
            inheritedFrom: MutableMap<ConceptInLanguage, MutableSet<ConceptInLanguage>>
        ) {
            for (superConcept in resolvedDirectSuperConcepts) {
                inheritedFrom.computeIfAbsent(superConcept, { LinkedHashSet() }).add(directSuperConcept)
                superConcept.loadInheritance(directSuperConcept, inheritedFrom)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ConceptInLanguage

            if (concept != other.concept) return false
            if (language != other.language) return false

            return true
        }

        override fun hashCode(): Int {
            var result = concept.hashCode()
            result = 31 * result + language.hashCode()
            return result
        }
    }
}

class ConceptRef(val languageName: String, val conceptName: String) {
    init {
        require(!conceptName.contains(".")) { "Simple name expected for concept: $conceptName" }
    }
    override fun toString(): String = languageName + "." + conceptName
}

private val reservedPropertyNames: Set<String> = setOf(
    "constructor", // already exists on JS objects
) + IConcept::class.members.map { it.name }

data class FeatureInConcept(val concept: LanguageSet.ConceptInLanguage, val data: IConceptFeatureData) {
    val validName: String = if (reservedPropertyNames.contains(data.name)) data.name + "_" else data.name
    val originalName: String = data.name
}

fun String.parseConceptRef(contextLanguage: LanguageData): ConceptRef {
    return if (this.contains(".")) {
        ConceptRef(this.substringBeforeLast("."), this.substringAfterLast("."))
    } else {
        ConceptRef(contextLanguage.name, this)
    }
}

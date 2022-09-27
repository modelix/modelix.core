package org.modelix.model.api

interface ILanguageRepository {
    companion object {
        val default = DefaultLanguageRepository
        private var repositories: Set<ILanguageRepository> = setOf(DefaultLanguageRepository)

        fun resolveConcept(ref: IConceptReference): IConcept {
            val concepts = repositories.mapNotNull { it.resolveConcept(ref.getUID()) }
            return when (concepts.size) {
                0 -> {
                    throw RuntimeException("Concept not found: $ref")
                }
                1 -> concepts.first()
                else -> throw RuntimeException("Multiple concepts found for $ref: $concepts")
            }
        }
        fun register(repository: ILanguageRepository) {
            repositories += repository
        }
        fun unregister(repository: ILanguageRepository) {
            repositories -= repository
        }
    }

    fun resolveConcept(uid: String): IConcept?
}

object DefaultLanguageRepository : ILanguageRepository {
    private val languages: MutableMap<String, ILanguage> = HashMap()
    private val concepts: MutableMap<String, IConcept> = HashMap()

    fun registerLanguage(language: ILanguage) {
        languages[language.getUID()] = language
        concepts += language.getConcepts().associateBy { it.getUID() }
    }

    fun unregisterLanguage(language: ILanguage) {
        languages -= language.getUID()
        concepts -= language.getConcepts().map { it.getUID() }.toSet()
    }

    fun registerConcept(concept: IConcept) {
        val existing = concepts[concept.getUID()]
        require(existing == null || existing == concept) {
            "Concept with UID ${concept.getUID()} already exists: $existing, $concept"
        }
        concepts[concept.getUID()] = concept
        concept.language?.let { languages[it.getUID()] = it }
    }

    override fun resolveConcept(uid: String): IConcept? {
        return concepts[uid]
    }
}

package org.modelix.model.api

// TODO make it thread-safe
interface ILanguageRepository {
    companion object {
        val default = DefaultLanguageRepository
        private var repositories: Set<ILanguageRepository> = setOf(DefaultLanguageRepository)
        private var subconceptsCache: Map<IConcept, Set<IConcept>>? = null

        fun resolveConcept(ref: IConceptReference): IConcept {
            return tryResolveConcept(ref) ?: throw RuntimeException("Concept not found: $ref")
        }
        fun tryResolveConcept(ref: IConceptReference): IConcept? {
            val concepts = repositories.mapNotNull { it.resolveConcept(ref.getUID()) }
            return when (concepts.size) {
                0 -> null
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

        fun getDirectSubConcepts(superConcept: IConcept): Set<IConcept> {
            val cache = loadSubConceptsCache()
            return cache[superConcept] ?: emptySet()
        }

        private fun loadSubConceptsCache(): Map<IConcept, Set<IConcept>> {
            // TODO invalidate when new concepts are registered
            subconceptsCache?.let { return it }
            val cache = HashMap<IConcept, MutableSet<IConcept>>()
            for (concept in repositories.asSequence().flatMap { it.getAllConcepts() }) {
                for (superConcept in concept.getDirectSuperConcepts()) {
                    cache.getOrPut(superConcept, { HashSet() }).add(concept)
                }
            }
            subconceptsCache = cache
            return cache
        }
    }

    fun resolveConcept(uid: String): IConcept?
    fun getAllConcepts(): List<IConcept>
}

fun IConcept.getDirectSubConcepts() = ILanguageRepository.getDirectSubConcepts(this)
fun IConcept.getAllSubConcepts(includeSelf: Boolean) = getAllSubConceptsIncludingDuplicates(includeSelf).toSet()
fun IConcept.getInstantiatableSubConcepts() = getAllSubConcepts(true).filterNot { it.isAbstract() }
private fun IConcept.getAllSubConceptsIncludingDuplicates(includeSelf: Boolean): Sequence<IConcept> {
    return if (includeSelf) {
        sequenceOf(this) + getAllSubConceptsIncludingDuplicates(false)
    } else {
        getDirectSubConcepts().asSequence().flatMap { it.getAllSubConceptsIncludingDuplicates(true) }
    }
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

    override fun getAllConcepts(): List<IConcept> {
        return concepts.values.toList()
    }
}

package org.modelix.model.api

/**
 * An [ILanguageRepository] contains languages and their corresponding concepts.
 */ // TODO make it thread-safe
interface ILanguageRepository {
    companion object {
        val default = DefaultLanguageRepository
        private var repositories: Set<ILanguageRepository> = setOf(DefaultLanguageRepository)
        private var subconceptsCache: Map<IConcept, Set<IConcept>>? = null

        /**
         * Resolves a concept within the registered language repositories.
         *
         * @param ref concept reference to the desired concept
         * @return resolved concept
         * @throws RuntimeException if the concept could not be found
         *          or multiple concepts were found for the given reference
         */
        fun resolveConcept(ref: IConceptReference): IConcept {
            return tryResolveConcept(ref) ?: throw RuntimeException("Concept not found: $ref")
        }

        /**
         * Tries to resolve a concept within the registered language repositories.
         *
         * @param ref concept reference to the desired concept
         * @return resolved concept or null, if the concept could not be found
         * @throws RuntimeException if multiple concepts were found for the given reference
         */
        fun tryResolveConcept(ref: IConceptReference): IConcept? {
            val concepts = repositories.mapNotNull { it.resolveConcept(ref.getUID()) }
            return when (concepts.size) {
                0 -> null
                1 -> concepts.first()
                else -> throw RuntimeException("Multiple concepts found for $ref: $concepts")
            }
        }

        /**
         * Registers the given repository.
         *
         * @param repository the repository to be registered
         */
        fun register(repository: ILanguageRepository) {
            repositories += repository
        }

        /**
         * Unregisters the given repository.
         *
         * @param repository the repository to be unregistered
         */
        fun unregister(repository: ILanguageRepository) {
            repositories -= repository
        }

        /**
         * Returns the direct sub-concepts of the given concept.
         *
         * @param superConcept the given concept
         * @return set of direct sub-concepts or an empty set, if the concept could not be found
         */
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

    /**
     * Resolves a concept within this language repository.
     *
     * @param uid uid of the concept to be resolved
     * @return resolved concept or null, if the concept could not be found
     */
    fun resolveConcept(uid: String): IConcept?

    /**
     * Returns a list of all concepts within this language repository.
     *
     * @return list of all concepts
     */
    fun getAllConcepts(): List<IConcept>
}

/**
 * Returns the direct sub-concepts of the receiver concept.
 *
 * @receiver the given concept
 * @return set of direct sub-concepts
 */
fun IConcept.getDirectSubConcepts() = ILanguageRepository.getDirectSubConcepts(this)

/**
 * Returns all sub-concepts (direct and indirect) of the receiver concept.
 *
 * @receiver the given concept
 * @param includeSelf determines whether the receiver will be included in the returned set
 * @return set of all sub-concepts
 */
fun IConcept.getAllSubConcepts(includeSelf: Boolean) = getAllSubConceptsIncludingDuplicates(includeSelf).toSet()

/**
 * Returns all instantiable sub-concepts (direct and indirect) of the receiver concept.
 *
 * A sub-concept is considered instantiable iff it is not abstract.
 *
 * @receiver the given concept
 * @return set of all instantiable sub-concepts (including the receiver concept if it is not abstract)
 */
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

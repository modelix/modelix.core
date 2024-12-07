package org.modelix.model.api

open class SimpleLanguage(private val name: String, private val uid: String? = null) : ILanguage {
    private var registered: Boolean = false
    fun register() {
        if (registered) return
        ILanguageRepository.default.registerLanguage(this)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        ILanguageRepository.default.registerLanguage(this)
        registered = false
    }

    override fun getUID(): String = uid ?: name

    private val concepts: MutableList<SimpleConcept> = ArrayList()

    override fun getConcepts(): List<SimpleConcept> = concepts

    fun addConcept(concept: SimpleConcept) {
        val currentLang = concept.language
        if (currentLang != null) throw IllegalStateException("concept ${concept.getShortName()} was already added to language ${currentLang.getName()}")
        concept.language = this
        concepts.add(concept)
        if (registered) {
            ILanguageRepository.default.registerConcept(concept)
        }
    }

    override fun getName() = name

    // just a dummy field to make sure that the (lazy) concepts are registered automatically
    protected open var includedConcepts: Array<SimpleConcept> = arrayOf()
}

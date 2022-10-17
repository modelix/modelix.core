package org.modelix.metamodel

import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.resolve

object TypedLanguagesRegistry : ILanguageRepository {
    private var languages: Map<String, GeneratedLanguage> = emptyMap()
    private var concepts: Map<String, GeneratedConcept<*, *>> = emptyMap()

    init {
        ILanguageRepository.register(this)
    }

    fun dispose() {
        ILanguageRepository.unregister(this)
    }

    fun register(language: GeneratedLanguage) {
        languages += language.getUID() to language
        concepts += language.getConcepts().filterIsInstance<GeneratedConcept<*, *>>().associateBy { it.getUID() }
    }

    fun unregister(language: GeneratedLanguage) {
        languages -= language.getUID()
        concepts -= language.getConcepts().map { it.getUID() }
    }

    fun isRegistered(language: GeneratedLanguage) = languages[language.getUID()] == language

    override fun resolveConcept(uid: String): GeneratedConcept<*, *>? {
        return concepts[uid]
    }

    fun wrapNode(node: INode): ITypedNode {
        val concept = (node.getConceptReference()?.resolve() as? GeneratedConcept<*, *>)
            ?: throw IllegalArgumentException("Unknown concept: ${node.getConceptReference()}")
        return concept.wrap(node)
    }
}

fun INode.typed(): ITypedNode = TypedLanguagesRegistry.wrapNode(this)
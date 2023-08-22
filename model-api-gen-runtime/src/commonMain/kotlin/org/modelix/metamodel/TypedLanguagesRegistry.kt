package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.tryResolve
import kotlin.js.JsExport
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.reflect.cast

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

    override fun getAllConcepts(): List<IConcept> {
        return concepts.values.toList()
    }

    fun wrapNode(node: INode): ITypedNode {
        val concept = (node.getConceptReference()?.tryResolve() as? GeneratedConcept<*, *>)
            ?: return UnknownConceptInstance(node)
        return concept.wrap(node)
    }
}

@JsExport
fun <NodeT : ITypedNode> INode.typed(concept: IConceptOfTypedNode<NodeT>): NodeT = typed(concept.getInstanceInterface())

fun <NodeT : ITypedNode> INode.typed(nodeClass: KClass<NodeT>): NodeT = nodeClass.cast(TypedLanguagesRegistry.wrapNode(this))
inline fun <reified NodeT : ITypedNode> INode.typed(): NodeT = TypedLanguagesRegistry.wrapNode(this) as NodeT
fun <NodeT : ITypedNode> INode.typedUnsafe(): NodeT = TypedLanguagesRegistry.wrapNode(this) as NodeT

@JvmName("asTypedNode")
fun INode.typed(): ITypedNode = TypedLanguagesRegistry.wrapNode(this)

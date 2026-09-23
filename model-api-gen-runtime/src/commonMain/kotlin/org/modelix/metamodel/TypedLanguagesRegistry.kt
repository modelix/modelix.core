package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.tryResolve
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

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

    override fun getPriority(): Int = 2000
}

/**
 * Wraps this node into the typed node class [nodeClass].
 *
 * @throws UnknownConceptException if the node's concept is not provided by any registered generated
 *         language, or resolves to a concept that is not an instance of [nodeClass]. The exception
 *         extends [ClassCastException] and its message names the node, its concept UID and [nodeClass].
 */
fun <NodeT : ITypedNode> INode.typed(nodeClass: KClass<out NodeT>): NodeT =
    typedOrNull(nodeClass) ?: throw UnknownConceptException.of(this, nodeClass)

/**
 * Wraps this node into the typed node class [NodeT] if its concept is provided by a registered
 * generated language, and returns `null` otherwise.
 *
 * This is the lenient counterpart of [typed]. It is the building block of the lenient accessors
 * ([ChildAccessor.known], [ChildAccessor.unknown]) and lets callers decide how to handle nodes whose
 * concept the reading application doesn't know.
 */
fun <NodeT : ITypedNode> INode.typedOrNull(nodeClass: KClass<out NodeT>): NodeT? =
    nodeClass.safeCast(TypedLanguagesRegistry.wrapNode(this))

/**
 * @see typed
 */
inline fun <reified NodeT : ITypedNode> INode.typed(): NodeT = typed(NodeT::class)

/**
 * @see typedOrNull
 */
inline fun <reified NodeT : ITypedNode> INode.typedOrNull(): NodeT? = typedOrNull(NodeT::class)

/**
 * Wraps this node without checking that the result is actually a [NodeT].
 *
 * The unchecked cast is erased on the JVM, so a mismatch only surfaces later, at an arbitrary usage
 * site. Prefer [typed] (strict, diagnosable) or [typedOrNull] (lenient).
 */
fun <NodeT : ITypedNode> INode.typedUnsafe(): NodeT = TypedLanguagesRegistry.wrapNode(this) as NodeT

@JvmName("asTypedNode")
fun INode.typed(): ITypedNode = TypedLanguagesRegistry.wrapNode(this)

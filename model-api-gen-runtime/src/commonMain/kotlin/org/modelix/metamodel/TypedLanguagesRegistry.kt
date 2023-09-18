/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.tryResolve
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

    override fun getPriority(): Int = 2000
}

fun <NodeT : ITypedNode> INode.typed(nodeClass: KClass<NodeT>): NodeT = nodeClass.cast(TypedLanguagesRegistry.wrapNode(this))
inline fun <reified NodeT : ITypedNode> INode.typed(): NodeT = TypedLanguagesRegistry.wrapNode(this) as NodeT
fun <NodeT : ITypedNode> INode.typedUnsafe(): NodeT = TypedLanguagesRegistry.wrapNode(this) as NodeT

@JvmName("asTypedNode")
fun INode.typed(): ITypedNode = TypedLanguagesRegistry.wrapNode(this)

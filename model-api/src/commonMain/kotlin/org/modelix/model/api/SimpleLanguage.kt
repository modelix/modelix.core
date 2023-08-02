/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
}

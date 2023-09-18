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
package org.modelix.metamodel.generator

import org.modelix.model.data.LanguageData

class ConceptsFilter(val languageSet: LanguageSet) {
    private val includedConcepts: MutableSet<String> = HashSet()
    private val includedLanguages: MutableSet<String> = HashSet()

    fun includeConcept(fqName: String) {
        if (includedConcepts.contains(fqName)) return
        includedConcepts.add(fqName)
        includedLanguages.add(fqName.substringBeforeLast("."))
        val concept = languageSet.resolveConcept(fqName) ?: return
        concept.concept.extends.forEach { includeConcept(resolveRelativeConcept(concept.language, it)) }
        concept.concept.children.forEach { includeConcept(resolveRelativeConcept(concept.language, it.type)) }
        concept.concept.references.forEach { includeConcept(resolveRelativeConcept(concept.language, it.type)) }
    }

    fun isConceptIncluded(conceptFqName: String): Boolean = includedConcepts.contains(conceptFqName)
    fun isLanguageIncluded(langName: String): Boolean = includedLanguages.contains(langName)

    fun apply(): LanguageSet {
        return LanguageSet(
            languageSet.getLanguages()
                .filter { includedLanguages.contains(it.name) }
                .map { lang ->
                    LanguageData(
                        lang.language.uid,
                        lang.name,
                        lang.getConceptsInLanguage().filter { includedConcepts.contains(it.fqName) }.map { it.concept },
                        lang.language.enums,
                    )
                },
        )
    }

    private fun resolveRelativeConcept(contextLanguage: LanguageData, conceptName: String): String {
        return if (conceptName.contains(".")) conceptName else contextLanguage.name + "." + conceptName
    }
}

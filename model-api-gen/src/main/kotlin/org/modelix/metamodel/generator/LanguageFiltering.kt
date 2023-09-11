/*
 * Copyright (c) 2023.
 *
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

package org.modelix.metamodel.generator

import org.modelix.model.data.LanguageData

fun processLanguageData(
    languagesData: List<LanguageData>,
    includedNamespaces: List<String>,
    includedLanguages: List<String>,
    includedConcepts: List<String>,
): ReadonlyProcessedLanguageSet {
    var languages = LanguageSet(languagesData)
    val previousLanguageCount = languages.getLanguages().size

    val includedNamespacesNormalized = includedNamespaces.map { it.trimEnd('.') }
    val includedLanguagesAndNS = includedLanguages + includedNamespacesNormalized
    val namespacePrefixes = includedNamespacesNormalized.map { it + "." }

    languages = languages.filter {
        languages.getLanguages().filter { lang ->
            includedLanguagesAndNS.contains(lang.name) ||
                namespacePrefixes.any { lang.name.startsWith(it) }
        }.forEach { lang ->
            lang.getConceptsInLanguage().forEach { concept ->
                includeConcept(concept.fqName)
            }
        }
        includedConcepts.forEach { includeConcept(it) }
    }

    val missingLanguages = includedLanguages - languages.getLanguages().map { it.name }.toSet()
    val missingConcepts =
        includedConcepts - languages.getLanguages().flatMap { it.getConceptsInLanguage() }.map { it.fqName }.toSet()

    if (missingLanguages.isNotEmpty() || missingConcepts.isNotEmpty()) {
        throw RuntimeException("The following languages or concepts were not found: " + (missingLanguages + missingConcepts))
    }

    println("${languages.getLanguages().size} of $previousLanguageCount languages included")

    return languages.process()
}

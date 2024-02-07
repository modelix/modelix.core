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

package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.language.LanguageRegistry
import jetbrains.mps.smodel.runtime.illegal.IllegalConceptDescriptor
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguageRepository

data class MPSLanguageRepository(private val repository: SRepository) : ILanguageRepository {

    fun resolveMPSConcept(uid: String): SAbstractConcept? = resolveConcept(uid)?.concept

    override fun resolveConcept(uid: String): MPSConcept? {
        if (!uid.startsWith("mps:")) return null

        val conceptId = try {
            SConceptId.deserialize(uid.substring(4))
        } catch (e: NumberFormatException) { return null } ?: return null // in case the id cannot be parsed

        val conceptDescriptor = ConceptRegistry.getInstance().getConceptDescriptor(conceptId)

        if (conceptDescriptor is IllegalConceptDescriptor) return null

        return MPSConcept(MetaAdapterFactory.getAbstractConcept(conceptDescriptor))
    }

    override fun getAllConcepts(): List<IConcept> {
        val result = mutableListOf<IConcept>()
        LanguageRegistry.getInstance(repository).withAvailableLanguages { language ->
            result.addAll(language.identity.concepts.map { MPSConcept(it) }.toList())
        }
        return result
    }

    override fun getPriority(): Int = 1000
}

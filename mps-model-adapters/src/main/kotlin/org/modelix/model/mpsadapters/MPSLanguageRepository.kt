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

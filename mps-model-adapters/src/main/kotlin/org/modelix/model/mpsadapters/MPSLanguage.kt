package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import org.jetbrains.mps.openapi.language.SLanguage
import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguage

data class MPSLanguage(val language: SLanguage) : ILanguage {
    override fun getUID(): String {
        return MetaIdHelper.getLanguage(language).serialize()
    }

    override fun getName(): String {
        return language.qualifiedName
    }

    override fun getConcepts(): List<IConcept> {
        return language.concepts.map { MPSConcept(it) }
    }
}

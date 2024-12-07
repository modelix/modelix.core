package org.modelix.model.api.meta

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.ILanguage

object NullConcept : EmptyConcept() {
    override fun getReference(): ConceptReference = ConceptReference(getUID())

    override val language: ILanguage?
        get() = null

    override fun getUID(): String = "null"

    override fun getShortName(): String = "null"

    override fun getLongName(): String = getShortName()
}

package org.modelix.model.metameta

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguage
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.area.IArea

data class PersistedConcept(val id: Long, val uid: String?) : IConcept, IConceptReference {
    override val language: ILanguage?
        get() = throw UnsupportedOperationException()

    override fun getChildLink(name: String): IChildLink {
        throw UnsupportedOperationException()
    }

    override fun getDirectSuperConcepts(): List<IConcept> {
        throw UnsupportedOperationException()
    }

    override fun getLongName(): String {
        throw UnsupportedOperationException()
    }

    override fun getProperty(name: String): IProperty {
        throw UnsupportedOperationException()
    }

    override fun getReference(): ConceptReference {
        throw UnsupportedOperationException()
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        throw UnsupportedOperationException()
    }

    override fun getShortName(): String {
        throw UnsupportedOperationException()
    }

    override fun getUID(): String {
        return uid!!
    }

    override fun isAbstract(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun isExactly(concept: IConcept?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun isSubConceptOf(superConcept: IConcept?): Boolean {
        throw UnsupportedOperationException()
    }

    @Deprecated("use ILanguageRepository.resolveConcept")
    override fun resolve(area: IArea?): IConcept? {
        throw UnsupportedOperationException()
    }

    @Deprecated("use getUID()")
    override fun serialize(): String {
        TODO("Not yet implemented")
    }

    override fun getAllChildLinks(): List<IChildLink> {
        throw UnsupportedOperationException()
    }

    override fun getAllProperties(): List<IProperty> {
        throw UnsupportedOperationException()
    }

    override fun getAllReferenceLinks(): List<IReferenceLink> {
        throw UnsupportedOperationException()
    }

    override fun getOwnChildLinks(): List<IChildLink> {
        throw UnsupportedOperationException()
    }

    override fun getOwnProperties(): List<IProperty> {
        throw UnsupportedOperationException()
    }

    override fun getOwnReferenceLinks(): List<IReferenceLink> {
        throw UnsupportedOperationException()
    }
}

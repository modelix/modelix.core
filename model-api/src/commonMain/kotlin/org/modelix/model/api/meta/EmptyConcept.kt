package org.modelix.model.api.meta

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink

abstract class EmptyConcept : IConcept {
    override fun isAbstract(): Boolean = true

    override fun isSubConceptOf(superConcept: IConcept?): Boolean = superConcept == this

    override fun getDirectSuperConcepts(): List<IConcept> = emptyList()

    override fun isExactly(concept: IConcept?): Boolean = concept == this

    override fun getOwnProperties(): List<IProperty> = emptyList()

    override fun getOwnChildLinks(): List<IChildLink> = emptyList()

    override fun getOwnReferenceLinks(): List<IReferenceLink> = emptyList()

    override fun getAllProperties(): List<IProperty> = emptyList()

    override fun getAllChildLinks(): List<IChildLink> = emptyList()

    override fun getAllReferenceLinks(): List<IReferenceLink> = emptyList()

    override fun getProperty(name: String): IProperty {
        throw IllegalArgumentException("Cannot get property '$name'. No concept information available for '${getUID()}'.")
    }

    override fun getChildLink(name: String): IChildLink {
        throw IllegalArgumentException("Cannot get link '$name'. No concept information available for '${getUID()}'.")
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        throw IllegalArgumentException("Cannot get link '$name'. No concept information available for '${getUID()}'.")
    }
}

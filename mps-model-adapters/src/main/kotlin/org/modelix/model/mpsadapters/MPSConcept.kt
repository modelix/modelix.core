package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.structure.concept.SAbstractConceptAdapter
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.adapter.structure.concept.SInterfaceConceptAdapterById
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SInterfaceConcept
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguage
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink

data class MPSConcept(val concept: SAbstractConceptAdapter) : IConcept {
    constructor(concept: SAbstractConcept) : this(concept as SAbstractConceptAdapter)
    override fun getReference(): ConceptReference {
        return ConceptReference(getUID())
    }

    override val language: ILanguage
        get() = MPSLanguage(concept.language)

    override fun getUID(): String {
        val id: SConceptId = when (concept) {
            is SConceptAdapterById -> concept.id
            is SInterfaceConceptAdapterById -> concept.id
            else -> error("Unknown concept type: $concept")
        }
        return UID_PREFIX + id.serialize()
    }

    override fun getShortName(): String {
        return concept.name
    }

    override fun getLongName(): String {
        return concept.language.qualifiedName + "." + concept.name
    }

    override fun isAbstract(): Boolean {
        // In MPS `org.jetbrains.mps.openapi.language.SAbstractConcept.isAbstract`
        // returns `true` for abstract concepts and interface concepts.
        // See https://github.com/JetBrains/MPS/blob/78b81f56866370e227262000e597a211f885b9e6/core/kernel/source/jetbrains/mps/smodel/adapter/structure/concept/SConceptAdapterById.java#L54
        // This exactly matches with the definition of `IConcept.isAbstract`,
        // as such concepts are not designated to be instantiated directly.
        return concept.isAbstract
    }

    override fun isSubConceptOf(superConcept: IConcept?): Boolean {
        if (superConcept == null) return false
        if (isExactly(superConcept)) return true
        if (superConcept is MPSConcept) {
            // Use the MPS logic if possible, because it's faster (super concepts are cached in a set).
            return concept.isSubConceptOf(superConcept.concept)
        } else {
            for (c in getDirectSuperConcepts()) {
                if (c.isSubConceptOf(superConcept)) return true
            }
        }
        return false
    }

    override fun getDirectSuperConcepts(): List<IConcept> {
        return when (concept) {
            is SConcept -> listOfNotNull<SAbstractConcept>(ConceptWorkaround(concept).superConcept) +
                ConceptWorkaround(concept).superInterfaces
            is SInterfaceConcept -> ConceptWorkaround(concept).superInterfaces
            else -> emptyList<SAbstractConcept>()
        }.map { MPSConcept(it) }
    }

    override fun isExactly(concept: IConcept?): Boolean {
        if (concept == null) return false
        if (concept == this) return true
        return concept.getUID() == getUID()
    }

    override fun getOwnProperties(): List<IProperty> {
        return concept.properties.filter { it.owner == concept }.map { MPSProperty(it) }
    }

    override fun getOwnChildLinks(): List<IChildLink> {
        return concept.containmentLinks.filter { it.owner == concept }.map { MPSChildLink(it) }
    }

    override fun getOwnReferenceLinks(): List<IReferenceLink> {
        return concept.referenceLinks.filter { it.owner == concept }.map { MPSReferenceLink(it) }
    }

    override fun getAllProperties(): List<IProperty> {
        return concept.properties.map { MPSProperty(it) }
    }

    override fun getAllChildLinks(): List<IChildLink> {
        return concept.containmentLinks.map { MPSChildLink(it) }
    }

    override fun getAllReferenceLinks(): List<IReferenceLink> {
        return concept.referenceLinks.map { MPSReferenceLink(it) }
    }

    override fun getProperty(name: String): IProperty {
        return MPSProperty(concept.properties.first { it.name == name })
    }

    override fun getChildLink(name: String): IChildLink {
        return MPSChildLink(concept.containmentLinks.first { it.name == name })
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        return MPSReferenceLink(concept.referenceLinks.first { it.name == name })
    }

    override fun getConceptProperty(name: String): String? {
        return when (name) {
            "alias" -> concept.conceptAlias
            else -> null
        }
    }

    companion object {
        private const val UID_PREFIX = "mps:"

        fun tryParseUID(uid: String): MPSConcept? {
            if (!uid.startsWith(UID_PREFIX)) return null
            val conceptId = SConceptId.deserialize(uid.substringAfter(UID_PREFIX))

            // For interface concepts `SInterfaceConceptAdapterById(conceptId, "")` would be correct, but we don't have
            // that information. Assuming that this concept is used to create new nodes SConceptAdapterById is the
            // better default.
            return MPSConcept(SConceptAdapterById(conceptId, ""))
        }
    }
}

fun SAbstractConcept.toModelix() = MPSConcept(this)

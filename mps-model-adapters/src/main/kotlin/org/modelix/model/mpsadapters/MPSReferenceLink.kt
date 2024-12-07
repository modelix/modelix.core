package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapter
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.IReferenceLinkReference

data class MPSReferenceLink(val link: SReferenceLinkAdapter) : IReferenceLink {
    constructor(link: SReferenceLink) : this(link as SReferenceLinkAdapter)
    override fun getConcept(): IConcept {
        return MPSConcept(link.owner)
    }

    override fun getUID(): String {
        return link.id.serialize()
    }

    override fun getSimpleName(): String {
        return link.name
    }

    override val isOptional: Boolean
        get() = link.isOptional

    override val targetConcept: IConcept
        get() = MPSConcept(link.targetConcept)

    override fun toReference(): IReferenceLinkReference = IReferenceLinkReference.fromIdAndName(getUID(), getSimpleName())
}

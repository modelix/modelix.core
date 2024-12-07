package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapter
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept

data class MPSChildLink(val link: SContainmentLinkAdapter) : IChildLink {
    constructor(link: SContainmentLink) : this(link as SContainmentLinkAdapter)
    override val isMultiple: Boolean
        get() = link.isMultiple

    @Deprecated("use .targetConcept", ReplaceWith("targetConcept"))
    override val childConcept: IConcept
        get() = targetConcept
    override val targetConcept: IConcept
        get() = MPSConcept(link.targetConcept)

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

    override fun toReference(): IChildLinkReference = IChildLinkReference.fromIdAndName(getUID(), getSimpleName())
}

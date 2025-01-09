package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.ids.SContainmentLinkId
import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapter
import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapterById
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IRoleReferenceByName
import org.modelix.model.api.IRoleReferenceByUID
import org.modelix.model.api.IUnclassifiedRoleReference

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

    companion object {
        fun tryFromReference(ref: IChildLinkReference): MPSChildLink? {
            val uid = (ref as? IRoleReferenceByUID ?: (ref as? IUnclassifiedRoleReference))?.getUID() ?: return null
            if (!uid.contains('/')) return null // avoid expensive exceptions
            val name = (ref as? IRoleReferenceByName)?.getSimpleName().orEmpty()
            val linkId = try {
                SContainmentLinkId.deserialize(uid)
            } catch (ex: IllegalArgumentException) {
                return null
            } catch (ex: IndexOutOfBoundsException) {
                return null
            }
            return MPSChildLink(SContainmentLinkAdapterById(linkId, name))
        }

        fun fromReference(ref: IChildLinkReference): MPSChildLink = requireNotNull(tryFromReference(ref)) { "Not an MPS child link: $ref" }
    }
}

package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.ids.SReferenceLinkId
import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapter
import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapterById
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReferenceByName
import org.modelix.model.api.IRoleReferenceByUID
import org.modelix.model.api.IUnclassifiedRoleReference

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

    companion object {
        fun tryFromReference(ref: IReferenceLinkReference): MPSReferenceLink? {
            val uid = (ref as? IRoleReferenceByUID ?: (ref as? IUnclassifiedRoleReference))?.getUID() ?: return null
            if (!uid.contains('/')) return null // avoid expensive exceptions
            val name = (ref as? IRoleReferenceByName)?.getSimpleName().orEmpty()
            val linkId = try {
                SReferenceLinkId.deserialize(uid)
            } catch (ex: IllegalArgumentException) {
                return null
            } catch (ex: IndexOutOfBoundsException) {
                return null
            }
            return MPSReferenceLink(SReferenceLinkAdapterById(linkId, name))
        }

        fun fromReference(ref: IReferenceLinkReference): MPSReferenceLink = requireNotNull(tryFromReference(ref)) { "Not an MPS reference link: $ref" }
    }
}

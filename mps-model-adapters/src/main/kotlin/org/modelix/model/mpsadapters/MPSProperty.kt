package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.ids.SPropertyId
import jetbrains.mps.smodel.adapter.structure.property.SPropertyAdapter
import jetbrains.mps.smodel.adapter.structure.property.SPropertyAdapterById
import org.jetbrains.mps.openapi.language.SProperty
import org.modelix.model.api.IConcept
import org.modelix.model.api.IProperty
import org.modelix.model.api.IPropertyDefinition
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IRoleReferenceByName
import org.modelix.model.api.IRoleReferenceByUID
import org.modelix.model.api.IUnclassifiedRoleReference

data class MPSProperty(val property: SPropertyAdapter) : IProperty, IPropertyDefinition {
    constructor(property: SProperty) : this(property as SPropertyAdapter)
    override fun getConcept(): IConcept {
        return MPSConcept(property.owner)
    }

    override fun getUID(): String {
        return property.id.serialize()
    }

    override fun getSimpleName(): String {
        return property.name
    }

    override val isOptional: Boolean
        get() = true

    override fun toReference(): IPropertyReference = IPropertyReference.fromIdAndName(getUID(), getSimpleName())

    companion object {
        fun tryFromReference(ref: IPropertyReference): MPSProperty? {
            val uid = (ref as? IRoleReferenceByUID ?: (ref as? IUnclassifiedRoleReference))?.getUID() ?: return null
            if (!uid.contains('/')) return null // avoid expensive exceptions
            val name = (ref as? IRoleReferenceByName)?.getSimpleName().orEmpty()
            val linkId = try {
                SPropertyId.deserialize(uid)
            } catch (ex: IllegalArgumentException) {
                return null
            } catch (ex: IndexOutOfBoundsException) {
                return null
            }
            return MPSProperty(SPropertyAdapterById(linkId, name))
        }

        fun fromReference(ref: IPropertyReference): MPSProperty = requireNotNull(tryFromReference(ref)) { "Not an MPS property: $ref" }
    }
}

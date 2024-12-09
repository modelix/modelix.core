package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.structure.property.SPropertyAdapter
import org.jetbrains.mps.openapi.language.SProperty
import org.modelix.model.api.IConcept
import org.modelix.model.api.IProperty
import org.modelix.model.api.IPropertyReference

data class MPSProperty(val property: SPropertyAdapter) : IProperty {
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
}

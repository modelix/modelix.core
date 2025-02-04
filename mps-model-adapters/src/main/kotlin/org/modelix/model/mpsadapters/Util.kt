package org.modelix.model.mpsadapters

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.IRole
import org.modelix.model.api.matches

// statically ensures that receiver and parameter are of the same type
internal fun IChildLink.conformsTo(other: IChildLink) = conformsToRole(other)
internal fun IReferenceLink.conformsTo(other: IReferenceLink) = conformsToRole(other)
internal fun IProperty.conformsTo(other: IProperty) = conformsToRole(other)

private fun IRole.conformsToRole(other: IRole): Boolean {
    return this.toReference().matches(other.toReference())
}

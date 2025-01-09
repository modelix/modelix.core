package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.module.SDependencyScope
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.area.IArea

data class MPSModuleDependencyAsNode(
    val moduleReference: SModuleReference,
    val moduleVersion: Int,
    val explicit: Boolean,
    val reexport: Boolean,
    val importer: SModule,
    val dependencyScope: SDependencyScope?,
) : IDefaultNodeAdapter {

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies
    }

    override fun getArea(): IArea {
        return MPSArea(importer.repository ?: MPSModuleRepository.getInstance())
    }

    override val reference: INodeReference
        get() = MPSModuleDependencyReference(
            usedModuleId = moduleReference.moduleId,
            userModuleReference = importer.moduleReference,
        )
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency
    override val parent: INode
        get() = MPSModuleAsNode(importer).asLegacyNode()

    override fun getPropertyValue(property: IProperty): String? {
        val moduleDependency = BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency

        return if (property.conformsTo(moduleDependency.explicit)) {
            explicit.toString()
        } else if (property.conformsTo(moduleDependency.name)) {
            moduleReference.moduleName
        } else if (property.conformsTo(moduleDependency.reexport)) {
            reexport.toString()
        } else if (property.conformsTo(moduleDependency.uuid)) {
            moduleReference.moduleId.toString()
        } else if (property.conformsTo(moduleDependency.version)) {
            moduleVersion.toString()
        } else if (property.conformsTo(moduleDependency.scope)) {
            dependencyScope?.toString() ?: "UNSPECIFIED"
        } else {
            null
        }
    }
}

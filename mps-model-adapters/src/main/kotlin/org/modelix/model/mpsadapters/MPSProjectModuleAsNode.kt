package org.modelix.model.mpsadapters

import jetbrains.mps.project.ProjectBase
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.area.IArea
import org.modelix.mps.api.ModelixMpsApi

data class MPSProjectModuleAsNode(val project: ProjectBase, val module: SModule) : IDefaultNodeAdapter {

    override val reference: INodeReference
        get() = MPSProjectModuleReference(
            projectRef = parent.reference as MPSProjectReference,
            moduleRef = module.moduleReference,
        )
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.ProjectModule
    override val parent: INode
        get() = MPSProjectAsNode(project)

    override val allChildren: Iterable<INode>
        get() = emptyList()

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)) {
            return MPSModuleAsNode(module).asLegacyNode()
        }
        return null
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        return getReferenceTarget(role)?.reference
    }

    override fun getPropertyValue(property: IProperty): String? {
        return if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.virtualFolder)) {
            ModelixMpsApi.getVirtualFolders(module).firstOrNull()
        } else {
            null
        }
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.virtualFolder)) {
            project.setVirtualFolder(module, value)
        }
    }

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules
    }

    override fun getArea(): IArea {
        return MPSArea(project.repository)
    }
}

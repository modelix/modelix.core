package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.area.IArea

data class MPSDevKitDependencyAsNode(
    val moduleReference: SModuleReference,
    val moduleImporter: SModule? = null,
    val modelImporter: SModel? = null,
) : IDefaultNodeAdapter {

    override fun getArea(): IArea {
        val repo = moduleImporter?.repository ?: modelImporter?.repository
        checkNotNull(repo) { "No importer found for $this" }
        return MPSArea(repo)
    }

    override val reference: INodeReference
        get() = MPSDevKitDependencyReference(
            moduleReference.moduleId,
            userModule = moduleImporter?.moduleReference,
            userModel = modelImporter?.reference,
        )
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency
    override val parent: INode
        get() = if (moduleImporter != null) {
            MPSModuleAsNode(moduleImporter)
        } else if (modelImporter != null) {
            MPSModelAsNode(modelImporter)
        } else {
            error("No importer found for $this")
        }

    override fun getContainmentLink(): IChildLink {
        return if (moduleImporter != null) {
            BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies
        } else if (modelImporter != null) {
            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages
        } else {
            error("No importer found for $this")
        }
    }

    override fun getPropertyValue(property: IProperty): String? {
        return if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name)) {
            moduleReference.moduleName
        } else if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)) {
            moduleReference.moduleId.toString()
        } else {
            null
        }
    }
}

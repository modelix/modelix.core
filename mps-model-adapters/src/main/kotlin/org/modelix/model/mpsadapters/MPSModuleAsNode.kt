package org.modelix.model.mpsadapters

import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.ProjectBase
import jetbrains.mps.project.ProjectManager
import jetbrains.mps.project.Solution
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SDependencyScope
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.area.IArea

data class MPSModuleAsNode(val module: SModule) : IDefaultNodeAdapter {

    companion object {
        private val logger = mu.KotlinLogging.logger { }
    }

    private val childrenAccessors: Map<IChildLink, () -> Iterable<INode>> = mapOf(
        BuiltinLanguages.MPSRepositoryConcepts.Module.models to { module.models.withoutDescriptorModel().map { MPSModelAsNode(it) } },
        BuiltinLanguages.MPSRepositoryConcepts.Module.facets to { module.facets.filterIsInstance<JavaModuleFacet>().map { MPSJavaModuleFacetAsNode(it) } },
        BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies to { getDependencies() },
        BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies to { getLanguageDependencies() },
    )

    override fun getArea(): IArea {
        return MPSArea(module.repository ?: MPSModuleRepository.getInstance())
    }

    override val reference: INodeReference
        get() = MPSModuleReference(module.moduleReference)
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.Module
    override val parent: INode?
        get() = module.repository?.let { MPSRepositoryAsNode(it) }

    override val allChildren: Iterable<INode>
        get() = childrenAccessors.values.flatMap { it() }

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Repository.modules
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        for (childrenAccessor in childrenAccessors) {
            if (link.conformsTo(childrenAccessor.key)) return childrenAccessor.value()
        }
        return emptyList()
    }

    private fun getDependencies(): Iterable<INode> {
        if (module !is AbstractModule) return emptyList()

        val moduleDescriptor = module.moduleDescriptor ?: return emptyList()

        return moduleDescriptor.dependencyVersions.map { (ref, version) ->
            MPSModuleDependencyAsNode(
                moduleReference = ref,
                moduleVersion = version,
                explicit = isDirectDependency(module, ref.moduleId),
                reexport = isReexport(module, ref.moduleId),
                importer = module,
                dependencyScope = getDependencyScope(module, ref.moduleId),
            )
        }
    }

    private fun getLanguageDependencies(): Iterable<INode> {
        if (module !is AbstractModule) return emptyList()

        val moduleDescriptor = module.moduleDescriptor ?: return emptyList()
        val dependencies = mutableListOf<INode>()

        for ((language, version) in moduleDescriptor.languageVersions) {
            dependencies.add(
                MPSSingleLanguageDependencyAsNode(language.sourceModuleReference, version, moduleImporter = module),
            )
        }

        for (devKit in moduleDescriptor.usedDevkits) {
            dependencies.add(MPSDevKitDependencyAsNode(devKit, module))
        }

        return dependencies
    }

    private fun isDirectDependency(module: SModule, moduleId: SModuleId): Boolean {
        if (module is Solution) {
            return module.moduleDescriptor.dependencies.any { it.moduleRef.moduleId == moduleId }
        }
        return module.declaredDependencies.any { it.targetModule.moduleId == moduleId }
    }

    private fun isReexport(module: SModule, moduleId: SModuleId): Boolean {
        return module.declaredDependencies
            .firstOrNull { it.targetModule.moduleId == moduleId }?.isReexport ?: false
    }

    private fun getDependencyScope(module: SModule, moduleId: SModuleId): SDependencyScope? {
        if (module is Solution) {
            return module.moduleDescriptor.dependencies
                .firstOrNull { it.moduleRef.moduleId == moduleId }?.scope
        }
        return module.declaredDependencies.firstOrNull { it.targetModule.moduleId == moduleId }?.scope
    }

    override fun getPropertyValue(property: IProperty): String? {
        return if (property.conformsTo(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)) {
            module.moduleName
        } else if (property.conformsTo(BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept.virtualPackage)) {
            ProjectManager.getInstance().openedProjects.asSequence()
                .filterIsInstance<ProjectBase>()
                .mapNotNull { it.getPath(module) }
                .firstOrNull()
                ?.virtualFolder
        } else if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Module.id)) {
            module.moduleId.toString()
        } else if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion)) {
            val version = (module as? AbstractModule)?.moduleDescriptor?.moduleVersion ?: 0
            version.toString()
        } else if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS)) {
            getCompileInMPS().toString()
        } else {
            null
        }
    }

    private fun getCompileInMPS(): Boolean {
        if (module is DevKit || module !is AbstractModule) {
            return false
        }
        return try {
            module.moduleDescriptor?.compileInMPS ?: false
        } catch (ex: UnsupportedOperationException) {
            logger.debug { ex }
            false
        }
    }

    internal fun findModuleDependency(dependencyId: SModuleId): MPSModuleDependencyAsNode? {
        if (module !is AbstractModule) {
            return null
        }

        module.moduleDescriptor?.dependencyVersions?.forEach { entry ->
            if (entry.key.moduleId == dependencyId) {
                return MPSModuleDependencyAsNode(
                    moduleReference = entry.key,
                    moduleVersion = entry.value,
                    explicit = isDirectDependency(module, entry.key.moduleId),
                    reexport = isReexport(module, entry.key.moduleId),
                    importer = module,
                    dependencyScope = getDependencyScope(module, entry.key.moduleId),
                )
            }
        }
        return null
    }

    internal fun findSingleLanguageDependency(dependencyId: SModuleId): MPSSingleLanguageDependencyAsNode? {
        if (module !is AbstractModule) {
            return null
        }
        val languageDependencies = module.moduleDescriptor?.languageVersions
        languageDependencies?.forEach { entry ->
            val sourceModelReference = entry.key.sourceModuleReference
            if (sourceModelReference.moduleId == dependencyId) {
                return MPSSingleLanguageDependencyAsNode(sourceModelReference, entry.value, moduleImporter = module)
            }
        }
        return null
    }

    internal fun findDevKitDependency(dependencyId: SModuleId): MPSDevKitDependencyAsNode? {
        if (module !is AbstractModule) {
            return null
        }
        module.moduleDescriptor?.usedDevkits?.forEach { devKit ->
            if (devKit.moduleId == dependencyId) {
                return MPSDevKitDependencyAsNode(devKit, module)
            }
        }
        return null
    }
}

private fun <T : SModel> Iterable<T>.withoutDescriptorModel(): List<T> {
    return filter { it.name.stereotype != "descriptor" }
}

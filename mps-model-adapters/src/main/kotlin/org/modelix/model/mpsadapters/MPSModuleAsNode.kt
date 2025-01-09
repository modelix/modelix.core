package org.modelix.model.mpsadapters

import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.ProjectBase
import jetbrains.mps.project.ProjectManager
import jetbrains.mps.project.Solution
import jetbrains.mps.project.facets.JavaModuleFacet
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SDependencyScope
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode

data class MPSModuleAsNode(val module: SModule) : MPSGenericNodeAdapter<MPSModuleAsNode>() {

    companion object {
        private val logger = mu.KotlinLogging.logger { }

        private val propertyAccessors = listOf<Pair<IPropertyReference, IPropertyAccessor<MPSModuleAsNode>>>(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference() to object : IPropertyAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): String? = element.module.moduleName
            },
            BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept.virtualPackage.toReference() to object : IPropertyAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): String? {
                    return ProjectManager.getInstance().openedProjects.asSequence()
                        .filterIsInstance<ProjectBase>()
                        .mapNotNull { it.getPath(element.module) }
                        .firstOrNull()
                        ?.virtualFolder
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference() to object : IPropertyAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): String? = element.module.moduleId.toString()
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion.toReference() to object : IPropertyAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): String? {
                    val version = (element as? AbstractModule)?.moduleDescriptor?.moduleVersion ?: 0
                    return version.toString()
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS.toReference() to object : IPropertyAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): String? {
                    return element.getCompileInMPS().toString()
                }
            },
        )

        private val referenceAccessors = listOf<Pair<IReferenceLinkReference, IReferenceAccessor<MPSModuleAsNode>>>()
        private val childAccessors = listOf<Pair<IChildLinkReference, IChildAccessor<MPSModuleAsNode>>>(
            BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference() to object : IChildAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): List<IWritableNode> = element.module.models.withoutDescriptorModel().map { MPSModelAsNode(it) }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.facets.toReference() to object : IChildAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): List<IWritableNode> {
                    return element.module.facets.filterIsInstance<JavaModuleFacet>()
                        .map { MPSJavaModuleFacetAsNode(it).asWritableNode() }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies.toReference() to object : IChildAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): List<IWritableNode> {
                    val module = element.module
                    if (module !is AbstractModule) return emptyList()

                    val moduleDescriptor = module.moduleDescriptor ?: return emptyList()

                    return moduleDescriptor.dependencyVersions.map { (ref, version) ->
                        MPSModuleDependencyAsNode(
                            moduleReference = ref,
                            moduleVersion = version,
                            explicit = element.isDirectDependency(module, ref.moduleId),
                            reexport = element.isReexport(module, ref.moduleId),
                            importer = module,
                            dependencyScope = element.getDependencyScope(module, ref.moduleId),
                        ).asWritableNode()
                    }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies.toReference() to object : IChildAccessor<MPSModuleAsNode> {
                override fun read(element: MPSModuleAsNode): List<IWritableNode> {
                    val module = element.module
                    if (module !is AbstractModule) return emptyList()
                    val moduleDescriptor = module.moduleDescriptor ?: return emptyList()
                    return moduleDescriptor.languageVersions.map { (language, version) ->
                        MPSSingleLanguageDependencyAsNode(language.sourceModuleReference, version, moduleImporter = module).asWritableNode()
                    } + moduleDescriptor.usedDevkits.map { devKit ->
                        MPSDevKitDependencyAsNode(devKit, module).asWritableNode()
                    }
                }
            },
        )
    }

    override fun getElement(): MPSModuleAsNode {
        return this
    }

    override fun getRepository(): SRepository? {
        return module.repository
    }

    override fun getPropertyAccessors() = propertyAccessors

    override fun getReferenceAccessors() = referenceAccessors

    override fun getChildAccessors() = childAccessors

    override fun getParent(): IWritableNode? {
        return module.repository?.let { MPSRepositoryAsNode(it).asWritableNode() }
    }

    override fun getNodeReference(): INodeReference {
        return MPSModuleReference(module.moduleReference)
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.Module
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference()
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

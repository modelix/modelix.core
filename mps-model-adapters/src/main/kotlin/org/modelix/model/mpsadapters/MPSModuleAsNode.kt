package org.modelix.model.mpsadapters

import jetbrains.mps.persistence.MementoImpl
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.project.facets.JavaModuleFacetImpl
import jetbrains.mps.project.structure.modules.Dependency
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.SModelId
import jetbrains.mps.smodel.adapter.ids.SLanguageId
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.workbench.actions.model.DeleteModelHelper
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.FacetsFacade
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode
import org.modelix.model.data.asData
import org.modelix.mps.api.ModelixMpsApi

fun MPSModuleAsNode(module: SModule) = MPSModuleAsNode.create(module)

abstract class MPSModuleAsNode<E : SModule> : MPSGenericNodeAdapter<E>() {

    companion object {
        private val logger = mu.KotlinLogging.logger { }

        fun <T : SModule> create(module: T): MPSModuleAsNode<T> {
            return when (module) {
                is Solution -> MPSSolutionAsNode(module)
                is Language -> MPSLanguageAsNode(module)
                is Generator -> MPSGeneratorAsNode(module)
                is DevKit -> MPSDevkitAsNode(module)
                else -> MPSUnknownModuleAsNode(module)
            } as MPSModuleAsNode<T>
        }

        internal fun readModuleReference(refNode: IReadableNode): SModuleReference {
            val moduleNode = refNode.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference()) ?: run {
                val originalRef = requireNotNull(refNode.getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference())) {
                    "Reference to module is not set: ${refNode.asLegacyNode().asData().toJson()}"
                }
                @Suppress("removal")
                MPSArea(ModelixMpsApi.getRepository()).resolveNode(originalRef)?.asWritableNode()
            }
            checkNotNull(moduleNode)
            val moduleId = moduleNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())!!
            val moduleName = moduleNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) ?: ""
            return PersistenceFacade.getInstance().createModuleReference(ModuleId.fromString(moduleId), moduleName)
        }

        private val propertyAccessors = listOf<Pair<IPropertyReference, IPropertyAccessor<SModule>>>(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference() to object : IPropertyAccessor<SModule> {
                override fun read(element: SModule): String? = element.moduleName
                override fun write(element: SModule, value: String?) = TODO()
            },
            BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept.virtualPackage.toReference() to object : IPropertyAccessor<SModule> {
                override fun read(element: SModule): String? {
                    return runCatching { ModelixMpsApi.getVirtualFolder(element) }.getOrNull()
                }

                override fun write(element: SModule, value: String?) {
                    ModelixMpsApi.setVirtualFolder(element, value)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference() to object : IPropertyAccessor<SModule> {
                override fun read(element: SModule): String? = element.moduleId.toString()
                override fun write(element: SModule, value: String?) {
                    throw UnsupportedOperationException("read only")
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion.toReference() to object : IPropertyAccessor<SModule> {
                override fun read(element: SModule): String? {
                    val version = (element as? AbstractModule)?.moduleDescriptor?.moduleVersion ?: 0
                    return version.toString()
                }
                override fun write(element: SModule, value: String?) {
                    (element as? AbstractModule)?.moduleDescriptor?.moduleVersion = value?.toInt() ?: 0
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS.toReference() to object : IPropertyAccessor<SModule> {
                override fun read(element: SModule): String? {
                    return element.getCompileInMPS().toString()
                }

                override fun write(element: SModule, value: String?) {
                    if (element.getCompileInMPS().toString() == value) return
                    (element as Solution).moduleDescriptor.compileInMPS = value.toBoolean()
                }
            },
        )

        private val referenceAccessors = listOf<Pair<IReferenceLinkReference, IReferenceAccessor<SModule>>>()
        val childAccessors = listOf<Pair<IChildLinkReference, IChildAccessor<SModule>>>(
            BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference() to object : IChildAccessor<SModule> {
                override fun read(element: SModule): List<IWritableNode> {
                    return element.models.withoutDescriptorModel().map { MPSModelAsNode(it) }
                }
                override fun addNew(
                    element: SModule,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    return element.createModel(
                        name = sourceNode.getNode().getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())
                            ?: "${element.moduleName}.unnamed",
                        id = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id.toReference())
                            ?.let { PersistenceFacade.getInstance().createModelId(it) } ?: SModelId.generate(),
                    ).let { MPSModelAsNode(it) }
                }

                override fun move(element: SModule, index: Int, child: IWritableNode) {
                    throw UnsupportedOperationException()
                }

                override fun remove(
                    element: SModule,
                    child: IWritableNode,
                ) {
                    DeleteModelHelper.delete(element, (child as MPSModelAsNode).model, true)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.facets.toReference() to object : IChildAccessor<SModule> {
                override fun read(element: SModule): List<IWritableNode> {
                    return element.facets.mapNotNull {
                        when (it) {
                            is JavaModuleFacet -> MPSJavaModuleFacetAsNode(it)
                            else -> null
                        }
                    }
                }

                override fun addNew(
                    element: SModule,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    return when (sourceNode.getConceptReference()) {
                        BuiltinLanguages.MPSRepositoryConcepts.JavaModuleFacet.getReference() -> {
                            val module = element as AbstractModule
                            val moduleDescriptor = checkNotNull(module.moduleDescriptor) { "Has no moduleDescriptor: $module" }
                            val newFacet = FacetsFacade.getInstance().getFacetFactory(JavaModuleFacet.FACET_TYPE)!!.create(element) as JavaModuleFacetImpl
                            newFacet.load(MementoImpl())
//                            val moduleDir = if (element is Generator) element.getGeneratorLocation() else module.moduleSourceDir
//                            if (moduleDir != null) {
//                                newFacet.setGeneratedClassesLocation(moduleDir.findChild(AbstractModule.CLASSES_GEN))
//                            }
                            moduleDescriptor.addFacetDescriptor(newFacet)
                            module.setModuleDescriptor(moduleDescriptor) // notify listeners
                            read(element).filterIsInstance<MPSJavaModuleFacetAsNode>().single()
                        }
                        else -> error("Unsupported facets type: ${sourceNode.getConceptReference()}")
                    }
                }

                override fun remove(element: SModule, child: IWritableNode) {
                    val module = element as AbstractModule
                    val moduleDescriptor = checkNotNull(module.moduleDescriptor) { "Has no moduleDescriptor: $module" }
                    val facet = child as MPSJavaModuleFacetAsNode
                    moduleDescriptor.removeFacetDescriptor(facet.facet)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies.toReference() to object : IChildAccessor<SModule> {
                override fun read(element: SModule): List<IWritableNode> {
                    val module = element
                    if (module !is AbstractModule) return emptyList()

                    val moduleDescriptor = module.moduleDescriptor ?: return emptyList()

                    return moduleDescriptor.dependencies.map { it.moduleRef }
                        .plus(moduleDescriptor.dependencyVersions.map { it.key })
                        .distinct()
                        .map { MPSModuleDependencyAsNode(element, it) }
                }

                override fun addNew(
                    element: SModule,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    val module = element as AbstractModule
                    val moduleDescriptor = checkNotNull(module.moduleDescriptor) { "No descriptor: $module" }

                    return when (sourceNode.getConceptReference()) {
                        BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.getReference() -> {
                            val id = requireNotNull(sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid.toReference())) {
                                "Has no ID: $sourceNode"
                            }
                            val name = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name.toReference()) ?: ""
                            val ref = PersistenceFacade.getInstance().createModuleReference(ModuleId.fromString(id), name)
                            val reexport = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport.toReference()).toBoolean()
                            val explicit = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.explicit.toReference()).toBoolean()
                            val version = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.version.toReference())?.toInt() ?: 0
                            if (explicit) {
                                moduleDescriptor.dependencies.add(Dependency(ref, reexport))
                            }
                            moduleDescriptor.dependencyVersions[ref] = version
                            MPSModuleDependencyAsNode(element, ref)
                        }
                        else -> error("Unsupported dependency type: ${sourceNode.getConceptReference()}")
                    }
                }

                override fun remove(element: SModule, child: IWritableNode) {
                    val module = element as AbstractModule
                    val moduleDescriptor = checkNotNull(module.moduleDescriptor) { "Has no moduleDescriptor: $module" }
                    val dependency = child as MPSModuleDependencyAsNode
                    moduleDescriptor.dependencies.removeIf { it.moduleRef == dependency.moduleReference }
                    moduleDescriptor.dependencyVersions.remove(dependency.moduleReference)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies.toReference() to object : IChildAccessor<SModule> {
                override fun read(element: SModule): List<IWritableNode> {
                    val module = element
                    if (module !is AbstractModule) return emptyList()
                    val moduleDescriptor = module.moduleDescriptor ?: return emptyList()
                    return moduleDescriptor.languageVersions.map { (language, version) ->
                        MPSSingleLanguageDependencyAsNode(language, moduleImporter = module)
                    }
                    // moduleDescriptor.usedDevkits is ignored because it is unused in MPS.
                    // On module level there are only languages, and they are derived from the model dependencies.
                    // Only models can contain devkit dependencies.
                }

                override fun addNew(
                    element: SModule,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    val module = element as AbstractModule
                    val moduleDescriptor = checkNotNull(module.moduleDescriptor) { "No descriptor: $module" }
                    return when (sourceNode.getConceptReference()) {
                        BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.getReference() -> {
                            val id = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid.toReference())
                            val name = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.toReference()) ?: ""
                            val version = sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version.toReference()) ?: ""
                            val lang = MetaAdapterFactory.getLanguage(SLanguageId.deserialize(id), name)
                            moduleDescriptor.languageVersions[lang] = version.toIntOrNull() ?: -1
                            MPSSingleLanguageDependencyAsNode(lang, moduleImporter = element)
                        }
                        BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency.getReference() -> {
                            throw IllegalArgumentException("Modules cannot contain devkit dependencies")
                        }
                        else -> error("Unsupported: ${sourceNode.getConceptReference()}")
                    }
                }

                override fun remove(
                    element: SModule,
                    child: IWritableNode,
                ) {
                    val module = element as AbstractModule
                    val moduleDescriptor = checkNotNull(module.moduleDescriptor) { "No descriptor: $element" }
                    when (child) {
                        is MPSSingleLanguageDependencyAsNode -> {
                            moduleDescriptor.languageVersions.remove(child.moduleReference)
                        }
                        is MPSDevKitDependencyAsNode -> {
                            moduleDescriptor.usedDevkits.remove(child.moduleReference)
                        }
                        else -> throw IllegalArgumentException("Unsupported child type: $child")
                    }
                }
            },
        )
    }

    abstract val module: E

    override fun isReadOnly(): Boolean {
        return module.isReadOnly
    }

    override fun getElement(): E {
        return module
    }

    override fun getRepository(): SRepository? {
        return module.repository
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<E>>> = propertyAccessors

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<E>>> = referenceAccessors

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<E>>> = childAccessors

    override fun getParent(): IWritableNode? {
        return module.repository?.asWritableNode()
    }

    override fun getNodeReference(): INodeReference {
        return module.moduleReference.toModelix()
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference()
    }

    internal fun findModuleDependency(dependencyId: SModuleId): MPSModuleDependencyAsNode? {
        val module = module
        if (module !is AbstractModule) {
            return null
        }

        module.moduleDescriptor?.dependencyVersions?.forEach { entry ->
            if (entry.key.moduleId == dependencyId) {
                return MPSModuleDependencyAsNode(
                    module,
                    moduleReference = entry.key,
                )
            }
        }
        return null
    }

    internal fun findSingleLanguageDependency(dependencyId: SModuleId): MPSSingleLanguageDependencyAsNode? {
        val module = module
        if (module !is AbstractModule) {
            return null
        }

        return childAccessors
            .first { it.first == BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies.toReference() }
            .second.read(module)
            .filterIsInstance<MPSSingleLanguageDependencyAsNode>()
            .find { it.moduleReference.sourceModuleReference.moduleId == dependencyId }
    }

    internal fun findDevKitDependency(dependencyId: SModuleId): MPSDevKitDependencyAsNode? {
        val module = module
        if (module !is AbstractModule) {
            return null
        }

        return childAccessors
            .first { it.first == BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies.toReference() }
            .second.read(module)
            .filterIsInstance<MPSDevKitDependencyAsNode>()
            .find { it.moduleReference.moduleId == dependencyId }
    }
}

data class MPSUnknownModuleAsNode(override val module: SModule) : MPSModuleAsNode<SModule>() {
    override fun getConcept(): IConcept = BuiltinLanguages.MPSRepositoryConcepts.Module
}
data class MPSGeneratorAsNode(override val module: Generator) : MPSModuleAsNode<Generator>() {
    companion object {
        private val propertyAccessors = listOf<Pair<IPropertyReference, IPropertyAccessor<Generator>>>(
            BuiltinLanguages.MPSRepositoryConcepts.Generator.alias.toReference() to object : IPropertyAccessor<Generator> {
                override fun read(element: Generator): String? = element.moduleDescriptor.alias
                override fun write(element: Generator, value: String?) {
                    element.moduleDescriptor.alias = value
                }
            },
        )
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<Generator>>> {
        return super.getPropertyAccessors() + propertyAccessors
    }

    override fun getConcept(): IConcept = BuiltinLanguages.MPSRepositoryConcepts.Generator

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Language.generators.toReference()
    }

    override fun getParent(): IWritableNode? {
        return module.sourceLanguage().resolveSourceModule()?.let { MPSModuleAsNode.create(it) }
    }
}
data class MPSLanguageAsNode(override val module: Language) : MPSModuleAsNode<Language>() {
    companion object {
        private val childAccessors: List<Pair<IChildLinkReference, IChildAccessor<Language>>> = MPSModuleAsNode.childAccessors + listOf<Pair<IChildLinkReference, IChildAccessor<Language>>>(
            BuiltinLanguages.MPSRepositoryConcepts.Language.generators.toReference() to object : IChildAccessor<Language> {
                override fun read(element: Language): List<IWritableNode> = element.generators.map { MPSGeneratorAsNode(it) }
                override fun addNew(
                    element: Language,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    return GeneratorProducer(ModelixMpsApi.getMPSProjects().first() as MPSProject).create(
                        element,
                        sourceNode.getNode().getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())!!,
                        sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference())!!.let { ModuleId.fromString(it) },
                        sourceNode.getNode().getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Generator.alias.toReference()),
                    ).let { MPSGeneratorAsNode(it) }
                }

                override fun remove(element: Language, child: IWritableNode) {
                    (child as MPSGeneratorAsNode).module.delete()
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.Language.extendedLanguages.toReference() to object : IChildAccessor<Language> {
                override fun read(element: Language): List<IWritableNode> {
                    return element.extendedLanguageRefs.map {
                        MPSModuleReferenceAsNode(
                            MPSLanguageAsNode(element),
                            BuiltinLanguages.MPSRepositoryConcepts.Language.extendedLanguages.toReference(),
                            it,
                        )
                    }
                }

                override fun addNew(element: Language, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    val newRef = readModuleReference(sourceNode.getNode())
                    element.addExtendedLanguage(newRef)
                    return MPSModuleReferenceAsNode(
                        MPSLanguageAsNode(element),
                        BuiltinLanguages.MPSRepositoryConcepts.Language.extendedLanguages.toReference(),
                        newRef,
                    )
                }

                override fun remove(element: Language, child: IWritableNode) {
                    element.moduleDescriptor.extendedLanguages.remove((child as MPSModuleReferenceAsNode).target)
                }
            },
        )
    }

    override fun getConcept(): IConcept = BuiltinLanguages.MPSRepositoryConcepts.Language

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<Language>>> = childAccessors
}
data class MPSSolutionAsNode(override val module: Solution) : MPSModuleAsNode<Solution>() {
    override fun getConcept(): IConcept = BuiltinLanguages.MPSRepositoryConcepts.Solution
}
data class MPSDevkitAsNode(override val module: DevKit) : MPSModuleAsNode<DevKit>() {
    companion object {
        private val childAccessors: List<Pair<IChildLinkReference, IChildAccessor<DevKit>>> = MPSModuleAsNode.childAccessors + listOf<Pair<IChildLinkReference, IChildAccessor<DevKit>>>(
            BuiltinLanguages.MPSRepositoryConcepts.DevKit.exportedLanguages.toReference() to object : IChildAccessor<DevKit> {
                override fun read(element: DevKit): List<IWritableNode> {
                    return element.moduleDescriptor!!.exportedLanguages.map {
                        MPSModuleReferenceAsNode(
                            MPSModuleAsNode(element),
                            BuiltinLanguages.MPSRepositoryConcepts.DevKit.exportedLanguages.toReference(),
                            it,
                        )
                    }
                }
                override fun addNew(element: DevKit, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    val ref = readModuleReference(sourceNode.getNode())
                    element.moduleDescriptor!!.exportedLanguages.add(ref)
                    return MPSModuleReferenceAsNode(MPSModuleAsNode(element), BuiltinLanguages.MPSRepositoryConcepts.DevKit.exportedLanguages.toReference(), ref)
                }

                override fun remove(element: DevKit, child: IWritableNode) {
                    element.moduleDescriptor!!.exportedLanguages.remove((child as MPSModuleReferenceAsNode).target)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.DevKit.exportedSolutions.toReference() to object : IChildAccessor<DevKit> {
                override fun read(element: DevKit): List<IWritableNode> {
                    return element.moduleDescriptor!!.exportedSolutions.map {
                        MPSModuleReferenceAsNode(
                            MPSModuleAsNode(element),
                            BuiltinLanguages.MPSRepositoryConcepts.DevKit.exportedSolutions.toReference(),
                            it,
                        )
                    }
                }
                override fun addNew(element: DevKit, index: Int, sourceNode: SpecWithResolvedConcept): IWritableNode {
                    val ref = readModuleReference(sourceNode.getNode())
                    element.moduleDescriptor!!.exportedSolutions.add(ref)
                    return MPSModuleReferenceAsNode(MPSModuleAsNode(element), BuiltinLanguages.MPSRepositoryConcepts.DevKit.exportedSolutions.toReference(), ref)
                }

                override fun remove(element: DevKit, child: IWritableNode) {
                    element.moduleDescriptor!!.exportedSolutions.remove((child as MPSModuleReferenceAsNode).target)
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.DevKit.extendedDevkits.toReference() to object : IChildAccessor<DevKit> {
                override fun read(element: DevKit): List<IWritableNode> {
                    return element.moduleDescriptor!!.extendedDevkits.map {
                        MPSModuleReferenceAsNode(
                            MPSModuleAsNode(element),
                            BuiltinLanguages.MPSRepositoryConcepts.DevKit.extendedDevkits.toReference(),
                            it,
                        )
                    }
                }
                override fun addNew(
                    element: DevKit,
                    index: Int,
                    sourceNode: SpecWithResolvedConcept,
                ): IWritableNode {
                    val ref = readModuleReference(sourceNode.getNode())
                    element.moduleDescriptor!!.extendedDevkits.add(ref)
                    return MPSModuleReferenceAsNode(MPSModuleAsNode(element), BuiltinLanguages.MPSRepositoryConcepts.DevKit.extendedDevkits.toReference(), ref)
                }

                override fun move(element: DevKit, index: Int, child: IWritableNode) {
                    throw UnsupportedOperationException()
                }

                override fun remove(element: DevKit, child: IWritableNode) {
                    element.moduleDescriptor!!.extendedDevkits.remove((child as MPSModuleReferenceAsNode).target)
                }
            },
        )
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<DevKit>>> = childAccessors

    override fun getConcept(): IConcept = BuiltinLanguages.MPSRepositoryConcepts.DevKit
}

fun <T : SModel> Iterable<T>.withoutDescriptorModel(): List<T> {
    return filter { it.name.stereotype != "descriptor" }
}

private fun SModule.getCompileInMPS(): Boolean {
    val module = this
    if (module !is Solution) {
        return false
    }
    @Suppress("removal")
    return module.moduleDescriptor.compileInMPS
}

fun SLanguage.resolveSourceModule() = sourceModuleReference.resolve(ModelixMpsApi.getRepository())

package org.modelix.model.mpsadapters

import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.structure.modules.Dependency
import jetbrains.mps.project.structure.modules.ModuleReference
import org.jetbrains.mps.openapi.module.SDependencyScope
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode

data class MPSModuleDependencyAsNode(
    val owner: SModule,
    val moduleReference: SModuleReference,
) : MPSGenericNodeAdapter<MPSModuleDependencyAsNode>() {

    companion object {
        val propertyAccessors: List<Pair<IPropertyReference, IPropertyAccessor<MPSModuleDependencyAsNode>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.explicit.toReference() to object : IPropertyAccessor<MPSModuleDependencyAsNode> {
                override fun read(element: MPSModuleDependencyAsNode): String? {
                    val module = element.owner as AbstractModule
                    val descriptor = module.moduleDescriptor
                    if (descriptor != null) {
                        return descriptor.dependencies.any { it.moduleRef == element.moduleReference }.toString()
                    }
                    return module.declaredDependencies.any { it.targetModule == element.moduleReference }.toString()
                }

                override fun write(
                    element: MPSModuleDependencyAsNode,
                    value: String?,
                ) {
                    val module = element.owner as AbstractModule
                    val descriptor = checkNotNull(module.moduleDescriptor) { "No descriptor: $module" }
                    if (value?.toBoolean() == true) {
                        descriptor.dependencies.add(Dependency(element.moduleReference, false))
                    } else {
                        descriptor.dependencies.removeIf { it.moduleRef == element.moduleReference }
                    }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name.toReference() to object : IPropertyAccessor<MPSModuleDependencyAsNode> {
                override fun read(element: MPSModuleDependencyAsNode): String? {
                    return element.moduleReference.moduleName
                }

                override fun write(element: MPSModuleDependencyAsNode, value: String?) {
                    val module = element.owner as AbstractModule
                    val descriptor = module.moduleDescriptor!!
                    descriptor.dependencies
                        .filter { it.moduleRef.moduleId == element.moduleReference.moduleId }
                        .forEach { it.moduleRef = ModuleReference(value, it.moduleRef.moduleId) }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport.toReference() to object : IPropertyAccessor<MPSModuleDependencyAsNode> {
                override fun read(element: MPSModuleDependencyAsNode): String? {
                    return (
                        element.owner.declaredDependencies
                            .firstOrNull { it.targetModule == element.moduleReference }
                            ?.isReexport
                            ?: false
                        ).toString()
                }

                override fun write(
                    element: MPSModuleDependencyAsNode,
                    value: String?,
                ) {
                    val value = value?.toBoolean() ?: false
                    val module = element.owner as AbstractModule
                    val descriptor = checkNotNull(module.moduleDescriptor) { "No descriptor: $module" }
                    val existing = descriptor.dependencies.firstOrNull { it.moduleRef == element.moduleReference }
                    if (existing != null) {
                        existing.isReexport = value
                    } else {
                        descriptor.dependencies.add(Dependency(element.moduleReference, value))
                    }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid.toReference() to object : IPropertyAccessor<MPSModuleDependencyAsNode> {
                override fun read(element: MPSModuleDependencyAsNode): String? {
                    return element.moduleReference.moduleId.toString()
                }

                override fun write(element: MPSModuleDependencyAsNode, value: String?) {
                    throw UnsupportedOperationException("read only")
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.version.toReference() to object : IPropertyAccessor<MPSModuleDependencyAsNode> {
                override fun read(element: MPSModuleDependencyAsNode): String? {
                    val module = element.owner as AbstractModule
                    val moduleDescriptor = checkNotNull(module.moduleDescriptor) { "No descriptor: $module" }
                    return moduleDescriptor.dependencyVersions[element.moduleReference]?.toString()
                }

                override fun write(
                    element: MPSModuleDependencyAsNode,
                    value: String?,
                ) {
                    val value = value?.toInt()
                    val module = element.owner as AbstractModule
                    val descriptor = checkNotNull(module.moduleDescriptor) { "No descriptor: $module" }
                    if (value != null) {
                        descriptor.dependencyVersions[element.moduleReference] = value
                    } else {
                        descriptor.dependencyVersions.remove(element.moduleReference)
                    }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.scope.toReference() to object : IPropertyAccessor<MPSModuleDependencyAsNode> {
                override fun read(element: MPSModuleDependencyAsNode): String? {
                    val module = element.owner as AbstractModule
                    val moduleDescriptor = module.moduleDescriptor
                    if (moduleDescriptor != null) {
                        return moduleDescriptor.dependencies.firstOrNull { it.moduleRef == element.moduleReference }?.scope?.toString()
                    } else {
                        return module.declaredDependencies.firstOrNull { it.targetModule == element.moduleReference }?.scope?.toString()
                    }
                }

                override fun write(
                    element: MPSModuleDependencyAsNode,
                    value: String?,
                ) {
                    val value = if (value == null) SDependencyScope.DEFAULT else SDependencyScope.valueOf(value)
                    val module = element.owner as AbstractModule
                    val descriptor = checkNotNull(module.moduleDescriptor) { "No descriptor: $module" }
                    val existing = descriptor.dependencies.firstOrNull { it.moduleRef == element.moduleReference }
                    if (existing != null) {
                        existing.scope = value
                    } else {
                        descriptor.dependencies.add(Dependency(element.moduleReference, value))
                    }
                }
            },
        )
    }

    override fun getContainmentLink(): IChildLinkReference {
        return BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies.toReference()
    }

    override fun getElement(): MPSModuleDependencyAsNode {
        return this
    }

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<MPSModuleDependencyAsNode>>> {
        return propertyAccessors
    }

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<MPSModuleDependencyAsNode>>> {
        return emptyList()
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<MPSModuleDependencyAsNode>>> {
        return emptyList()
    }

    override fun getParent(): IWritableNode? {
        return MPSModuleAsNode(owner)
    }

    override fun getNodeReference(): INodeReference {
        return MPSModuleDependencyReference(
            usedModuleId = moduleReference.moduleId,
            userModuleReference = owner.moduleReference,
        )
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency
    }
}

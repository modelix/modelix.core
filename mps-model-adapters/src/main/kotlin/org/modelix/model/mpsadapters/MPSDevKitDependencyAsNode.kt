package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode

data class MPSDevKitDependencyAsNode(
    val moduleReference: SModuleReference,
    val moduleImporter: SModule? = null,
    val modelImporter: SModel? = null,
) : MPSGenericNodeAdapter<MPSDevKitDependencyAsNode>() {

    companion object {
        val propertyAccessors: List<Pair<IPropertyReference, IPropertyAccessor<MPSDevKitDependencyAsNode>>> = listOf(
            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.toReference() to object : IPropertyAccessor<MPSDevKitDependencyAsNode> {
                override fun read(element: MPSDevKitDependencyAsNode): String? {
                    return element.moduleReference.moduleName
                }

                override fun write(element: MPSDevKitDependencyAsNode, value: String?) {
                    throw UnsupportedOperationException("read only")
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid.toReference() to object : IPropertyAccessor<MPSDevKitDependencyAsNode> {
                override fun read(element: MPSDevKitDependencyAsNode): String? {
                    return element.moduleReference.moduleId.toString()
                }

                override fun write(element: MPSDevKitDependencyAsNode, value: String?) {
                    throw UnsupportedOperationException("read only")
                }
            },
        )
    }

    init {
        require(moduleImporter != null || modelImporter != null)
    }

    override fun getElement(): MPSDevKitDependencyAsNode = this

    override fun getPropertyAccessors(): List<Pair<IPropertyReference, IPropertyAccessor<MPSDevKitDependencyAsNode>>> {
        return propertyAccessors
    }

    override fun getReferenceAccessors(): List<Pair<IReferenceLinkReference, IReferenceAccessor<MPSDevKitDependencyAsNode>>> {
        return emptyList()
    }

    override fun getChildAccessors(): List<Pair<IChildLinkReference, IChildAccessor<MPSDevKitDependencyAsNode>>> {
        return emptyList()
    }

    override fun getParent(): IWritableNode? {
        return if (moduleImporter != null) {
            MPSModuleAsNode(moduleImporter)
        } else if (modelImporter != null) {
            MPSModelAsNode(modelImporter)
        } else {
            error("No importer found for $this")
        }
    }

    override fun getNodeReference(): INodeReference {
        return MPSDevKitDependencyReference(
            moduleReference.moduleId,
            userModule = moduleImporter?.moduleReference,
            userModel = modelImporter?.reference,
        )
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency
    }

    override fun getContainmentLink(): IChildLinkReference {
        return if (moduleImporter != null) {
            BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies.toReference()
        } else if (modelImporter != null) {
            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages.toReference()
        } else {
            error("No importer found for $this")
        }
    }
}

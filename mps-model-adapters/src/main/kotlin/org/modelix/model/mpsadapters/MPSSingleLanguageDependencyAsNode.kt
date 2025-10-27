package org.modelix.model.mpsadapters

import jetbrains.mps.extapi.model.SModelDescriptorStub
import jetbrains.mps.project.AbstractModule
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IWritableNode

data class MPSSingleLanguageDependencyAsNode(
    val moduleReference: SLanguage,
    val moduleImporter: SModule? = null,
    val modelImporter: SModel? = null,
) : MPSGenericNodeAdapter<MPSSingleLanguageDependencyAsNode>() {

    companion object {
        private val propertyAccessors = listOf<Pair<IPropertyReference, IPropertyAccessor<MPSSingleLanguageDependencyAsNode>>>(
            BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version.toReference() to object : IPropertyAccessor<MPSSingleLanguageDependencyAsNode> {
                override fun read(element: MPSSingleLanguageDependencyAsNode): String? {
                    val version = element.moduleImporter?.let {
                        val module = (it as? AbstractModule) ?: return@let null
                        val descriptor = module.moduleDescriptor ?: return@let null
                        descriptor.languageVersions[element.moduleReference]
                    } ?: element.modelImporter?.let {
                        val model = it as? SModelDescriptorStub ?: return@let null
                        model.getLanguageImportVersion(element.moduleReference).takeIf { it != -1 }
                    } ?: return null
                    return version.toString()
                }

                override fun write(element: MPSSingleLanguageDependencyAsNode, value: String?) {
                    val version = value?.toInt() ?: -1
                    if (element.moduleImporter != null) {
                        val module = element.moduleImporter as AbstractModule
                        val descriptor = checkNotNull(module.moduleDescriptor) {
                            "Has no module descriptor: ${element.moduleImporter}"
                        }
                        descriptor.languageVersions[element.moduleReference] = version
                    } else if (element.modelImporter != null) {
                        val model = element.modelImporter as SModelDescriptorStub
                        model.setLanguageImportVersion(element.moduleReference, version)
                    } else {
                        error("No importing module or model specified")
                    }
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.toReference() to object : IPropertyAccessor<MPSSingleLanguageDependencyAsNode> {
                override fun read(element: MPSSingleLanguageDependencyAsNode): String? {
                    return element.moduleReference.qualifiedName
                }

                override fun write(element: MPSSingleLanguageDependencyAsNode, value: String?) {
                    throw UnsupportedOperationException("read only")
                }
            },
            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid.toReference() to object : IPropertyAccessor<MPSSingleLanguageDependencyAsNode> {
                override fun read(element: MPSSingleLanguageDependencyAsNode): String? {
                    return element.moduleReference.sourceModuleReference.moduleId.toString()
                }

                override fun write(element: MPSSingleLanguageDependencyAsNode, value: String?) {
                    throw UnsupportedOperationException("read only")
                }
            },

        )
        private val referenceAccessors = listOf<Pair<IReferenceLinkReference, IReferenceAccessor<MPSSingleLanguageDependencyAsNode>>>()
        private val childAccessors = listOf<Pair<IChildLinkReference, IChildAccessor<MPSSingleLanguageDependencyAsNode>>>()
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

    override fun getElement(): MPSSingleLanguageDependencyAsNode = this

    override fun getPropertyAccessors() = propertyAccessors

    override fun getReferenceAccessors() = referenceAccessors

    override fun getChildAccessors() = childAccessors

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
        return MPSSingleLanguageDependencyReference(
            moduleReference.sourceModuleReference.moduleId,
            userModule = moduleImporter?.moduleReference,
            userModel = modelImporter?.reference,
        )
    }

    override fun getConcept(): IConcept {
        return BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency
    }
}

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.mpsadapters

import jetbrains.mps.project.ProjectBase
import jetbrains.mps.project.ProjectManager
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.project.structure.modules.ModuleReference
import jetbrains.mps.smodel.GlobalModelAccess
import jetbrains.mps.smodel.SNodePointer
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference

data class MPSArea(val repository: SRepository) : IArea, IAreaReference {

    private fun resolveMPSModelReference(ref: INodeReference): INode {
        if (ref is MPSModelReference) {
            return ref.modelReference.resolve(repository).let { MPSModelAsNode(it) }
        }

        val serialized = ref.serialize().substringAfter("${MPSModelReference.PREFIX}:")
        val modelRef = PersistenceFacade.getInstance().createModelReference(serialized)

        return MPSModelAsNode(modelRef.resolve(repository))
    }

    override fun getRoot(): INode {
        return MPSRepositoryAsNode(repository)
    }

    @Deprecated("use ILanguageRepository.resolveConcept")
    override fun resolveConcept(ref: IConceptReference): IConcept? {
        return MPSLanguageRepository(repository).resolveConcept(ref.getUID())
    }

    override fun resolveNode(ref: INodeReference): INode? {
        // TOdo document optimize most common case
        if (ref is MPSNodeReference) {
            resolveMPSNodeReference(ref)
        }
        val serialized = ref.serialize()
        val prefix = serialized.substringBefore(":")
        return when (prefix) {
            MPSModuleReference.PREFIX -> resolveMPSModuleReference(ref)
            MPSModelReference.PREFIX -> resolveMPSModelReference(ref)
            MPSNodeReference.PREFIX, "mps-node" -> resolveMPSNodeReference(ref) // mps-node for backwards compatibility
            MPSDevKitDependencyReference.PREFIX -> resolveMPSDevKitDependencyReference(ref)
            MPSJavaModuleFacetReference.PREFIX -> resolveMPSJavaModuleFacetReference(ref)
            MPSModelImportReference.PREFIX -> resolveMPSModelImportReference(ref)
            MPSModuleDependencyReference.PREFIX -> resolveMPSModuleDependencyReference(ref)
            MPSProjectReference.PREFIX -> resolveMPSProjectReference(ref)
            MPSProjectModuleReference.PREFIX -> resolveMPSProjectModuleReference(ref)
            MPSSingleLanguageDependencyReference.PREFIX -> resolveMPSSingleLanguageDependencyReference(ref)
            MPSRepositoryReference.PREFIX -> resolveMPSRepositoryReference()
            else -> null
        }
    }

    override fun resolveOriginalNode(ref: INodeReference): INode? {
        return resolveNode(ref)
    }

    override fun resolveBranch(id: String): IBranch? {
        return null
    }

    override fun collectAreas(): List<IArea> {
        return listOf(this)
    }

    override fun getReference(): IAreaReference {
        return this
    }

    override fun resolveArea(ref: IAreaReference): IArea? {
        return takeIf { ref == it }
    }

    override fun <T> executeRead(f: () -> T): T {
        var result: T? = null
        repository.modelAccess.runReadAction {
            result = f()
        }
        return result!!
    }

    override fun <T> executeWrite(f: () -> T): T {
        var result: T? = null
        if (repository.modelAccess is GlobalModelAccess) {
            repository.modelAccess.runWriteAction { result = f() }
        } else {
            repository.modelAccess.executeCommand { result = f() }
        }
        return result!!
    }

    override fun canRead(): Boolean {
        return repository.modelAccess.canRead()
    }

    override fun canWrite(): Boolean {
        return repository.modelAccess.canWrite()
    }

    override fun addListener(l: IAreaListener) {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun removeListener(l: IAreaListener) {
        throw UnsupportedOperationException("Not implemented")
    }

    private fun resolveMPSModuleReference(ref: INodeReference): MPSModuleAsNode? {
        val moduleRef = if (ref is MPSModuleReference) {
            ref.moduleReference
        } else {
            val serializedRef = ref.serialize().substringAfter("${MPSModuleReference.PREFIX}:")
            ModuleReference.parseReference(serializedRef)
        }

        return moduleRef.resolve(repository)?.let { MPSModuleAsNode(it) }
    }

    private fun resolveMPSNodeReference(ref: INodeReference): MPSNode? {
        val sNodeReference = if (ref is MPSNodeReference) {
            ref.ref
        } else {
            val serialized = ref.serialize()
            val serializedMPSRef = when {
                serialized.startsWith("mps-node:") -> serialized.substringAfter("mps-node:")
                serialized.startsWith("mps:") -> serialized.substringAfter("mps:")
                else -> return null
            }
            SNodePointer.deserialize(serializedMPSRef)
        }

        return sNodeReference.resolve(repository)?.let { MPSNode(it) }
    }

    private fun resolveMPSDevKitDependencyReference(ref: INodeReference): MPSDevKitDependencyAsNode? {
        if (ref is MPSDevKitDependencyReference) {
            return when {
                ref.userModule != null -> ref.userModule.resolve(repository)
                    ?.let { MPSModuleAsNode(it).findDevKitDependency(ref.usedModuleId) }
                ref.userModel != null -> ref.userModel.resolve(repository)
                    ?.let { MPSModelAsNode(it).findDevKitDependency(ref.usedModuleId) }
                else -> error("No importer found.")
            }
        }
        val serialized = ref.serialize()
        val serializedModuleId = serialized.substringAfter("${MPSDevKitDependencyReference.PREFIX}:")
            .substringBefore(MPSDevKitDependencyReference.SEPARATOR)

        val importer = serialized.substringAfter(MPSDevKitDependencyReference.SEPARATOR)
        val foundImporter = resolveNode(NodeReference(importer))

        val moduleId = PersistenceFacade.getInstance().createModuleId(serializedModuleId)

        return when (foundImporter) {
            is MPSModelAsNode -> foundImporter.findDevKitDependency(moduleId)
            is MPSModuleAsNode -> foundImporter.findDevKitDependency(moduleId)
            else -> null
        }
    }

    private fun resolveMPSJavaModuleFacetReference(ref: INodeReference): MPSJavaModuleFacetAsNode? {
        val moduleRef = if (ref is MPSJavaModuleFacetReference) {
            ref.moduleReference
        } else {
            val serialized = ref.serialize()
            val serializedModuleRef = serialized.substringAfter("${MPSJavaModuleFacetReference.PREFIX}:")
            ModuleReference.parseReference(serializedModuleRef)
        }

        val facet = moduleRef.resolve(repository)?.getFacetOfType(JavaModuleFacet.FACET_TYPE)
        return facet?.let { MPSJavaModuleFacetAsNode(it as JavaModuleFacet) }
    }

    private fun resolveMPSModelImportReference(ref: INodeReference): MPSModelImportAsNode? {
        val serialized = ref.serialize()
        val importedModelRef = if (ref is MPSModelImportReference) {
            ref.importedModel
        } else {
            val serializedModelRef = serialized
                .substringAfter("${MPSModelImportReference.PREFIX}:")
                .substringBefore(MPSModelImportReference.SEPARATOR)
            PersistenceFacade.getInstance().createModelReference(serializedModelRef)
        }

        val importingModelRef = if (ref is MPSModelImportReference) {
            ref.importingModel
        } else {
            val serializedModelRef = serialized.substringAfter(MPSModelImportReference.SEPARATOR)
            PersistenceFacade.getInstance().createModelReference(serializedModelRef)
        }

        val importedModel = importedModelRef.resolve(repository) ?: return null
        val importingModel = importingModelRef.resolve(repository) ?: return null

        return MPSModelImportAsNode(importedModel = importedModel, importingModel = importingModel)
    }

    private fun resolveMPSModuleDependencyReference(ref: INodeReference): MPSModuleDependencyAsNode? {
        val serialized = ref.serialize()
        val usedModuleId = if (ref is MPSModuleDependencyReference) {
            ref.usedModuleId
        } else {
            val serializedModuleId = serialized
                .substringAfter("${MPSModuleDependencyReference.PREFIX}:")
                .substringBefore(MPSModuleDependencyReference.SEPARATOR)
            PersistenceFacade.getInstance().createModuleId(serializedModuleId)
        }

        val userModuleReference = if (ref is MPSModuleDependencyReference) {
            ref.userModuleReference
        } else {
            val serializedModuleRef = serialized.substringAfter(MPSModuleDependencyReference.SEPARATOR)
            ModuleReference.parseReference(serializedModuleRef)
        }

        return userModuleReference.resolve(repository)
            ?.let { MPSModuleAsNode(it) }
            ?.findModuleDependency(usedModuleId)
    }

    private fun resolveMPSProjectReference(ref: INodeReference): MPSProjectAsNode? {
        val projectName = if (ref is MPSProjectReference) {
            ref.projectName
        } else {
            ref.serialize().substringAfter("${MPSProjectReference.PREFIX}:")
        }

        val project = ProjectManager.getInstance().openedProjects
            .filterIsInstance<ProjectBase>()
            .find { it.name == projectName }

        return project?.let { MPSProjectAsNode(it) }
    }

    private fun resolveMPSProjectModuleReference(ref: INodeReference): MPSProjectModuleAsNode? {
        val serialized = ref.serialize()
        val moduleRef = if (ref is MPSProjectModuleReference) {
            ref.moduleRef
        } else {
            val serializedModuleRef = serialized
                .substringAfter("${MPSProjectModuleReference.PREFIX}:")
                .substringBefore(MPSProjectModuleReference.SEPARATOR)
            ModuleReference.parseReference(serializedModuleRef)
        }

        val projectRef = if (ref is MPSProjectModuleReference) {
            ref.projectRef
        } else {
            val projectRef = serialized.substringAfter(MPSProjectModuleReference.SEPARATOR)
            NodeReference(projectRef)
        }

        return moduleRef.resolve(repository)?.let {
            MPSProjectModuleAsNode(
                project = (resolveNode(projectRef) as MPSProjectAsNode).project,
                module = it,
            )
        }
    }

    private fun resolveMPSSingleLanguageDependencyReference(ref: INodeReference): MPSSingleLanguageDependencyAsNode? {
        if (ref is MPSSingleLanguageDependencyReference) {
            return when {
                ref.userModule != null -> ref.userModule.resolve(repository)
                    ?.let { MPSModuleAsNode(it).findSingleLanguageDependency(ref.usedModuleId) }
                ref.userModel != null -> ref.userModel.resolve(repository)
                    ?.let { MPSModelAsNode(it).findSingleLanguageDependency(ref.usedModuleId) }
                else -> error("No importer found.")
            }
        }
        val serialized = ref.serialize()
        val serializedModuleId = serialized.substringAfter("${MPSSingleLanguageDependencyReference.PREFIX}:")
            .substringBefore(MPSSingleLanguageDependencyReference.SEPARATOR)

        val importer = serialized.substringAfter(MPSSingleLanguageDependencyReference.SEPARATOR)
        val foundImporter = resolveNode(NodeReference(importer))

        val moduleId = PersistenceFacade.getInstance().createModuleId(serializedModuleId)

        return when (foundImporter) {
            is MPSModelAsNode -> foundImporter.findSingleLanguageDependency(moduleId)
            is MPSModuleAsNode -> foundImporter.findSingleLanguageDependency(moduleId)
            else -> null
        }
    }

    private fun resolveMPSRepositoryReference(): MPSRepositoryAsNode {
        return MPSRepositoryAsNode(repository)
    }
}

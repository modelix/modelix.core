/*
 * Copyright (c) 2023.
 *
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

import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.area.IArea
import java.util.Collections

// status: migrated, but needs some bugfixes
@Deprecated("use MPSSingleLanguageDependencyAsNode", replaceWith = ReplaceWith("MPSSingleLanguageDependencyAsNode"))
class SingleLanguageDependencyAsNode : INode {
    var moduleReference: SModuleReference? = null
        private set
    var languageVersion = 0
        private set
    private var moduleImporter: SModule? = null
    private var modelImporter: SModel? = null

    constructor(moduleReference: SModuleReference, languageVersion: Int, importer: SModule) {
        this.moduleReference = moduleReference
        this.languageVersion = languageVersion
        this.moduleImporter = importer
    }

    constructor(moduleReference: SModuleReference, languageVersion: Int, importer: SModel) {
        this.moduleReference = moduleReference
        this.languageVersion = languageVersion
        modelImporter = importer
    }

    override fun getArea() = MPSArea(MPSModuleRepository.getInstance())

    override val isValid = true

    override val reference =
        if (moduleImporter != null) {
            NodeReference(moduleImporter!!.moduleReference, moduleReference!!.moduleId)
        } else if (modelImporter != null) {
            NodeReference(modelImporter!!.reference, moduleReference!!.moduleId)
        } else {
            throw IllegalStateException()
        }

    override val concept = BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency

    @Deprecated("use getContainmentLink()")
    override val roleInParent: String?
        get() = if (this.moduleImporter != null) {
            BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies.getSimpleName()
        } else if (this.modelImporter != null) {
            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages.getSimpleName()
        } else {
            null
        }

    override val parent =
        if (moduleImporter != null) {
            MPSModuleAsNode.wrap(moduleImporter)
        } else if (modelImporter != null) {
            MPSModelAsNode.wrap(modelImporter)
        } else {
            null
        }

    @Deprecated("use IChildLink instead of String")
    override fun getChildren(role: String?): MutableList<INode> = Collections.emptyList()

    override val allChildren = concept.getAllChildLinks().map { getChildren(it.name) }.flatten()

    @Deprecated("use IChildLink instead of String")
    override fun moveChild(role: String?, index: Int, child: INode) = throw UnsupportedOperationException()

    @Deprecated("use IChildLink instead of String")
    override fun addNewChild(role: String?, index: Int, concept: IConcept?) = throw UnsupportedOperationException()

    override fun removeChild(child: INode) = throw UnsupportedOperationException()

    @Deprecated("use IReferenceLink instead of String")
    override fun getReferenceTarget(role: String) = null

    @Deprecated("use IReferenceLink instead of String")
    override fun setReferenceTarget(role: String, target: INode?) = throw UnsupportedOperationException()

    @Deprecated("use getPropertyValue(IProperty)")
    override fun getPropertyValue(role: String) =
        when (role) {
            BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version.getSimpleName() -> {
                this.languageVersion.toString()
            }

            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.getSimpleName() -> {
                this.moduleReference?.moduleName
            }

            BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid.getSimpleName() -> {
                this.moduleReference?.moduleId.toString()
            }

            else -> {
                null
            }
        }

    @Deprecated("use setPropertyValue(IProperty, String?)")
    override fun setPropertyValue(role: String, value: String?) = throw UnsupportedOperationException()

    @Deprecated("use getPropertyLinks()")
    override fun getPropertyRoles() = concept.getAllProperties().map { it.name }

    @Deprecated("use getReferenceLinks()")
    override fun getReferenceRoles() = concept.getAllReferenceLinks().map { it.name }

    override fun getConceptReference() = concept.getReference()

    inner class NodeReference : INodeReference {
        private var userModuleReference: SModuleReference? = null
        private var userModel: SModelReference? = null
        private var usedModuleId: SModuleId? = null

        constructor(userModuleReference: SModuleReference, usedModuleId: SModuleId) {
            this.userModuleReference = userModuleReference
            this.usedModuleId = usedModuleId
        }

        constructor(userModel: SModelReference, usedModuleId: SModuleId) {
            this.userModel = userModel
            this.usedModuleId = usedModuleId
        }

        @Deprecated("use .resolveIn(INodeResolutionScope)", replaceWith = ReplaceWith("resolveIn(area!!)"))
        override fun resolveNode(area: IArea?): INode? {
            var repo: SRepository? = null
            if (area != null) {
                val areas = area.collectAreas()
                repo = areas.filterIsInstance<MPSArea>().map { it.repository }.firstOrNull()
            }
            if (repo == null) {
                repo = MPSModuleRepository.getInstance()
            }

            return if (this.userModuleReference != null) {
                val user = userModuleReference!!.resolve(repo!!) ?: return null
                MPSModuleAsNode.wrap(user)?.findSingleLanguageDependency(this.usedModuleId!!)
            } else if (this.userModel != null) {
                val model = userModel?.resolve(repo)
                MPSModelAsNode.wrap(model)?.findSingleLanguageDependency(this.usedModuleId!!)
            } else {
                null
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || other !is NodeReference) {
                return false
            }

            if (this.userModuleReference != other.userModuleReference) {
                return false
            }
            if (this.userModel != other.userModel) {
                return false
            }
            if (this.usedModuleId != other.usedModuleId) {
                return false
            }

            return true
        }

        @Override
        override fun hashCode(): Int {
            var result = 1
            result = 31 * result + (userModuleReference?.hashCode() ?: 0)
            result = 11 * result + (usedModuleId?.hashCode() ?: 0)
            result = 37 * result + (userModel?.hashCode() ?: 0)
            return result
        }
    }
}

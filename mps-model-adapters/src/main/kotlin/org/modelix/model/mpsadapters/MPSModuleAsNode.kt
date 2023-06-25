/*
 * Copyright 2003-2023 JetBrains s.r.o.
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

import jetbrains.mps.project.ProjectBase
import jetbrains.mps.project.ProjectManager
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IDeprecatedNodeDefaults
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.area.IArea

data class MPSModuleAsNode(val module: SModule) : IDeprecatedNodeDefaults {
    override fun getArea(): IArea {
        TODO("Not yet implemented")
    }

    override val isValid: Boolean
        get() = TODO("Not yet implemented")
    override val reference: INodeReference
        get() = TODO("Not yet implemented")
    override val concept: IConcept
        get() = RepositoryLanguage.Module
    override val parent: INode?
        get() = module.repository?.let { MPSRepositoryAsNode(it) }

    override fun getConceptReference(): IConceptReference? {
        TODO("Not yet implemented")
    }

    override val allChildren: Iterable<INode>
        get() = module.models.map { MPSModelAsNode(it) }

    override fun removeChild(child: INode) {
        TODO("Not yet implemented")
    }

    override fun getContainmentLink(): IChildLink? {
        TODO("Not yet implemented")
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        return if (link.getUID().endsWith("0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/474657388638618898") ||
            link.getUID().contains("models") ||
            link.getSimpleName() == "models"
        ) {
            module.models.map { MPSModelAsNode(it) }
        } else {
            emptyList()
        }
    }

    override fun moveChild(role: IChildLink, index: Int, child: INode) {
        TODO("Not yet implemented")
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode {
        TODO("Not yet implemented")
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode {
        TODO("Not yet implemented")
    }

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        TODO("Not yet implemented")
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        TODO("Not yet implemented")
    }

    override fun getPropertyValue(property: IProperty): String? {
        return if (property.getUID().endsWith(RepositoryLanguage.NamePropertyUID) ||
            property.getUID().contains("name") ||
            property.getSimpleName() == "name"
        ) {
            module.moduleName
        } else if (property.getUID().endsWith(RepositoryLanguage.VirtualPackagePropertyUID) ||
            property.getUID().contains("virtualPackage") ||
            property.getSimpleName() == "virtualPackage"
        ) {
            ProjectManager.getInstance().openedProjects.asSequence()
                .filterIsInstance<ProjectBase>()
                .mapNotNull { it.getPath(module) }
                .firstOrNull()
                ?.virtualFolder
        } else {
            null
        }
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        TODO("Not yet implemented")
    }

    override fun getPropertyLinks(): List<IProperty> {
        return concept.getAllProperties()
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        return concept.getAllReferenceLinks()
    }
}

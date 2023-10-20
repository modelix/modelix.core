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
import jetbrains.mps.smodel.tempmodel.TempModule
import jetbrains.mps.smodel.tempmodel.TempModule2
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NullChildLink
import org.modelix.model.area.IArea

data class MPSRepositoryAsNode(val repository: SRepository) : IDefaultNodeAdapter {

    override fun getArea(): IArea {
        return MPSArea(repository)
    }

    override val reference: INodeReference
        get() = NodeReference("mps-repository")
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.Repository
    override val parent: INode?
        get() = null

    override val allChildren: Iterable<INode>
        get() = repository.modules.map { MPSModuleAsNode(it) }

    override fun getContainmentLink(): IChildLink? {
        return null
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        return if (link is NullChildLink) {
            return emptyList()
        } else if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)) {
            repository.modules.filter { !it.isTempModule() }.map { MPSModuleAsNode(it) }
        } else if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Repository.projects)) {
            ProjectManager.getInstance().openedProjects
                .filterIsInstance<ProjectBase>()
                .map { MPSProjectAsNode(it) }
        } else if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.Repository.tempModules)) {
            repository.modules.filter { it.isTempModule() }.map { MPSModuleAsNode(it) }
        } else {
            emptyList()
        }
    }
}

private fun SModule.isTempModule(): Boolean = this is TempModule || this is TempModule2

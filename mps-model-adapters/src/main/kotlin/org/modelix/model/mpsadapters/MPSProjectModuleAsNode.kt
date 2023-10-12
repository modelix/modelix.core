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

import jetbrains.mps.project.ProjectBase
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.area.IArea

data class MPSProjectModuleAsNode(val project: ProjectBase, val module: SModule) : IDefaultNodeAdapter {

    companion object {
        private val builtinProjectModule = BuiltinLanguages.MPSRepositoryConcepts.ProjectModule
    }

    override val reference: INodeReference
        get() = TODO("Not yet implemented")
    override val concept: IConcept
        get() = builtinProjectModule
    override val parent: INode
        get() = MPSProjectAsNode(project)

    override val allChildren: Iterable<INode>
        get() = emptyList()

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        if (link.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)) {
            return MPSModuleAsNode(module)
        }
        return null
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        return getReferenceTarget(role)?.reference
    }

    override fun getPropertyValue(property: IProperty): String? {
        if (property.conformsTo(builtinProjectModule.virtualFolder)) {
            return project.getPath(module)?.virtualFolder
        }
        return null
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        if (property.conformsTo(builtinProjectModule.virtualFolder)) {
            project.setVirtualFolder(module, value)
        }
    }

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules
    }

    override fun getArea(): IArea {
        return MPSArea(project.repository)
    }
}

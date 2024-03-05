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
import org.jetbrains.mps.openapi.module.SDependencyScope
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.area.IArea

data class MPSModuleDependencyAsNode(
    val moduleReference: SModuleReference,
    val moduleVersion: Int,
    val explicit: Boolean,
    val reexport: Boolean,
    val importer: SModule,
    val dependencyScope: SDependencyScope?,
) : IDefaultNodeAdapter {

    override fun getContainmentLink(): IChildLink {
        return BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies
    }

    override fun getArea(): IArea {
        return MPSArea(importer.repository ?: MPSModuleRepository.getInstance())
    }

    override val reference: INodeReference
        get() = MPSModuleDependencyReference(
            usedModuleId = moduleReference.moduleId,
            userModuleReference = importer.moduleReference,
        )
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency
    override val parent: INode
        get() = MPSModuleAsNode(importer)

    override fun getPropertyValue(property: IProperty): String? {
        val moduleDependency = BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency

        return if (property.conformsTo(moduleDependency.explicit)) {
            explicit.toString()
        } else if (property.conformsTo(moduleDependency.name)) {
            moduleReference.moduleName
        } else if (property.conformsTo(moduleDependency.reexport)) {
            reexport.toString()
        } else if (property.conformsTo(moduleDependency.uuid)) {
            moduleReference.moduleId.toString()
        } else if (property.conformsTo(moduleDependency.version)) {
            moduleVersion.toString()
        } else if (property.conformsTo(moduleDependency.scope)) {
            dependencyScope?.toString() ?: "UNSPECIFIED"
        } else {
            null
        }
    }
}

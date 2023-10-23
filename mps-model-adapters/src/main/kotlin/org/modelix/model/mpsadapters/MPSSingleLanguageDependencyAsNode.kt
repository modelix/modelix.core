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

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.area.IArea

data class MPSSingleLanguageDependencyAsNode(
    val moduleReference: SModuleReference,
    val languageVersion: Int,
    val moduleImporter: SModule? = null,
    val modelImporter: SModel? = null,
) : IDefaultNodeAdapter {

    override fun getPropertyValue(property: IProperty): String? {
        return if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)) {
            languageVersion.toString()
        } else if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name)) {
            moduleReference.moduleName
        } else if (property.conformsTo(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)) {
            moduleReference.moduleId.toString()
        } else {
            null
        }
    }

    override fun getContainmentLink(): IChildLink {
        return if (moduleImporter != null) {
            BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies
        } else if (modelImporter != null) {
            BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages
        } else {
            throw IllegalStateException("No importer found for $this")
        }
    }

    override fun getArea(): IArea {
        val repo = moduleImporter?.repository ?: modelImporter?.repository
        checkNotNull(repo) { "No importer found for $this" }
        return MPSArea(repo)
    }

    override val reference: INodeReference
        get() = MPSModuleReference(moduleReference)
    override val concept: IConcept
        get() = BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency
    override val parent: INode
        get() = if (moduleImporter != null) {
            MPSModuleAsNode(moduleImporter)
        } else if (modelImporter != null) {
            MPSModelAsNode(modelImporter)
        } else {
            throw IllegalStateException("No importer found for $this")
        }
}

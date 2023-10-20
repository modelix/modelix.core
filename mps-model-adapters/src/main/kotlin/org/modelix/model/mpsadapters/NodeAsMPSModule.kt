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
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode

class NodeAsMPSModule private constructor(val node: INode, val repository: SRepository?) : SModule {

    companion object {
        fun wrap(modelNode: INode, repository: SRepository?): SModule = NodeAsMPSModule(modelNode, repository)
    }

    init {
        check(node.concept?.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Module) == true) { "Not a module: ${node.concept}" }
    }

    override fun addModuleListener(listener: SModuleListener?) = throw UnsupportedOperationException("Not implemented")

    override fun getDeclaredDependencies() = throw UnsupportedOperationException("Not implemented")

    override fun getFacets() = throw UnsupportedOperationException("Not implemented")

    override fun getModel(id: SModelId?) = throw UnsupportedOperationException("Not implemented")

    override fun getModelRoots() = throw UnsupportedOperationException("Not implemented")

    override fun getModels() = node.getChildren(Module.models).map { NodeAsMPSModel.wrap(it, sRepository) }

    override fun getModuleId() = throw UnsupportedOperationException("Not implemented")

    override fun getModuleName() = node.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)

    override fun getModuleReference() = throw UnsupportedOperationException("Not implemented")

    override fun getRepository(): SRepository = sRepository ?: MPSModuleRepository.getInstance()

    override fun getUsedLanguages() = throw UnsupportedOperationException("Not implemented")

    override fun isPackaged() = false

    override fun isReadOnly() = true

    override fun removeModuleListener(listener: SModuleListener?) =
        throw UnsupportedOperationException("Not implemented")

    override fun getUsedLanguageVersion(usedLanguage: SLanguage) =
        throw UnsupportedOperationException("Not implemented")
}

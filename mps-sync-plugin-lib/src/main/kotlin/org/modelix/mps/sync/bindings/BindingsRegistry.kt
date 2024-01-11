/*
 * Copyright (c) 2024.
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

package org.modelix.mps.sync.bindings

import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IBinding
import java.util.stream.Stream
import kotlin.streams.toList

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class BindingsRegistry private constructor() {

    companion object {
        val instance = BindingsRegistry()
    }

    private val modelBindingsByModule = mutableMapOf<SModule, LinkedHashSet<ModelBinding>>()
    private val moduleBindings = LinkedHashSet<ModuleBinding>()

    fun addModelBinding(binding: ModelBinding) =
        modelBindingsByModule.computeIfAbsent(binding.model.module!!) { LinkedHashSet() }.add(binding)

    fun addModuleBinding(binding: ModuleBinding) = moduleBindings.add(binding)

    fun removeModelBinding(module: SModule, binding: ModelBinding) = modelBindingsByModule[module]?.remove(binding)

    fun removeModuleBinding(binding: ModuleBinding) {
        val module = binding.module
        check(
            modelBindingsByModule.getOrDefault(module, LinkedHashSet()).isEmpty(),
        ) { "$binding cannot be removed, because not all of its model' bindings have been removed." }

        modelBindingsByModule.remove(module)
        moduleBindings.remove(binding)
    }

    fun getModelBindings(): List<ModelBinding> =
        modelBindingsByModule.values.flatten().toCollection(mutableListOf()).toList()

    fun getModelBindings(module: SModule): Set<ModelBinding>? = modelBindingsByModule[module]?.toSet()

    fun getModuleBindings(): List<ModuleBinding> = moduleBindings.toList()

    fun getModelBinding(model: SModelBase) = getModelBindings().find { it.model == model }

    fun getModelBinding(modelId: SModelId) = getModelBindings().find { it.model.modelId == modelId }

    fun getModuleBinding(module: AbstractModule) = moduleBindings.find { it.module == module }

    fun getAllBindings(): List<IBinding> = Stream.concat(getModelBindings().stream(), moduleBindings.stream()).toList()
}

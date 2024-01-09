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
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class BindingsRegistry private constructor() {

    companion object {
        val instance = BindingsRegistry()
    }

    private val modelBindingsByModule = mutableMapOf<SModule, MutableSet<ModelBinding>>()
    private val moduleBindings = mutableSetOf<ModuleBinding>()

    private val modelBindings = modelBindingsByModule.values.flatten().toCollection(mutableSetOf()).toSet()

    fun addModelBinding(binding: ModelBinding) =
        modelBindingsByModule.computeIfAbsent(binding.model.module!!) { mutableSetOf() }.add(binding)

    fun addModuleBinding(binding: ModuleBinding) = moduleBindings.add(binding)

    fun removeModelBinding(binding: ModelBinding) = modelBindingsByModule[binding.model.module]?.remove(binding)

    fun removeModuleBinding(binding: ModuleBinding) = moduleBindings.remove(binding)

    fun getModelBindings(): Set<ModelBinding> = modelBindings

    fun getModelBindings(module: SModule): Set<ModelBinding>? = modelBindingsByModule[module]?.toSet()

    fun getModuleBindings(): Set<ModuleBinding> = moduleBindings

    fun getModelBinding(model: SModelBase) = modelBindings.find { it.model == model }

    fun getModuleBinding(module: AbstractModule) = moduleBindings.find { it.module == module }
}

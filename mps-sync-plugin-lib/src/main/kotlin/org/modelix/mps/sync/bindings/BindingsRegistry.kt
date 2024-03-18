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
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.synchronizedMap
import java.util.concurrent.LinkedBlockingQueue

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object BindingsRegistry {

    private val modelBindingsByModule = synchronizedMap<SModule, MutableSet<ModelBinding>>()
    private val moduleBindings = synchronizedLinkedHashSet<ModuleBinding>()

    val changedBindings = LinkedBlockingQueue<BindingState>()

    fun addModelBinding(binding: ModelBinding) {
        modelBindingsByModule.computeIfAbsent(binding.model.module!!) { synchronizedLinkedHashSet() }.add(binding)
        bindingAdded(binding)
    }

    fun addModuleBinding(binding: ModuleBinding) {
        moduleBindings.add(binding)
        bindingAdded(binding)
    }

    fun removeModelBinding(module: SModule, binding: ModelBinding) {
        modelBindingsByModule[module]?.remove(binding)
        bindingRemoved(binding)
    }

    fun removeModuleBinding(binding: ModuleBinding) {
        val module = binding.module
        check(
            modelBindingsByModule.getOrDefault(module, LinkedHashSet()).isEmpty(),
        ) { "$binding cannot be removed, because not all of its model' bindings have been removed." }

        modelBindingsByModule.remove(module)
        moduleBindings.remove(binding)
        bindingRemoved(binding)
    }

    fun getModelBindings(): List<ModelBinding> =
        modelBindingsByModule.values.flatten().toCollection(mutableListOf()).toList()

    fun getModelBindings(module: SModule): Set<ModelBinding>? = modelBindingsByModule[module]?.toSet()

    fun getModuleBindings(): List<ModuleBinding> = moduleBindings.toList()

    fun getModelBinding(model: SModelBase) = getModelBindings().find { it.model == model }

    fun getModelBinding(modelId: SModelId) = getModelBindings().find { it.model.modelId == modelId }

    fun getModuleBinding(module: AbstractModule) = moduleBindings.find { it.module == module }

    fun bindingActivated(binding: IBinding) =
        changedBindings.put(BindingState(binding, BindingLifecycleState.ACTIVATE))

    private fun bindingAdded(binding: IBinding) =
        changedBindings.put(BindingState(binding, BindingLifecycleState.ADD))

    private fun bindingRemoved(binding: IBinding) =
        changedBindings.put(BindingState(binding, BindingLifecycleState.REMOVE))
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class BindingState(
    val binding: IBinding,
    val state: BindingLifecycleState,
)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
enum class BindingLifecycleState {
    ADD,
    REMOVE,
    ACTIVATE,
}

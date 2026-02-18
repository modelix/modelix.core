package org.modelix.mps.sync3

import org.modelix.mps.multiplatform.model.MPSModuleReference

class ModuleToBindingMapping {
    private val mappings = HashMap<MPSModuleReference, BindingId>()

    @Synchronized
    fun assign(module: MPSModuleReference, binding: BindingId) {
        mappings[module] = binding
    }

    @Synchronized
    fun getBinding(module: MPSModuleReference): BindingId? {
        return mappings[module]
    }
}

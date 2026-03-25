package org.modelix.mps.sync3

import org.modelix.mps.multiplatform.model.MPSModuleReference

interface IModuleToBindingMapping {
    fun assign(module: MPSModuleReference, binding: BindingId)
    fun getBinding(module: MPSModuleReference): BindingId?
}

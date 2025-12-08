package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference

data class MPSModuleDependencyReference(
    val usedModuleId: String,
    val userModuleReference: MPSModuleReference,
) : INodeReference() {

    companion object {
        const val PREFIX = "mps-module-dep"
        const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        return "$PREFIX:$usedModuleId$SEPARATOR${userModuleReference.toMPSString()}"
    }
}

package org.modelix.mps.sync3

import org.jetbrains.mps.openapi.module.SModuleId

interface IModuleMappings {
    fun getAllModuleOwners(): Map<SModuleId, IModuleOwnerId>
    fun getModuleOwner(moduleId: SModuleId): IModuleOwnerId?
}

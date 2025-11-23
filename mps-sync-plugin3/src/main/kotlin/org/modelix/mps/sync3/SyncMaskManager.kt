package org.modelix.mps.sync3

import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.mpsadapters.MPSProjectAdapter
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.model.mpsadapters.MPSProjectReference
import org.modelix.model.mpsadapters.toModelix
import org.modelix.model.sync.bulk.IModelMask
import org.modelix.mps.model.sync.bulk.MPSProjectSyncMask
import org.modelix.mps.multiplatform.model.MPSModuleReference

/**
 * This class manages which binding is responsible for synchronizing which module.
 * It prevents a module from being synchronized multiple times.
 */
class SyncMaskManager {
    private val moduleToBinding = HashMap<MPSModuleReference, BindingId>()
    private val projectToBinding = HashMap<MPSProjectReference, BindingId>()
    private var primaryBinding: BindingId? = null

    fun getMask(bindingId: BindingId, isMPSSide: Boolean): IModelMask {
        val projects = MPSProjectAsNode.getAllProjects()
            .filterIsInstance<MPSProjectAdapter>()
            .filter { projectToBinding[MPSProjectAsNode(it).getNodeReference()] == bindingId }
            .map { it.mpsProject as MPSProject }
        return if (primaryBinding == bindingId) {
            MPSProjectSyncMask(
                projects = projects,
                isMPSSide = isMPSSide,
                excludedModules = moduleToBinding.filterValues { it != bindingId }.keys,
            )
        } else {
            MPSProjectSyncMask(
                projects = projects,
                isMPSSide = isMPSSide,
                includedModules = moduleToBinding.filterValues { it == bindingId }.keys,
            )
        }
    }

    /**
     * The primary binding will synchronize all modules that are not explicitly assigned to any other binding.
     */
    fun setAsPrimary(bindingId: BindingId) {
        primaryBinding = bindingId
    }

    private fun getOrSetPrimaryBinding(bindingId: BindingId): BindingId {
        return primaryBinding ?: bindingId.also { primaryBinding = it }
    }

    fun assign(bindingId: BindingId, module: SModule) {
        assign(bindingId, module.moduleReference.toModelix())
    }

    fun assign(bindingId: BindingId, module: MPSModuleReference) {
        if (moduleToBinding[module] == getOrSetPrimaryBinding(bindingId)) return
        moduleToBinding[module] = bindingId
    }

    fun assign(bindingId: BindingId, project: org.jetbrains.mps.openapi.project.Project) {
        assign(bindingId, MPSProjectReference(project))
    }

    fun assign(bindingId: BindingId, project: MPSProjectReference) {
        if (projectToBinding[project] == getOrSetPrimaryBinding(bindingId)) return
        projectToBinding[project] = bindingId
    }
}

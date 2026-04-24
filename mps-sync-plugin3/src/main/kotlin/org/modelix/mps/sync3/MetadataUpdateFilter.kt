package org.modelix.mps.sync3

import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.mpsadapters.MPSGenericNodeAdapter
import org.modelix.model.sync.bulk.ModelSynchronizer

/**
 * Some meta-data changes don't fire a separate event but just a generic 'module changed' event.
 * This filter ensures that meta-data is always synchronized consistently, meaning if there is any change of the
 * meta-data a full sync is done on the meta-data inside that module.
 */
class MetadataUpdateFilter(val changedModules: Set<SModuleReference>, val wrapped: ModelSynchronizer.IIncrementalUpdateInformation) : ModelSynchronizer.IIncrementalUpdateInformation {
    private fun isInsideChangedModule(node: IReadableNode): Boolean {
        try {
            if (node is MPSGenericNodeAdapter<*>) {
                val module = node.getOwningMPSModule()
                if (module != null) {
                    if (changedModules.contains(module.moduleReference)) {
                        return true
                    }
                    if (module is Generator) {
                        if (changedModules.contains(module.sourceLanguage().sourceModuleReference)) {
                            return true
                        }
                    }
                    if (module is Language) {
                        for (generator in module.generators) {
                            if (changedModules.contains(generator.moduleReference)) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            return false
        }
        return false
    }

    override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean {
        if (isInsideChangedModule(subtreeRoot)) return true
        return wrapped.needsDescentIntoSubtree(subtreeRoot)
    }

    override fun needsSynchronization(node: IReadableNode): Boolean {
        if (isInsideChangedModule(node)) return true
        return wrapped.needsSynchronization(node)
    }
}

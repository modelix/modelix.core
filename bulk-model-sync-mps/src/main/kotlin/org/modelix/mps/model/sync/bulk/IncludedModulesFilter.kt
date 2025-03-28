package org.modelix.mps.model.sync.bulk

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IReadableNode
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.isModuleIncluded

/**
 * A filter that skips nodes, which
 *  (1) represent MPS modules and
 *  (2) do not match the included module names ([includedModules]) or prefixes ([includedModulePrefixes]) and
 *  (3) are not excluded via [excludedModules] or [excludedModulesPrefixes].
 *
 * Note: This is currently not meant to be used standalone.
 * It should be used with other filters in a [CompositeFilter].
 */
@Deprecated("Use IModelMask")
class IncludedModulesFilter(
    val includedModules: Collection<String>,
    val includedModulePrefixes: Collection<String>,
    val excludedModules: Collection<String> = emptySet(),
    val excludedModulesPrefixes: Collection<String> = emptySet(),
) : ModelSynchronizer.IIncrementalUpdateInformation {
    override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean {
        if (subtreeRoot.getConceptReference() != BuiltinLanguages.MPSRepositoryConcepts.Module.getReference()) return true
        val moduleName = subtreeRoot.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) ?: return true

        return isModuleIncluded(
            moduleName = moduleName,
            includedModules = includedModules,
            includedPrefixes = includedModulePrefixes,
            excludedModules = excludedModules,
            excludedPrefixes = excludedModulesPrefixes,
        )
    }

    override fun needsSynchronization(node: IReadableNode): Boolean {
        return true // We don't want to restrict this here. Other filters in the CompositeFilter will decide this.
    }
}

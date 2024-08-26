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

package org.modelix.mps.model.sync.bulk

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
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
class IncludedModulesFilter(
    val includedModules: Collection<String>,
    val includedModulePrefixes: Collection<String>,
    val excludedModules: Collection<String> = emptySet(),
    val excludedModulesPrefixes: Collection<String> = emptySet(),
) : ModelSynchronizer.IFilter {
    override fun needsDescentIntoSubtree(subtreeRoot: INode): Boolean {
        if (subtreeRoot.getConceptReference() != BuiltinLanguages.MPSRepositoryConcepts.Module.getReference()) return true
        val moduleName = subtreeRoot.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) ?: return true

        return isModuleIncluded(
            moduleName = moduleName,
            includedModules = includedModules,
            includedPrefixes = includedModulePrefixes,
            excludedModules = excludedModules,
            excludedPrefixes = excludedModulesPrefixes,
        )
    }

    override fun needsSynchronization(node: INode): Boolean {
        return true // We don't want to restrict this here. Other filters in the CompositeFilter will decide this.
    }
}

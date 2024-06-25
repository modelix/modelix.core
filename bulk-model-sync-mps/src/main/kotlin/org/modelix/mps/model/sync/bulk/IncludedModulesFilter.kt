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

/**
 * A filter that skips nodes, which represent MPS modules and do not match
 * the included module names ([includedModules]) or prefixes ([includedModulePrefixes]).
 *
 * Note: This is currently not meant to be used standalone.
 * It should be used with other filters in a [CompositeFilter].
 */
class IncludedModulesFilter(
    val includedModules: Collection<String>,
    val includedModulePrefixes: Collection<String>,
) : ModelSynchronizer.IFilter {
    override fun needsDescentIntoSubtree(subtreeRoot: INode): Boolean {
        if (subtreeRoot.getConceptReference() != BuiltinLanguages.MPSRepositoryConcepts.Module.getReference()) return true
        val moduleName = subtreeRoot.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) ?: return true

        return includedModules.any { it == moduleName } || includedModulePrefixes.any { moduleName.startsWith(it) }
    }

    override fun needsSynchronization(node: INode): Boolean {
        return true // We don't want to restrict this here. Other filters in the CompositeFilter will decide this.
    }
}

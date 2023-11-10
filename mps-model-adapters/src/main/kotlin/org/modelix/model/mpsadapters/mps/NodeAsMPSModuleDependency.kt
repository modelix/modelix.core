/*
 * Copyright (c) 2023.
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

package org.modelix.model.mpsadapters.mps

import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SDependencyScope
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.mpsadapters.MPSModuleDependencyAsNode
import org.modelix.model.mpsadapters.MPSModuleReference

data class NodeAsMPSModuleDependency(val node: MPSModuleDependencyAsNode, val sRepository: SRepository?) : SDependency {

    override fun getScope(): SDependencyScope = checkNotNull(node.dependencyScope) { "Invalid dependency scope for dependency $node" }
    override fun isReexport() = node.reexport
    override fun getTargetModule() = node.moduleReference

    override fun getTarget(): SModule? {
        val ref = MPSModuleReference(node.moduleReference)
        return node.getArea().resolveNode(ref)?.let { NodeAsMPSModule(it, sRepository) }
    }
}

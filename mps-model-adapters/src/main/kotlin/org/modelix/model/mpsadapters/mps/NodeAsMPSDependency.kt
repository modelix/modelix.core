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
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.INode
import org.modelix.model.mpsadapters.MPSModuleDependencyAsNode
import org.modelix.model.mpsadapters.MPSModuleReference

data class NodeAsMPSDependency(val node: INode, val sRepository: SRepository?) : SDependency {
    override fun getScope(): SDependencyScope {
        return when (node) {
            is MPSModuleDependencyAsNode -> node.dependencyScope
            else -> null
        } ?: error("Node is not a valid dependency")
    }

    override fun isReexport(): Boolean {
        return when (node) {
            is MPSModuleDependencyAsNode -> node.reexport
            else -> error("Node is not a valid dependency")
        }
    }

    override fun getTargetModule(): SModuleReference {
        return when (node) {
            is MPSModuleDependencyAsNode -> node.moduleReference
            else -> null
        } ?: error("Node is not a valid dependency")
    }

    override fun getTarget(): SModule? {
        val ref = MPSModuleReference(targetModule)
        return node.getArea().resolveNode(ref)?.let { NodeAsMPSModule(it, sRepository) }
    }
}

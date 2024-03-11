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

package org.modelix.mps.sync.mps

import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepositoryListenerBase
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class RepositoryChangeListener(branch: IBranch) : SRepositoryListenerBase() {

    private val bindingsRegistry = BindingsRegistry

    private val nodeSynchronizer = NodeSynchronizer(branch)

    override fun moduleRemoved(module: SModuleReference) {
        if (ApplicationLifecycleTracker.applicationClosing) {
            return
        }

        val binding = bindingsRegistry.getModuleBindings().find { it.module.moduleId == module.moduleId }
        if (binding != null) {
            nodeSynchronizer.removeNode(
                parentNodeIdProducer = { ITree.ROOT_ID },
                childNodeIdProducer = { it[module.moduleId]!! },
            )
            binding.deactivate(removeFromServer = true)
        }
    }
}

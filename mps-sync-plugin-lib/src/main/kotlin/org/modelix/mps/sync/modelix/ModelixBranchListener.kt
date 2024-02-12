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

package org.modelix.mps.sync.modelix

import jetbrains.mps.project.MPSProject
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.incremental.ModelixTreeChangeVisitor

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelixBranchListener(
    private val replicatedModel: ReplicatedModel,
    private val project: MPSProject,
    private val languageRepository: MPSLanguageRepository,
    private val nodeMap: MpsToModelixMap,
    private val syncQueue: SyncQueue,
) : IBranchListener {
    override fun treeChanged(oldTree: ITree?, newTree: ITree) {
        if (oldTree != null) {
            newTree.visitChanges(
                oldTree,
                ModelixTreeChangeVisitor(replicatedModel, project, languageRepository, nodeMap, syncQueue),
            )
        }
    }
}

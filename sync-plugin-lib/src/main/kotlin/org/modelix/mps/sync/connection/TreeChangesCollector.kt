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

package org.modelix.mps.sync.connection

import de.q60.mps.incremental.runtime.DependencyKey
import de.q60.mps.shadowmodels.runtime.engine.AllChildrenDependency
import de.q60.mps.shadowmodels.runtime.engine.ContainmentDependency
import de.q60.mps.shadowmodels.runtime.engine.RoleDependency
import de.q60.mps.shadowmodels.runtime.model.persistent.SM_PNodeDependency
import org.modelix.model.api.IBranch
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.PNodeAdapter

// status: ready to test
class TreeChangesCollector(private val branch: IBranch) : ITreeChangeVisitorEx {

    val changes = mutableListOf<DependencyKey>()

    private fun toNodeRef(nodeId: Long): INodeReference = PNodeAdapter(nodeId, branch).reference

    override fun containmentChanged(nodeId: Long) {
        changes.add(ContainmentDependency(toNodeRef(nodeId)))
    }

    override fun childrenChanged(nodeId: Long, role: String?) {
        changes.add(RoleDependency(toNodeRef(nodeId), role))
        changes.add(AllChildrenDependency(toNodeRef(nodeId)))
    }

    override fun referenceChanged(nodeId: Long, role: String) {
        changes.add(RoleDependency(toNodeRef(nodeId), role))
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        changes.add(RoleDependency(toNodeRef(nodeId), role))
    }

    override fun nodeRemoved(nodeId: Long) {
        changes.add(SM_PNodeDependency(branch, nodeId))
    }

    override fun nodeAdded(nodeId: Long) {
        changes.add(SM_PNodeDependency(branch, nodeId))
    }
}

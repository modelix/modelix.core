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

package org.modelix.mps.sync.binding

import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction

class ProjectModulesSynchronizer(projectNodeId: Long, mpsProject: MPSProject) {
    fun syncToMPS(tree: ITree): Map<Long, SModule> {
        TODO("Not yet implemented")
    }

    fun syncToCloud(transaction: IWriteTransaction): Map<Long, SModule> {
        TODO()
    }
}

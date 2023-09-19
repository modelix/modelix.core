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

package org.modelix.mps.sync.plugin.config

import com.intellij.openapi.project.Project
import org.modelix.model.api.IBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.mps.sync.CloudRepository

class PersistedBindingConfiguration {

    companion object {
        fun getInstance(project: Project?): PersistedBindingConfiguration {
            TODO("Not yet implemented")
        }
    }

    fun addMappedBoundModule(treeInRepository: CloudRepository, cloudModuleNode: PNodeAdapter) {
        TODO("Not yet implemented")
    }

    fun addTransientBoundModule(repositoryInModelServer: CloudRepository, branch: IBranch, cloudNodeId: Long) {
        TODO("Not yet implemented")
    }

    fun addTransientBoundProject(treeInRepository: CloudRepository) {
        TODO()
    }
}

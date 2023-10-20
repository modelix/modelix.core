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

import org.modelix.model.api.IBranch
import org.modelix.model.client.ActiveBranch
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.binding.Binding
import org.modelix.mps.sync.binding.RootBinding
import org.modelix.mps.sync.replication.CloudRepository

interface ModelServerConnectionInterface {

    val baseUrl: String

    fun isConnected(): Boolean
    fun dispose()
    fun getAuthor(): String

    fun addRepository(id: String)
    fun removeRepository(id: String)

    fun trees(): List<CloudRepository>

    fun addBinding(repositoryId: RepositoryId, binding: Binding, callback: Runnable?)
    fun addBinding(repositoryId: RepositoryId, binding: Binding)
    fun removeBinding(binding: Binding)

    fun getActiveBranch(repositoryId: RepositoryId): ActiveBranch
    fun getActiveBranches(): Iterable<ActiveBranch>

    fun getInfoBranch(): IBranch
    fun getRootBinding(repositoryId: RepositoryId): RootBinding
}

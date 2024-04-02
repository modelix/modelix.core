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

package org.modelix.model.server.handlers

import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId

interface IRepositoriesManager {
    /**
     * Used to retrieve the server ID. If needed, the server ID is created and stored.
     *
     * If a server ID was not created yet, it is generated and saved in the database.
     * It gets stored under the current and all legacy database keys.
     *
     * If the server ID was created previously but is only stored under a legacy database key,
     * it also gets stored under the current and all legacy database keys.
     */
    suspend fun maybeInitAndGetSeverId(): String
    fun getRepositories(): Set<RepositoryId>
    suspend fun createRepository(repositoryId: RepositoryId, userName: String?, useRoleIds: Boolean = true): CLVersion
    suspend fun removeRepository(repository: RepositoryId): Boolean

    fun getBranches(repositoryId: RepositoryId): Set<BranchReference>

    suspend fun removeBranches(repository: RepositoryId, branchNames: Set<String>)

    /**
     * Same as [removeBranches] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
    fun removeBranchesBlocking(repository: RepositoryId, branchNames: Set<String>)
    suspend fun getVersion(branch: BranchReference): CLVersion?
    suspend fun getVersionHash(branch: BranchReference): String?
    suspend fun pollVersionHash(branch: BranchReference, lastKnown: String?): String
    suspend fun mergeChanges(branch: BranchReference, newVersionHash: String): String

    /**
     * Same as [mergeChanges] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
    fun mergeChangesBlocking(branch: BranchReference, newVersionHash: String): String
    suspend fun computeDelta(versionHash: String, baseVersionHash: String?): ObjectData
}

fun IRepositoriesManager.getBranchNames(repositoryId: RepositoryId): Set<String> {
    return getBranches(repositoryId).map { it.branchName }.toSet()
}

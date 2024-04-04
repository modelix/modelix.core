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

/**
 * Exposes high-level methods to interact with repositories, their branches and their versions.
 *
 * All methods should use non-blocking IO (or dispatch it appropriately).
 */
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

    suspend fun getRepositories(): Set<RepositoryId>

    /**
     * Create a repository
     *
     * @param repositoryId ID of repository to be created
     * @param userName used as author of the initial version
     * @param useRoleIds whether the tree created for the initial version uses UIDs or names
     *                   to access roles see [ITree.usesRoleIds]
     * @return the created initial version of the created repository
     * @throws RepositoryAlreadyExistsException
     */
    suspend fun createRepository(repositoryId: RepositoryId, userName: String?, useRoleIds: Boolean = true): CLVersion

    /**
     * Remove a repository
     *
     * That data is not yet removed from the database.
     * See https://issues.modelix.org/issue/MODELIX-856
     *
     * @param repositoryId ID of repository to be removed
     * @return true if the repository existed and was removed, false if the repository did not exist
     */
    suspend fun removeRepository(repositoryId: RepositoryId): Boolean
    suspend fun getBranches(repositoryId: RepositoryId): Set<BranchReference>
    suspend fun removeBranches(repositoryId: RepositoryId, branchNames: Set<String>)

    /**
     * @return the current version in this branch or null if the branch does not exist
     */
    suspend fun getVersion(branch: BranchReference): CLVersion?

    /**
     * @return the hash of the current version in this branch or null if the branch does not exist
     */
    suspend fun getVersionHash(branch: BranchReference): String?

    /**
     * Waits for a new version to appear on branch and then returns the hash of the new version.
     *
     * The waiting is limited with a timeout after which the hash of the current version is returned
     * even if no new version was created in the meantime.
     *
     * @param lastKnown returns immediately if the last known version is specified and the current version differs
     * @throws [IllegalStateException] if the branch does not exist or was removed while polling
     * @return hash of the current version on the branch
     */
    suspend fun pollVersionHash(branch: BranchReference, lastKnown: String?): String

    /**
     * Merges the changes from [newVersionHash] into the current version in [branch].
     *
     * @param newVersionHash has of the version to be merged
     * @param branch branch to merge into
     * @return the version hash of the merge version
     */
    suspend fun mergeChanges(branch: BranchReference, newVersionHash: String): String

    /**
     * Computes the object data referenced by [versionHash] but not referenced by [baseVersionHash] (aka. a delta).
     *
     * If no [baseVersionHash] is provided all object data referenced by [versionHash] is returned.
     *
     * @param versionHash hash of the version of which object data is requested
     * @param baseVersionHash optionally hash of a version of which data is already known
     * @return an [ObjectData] for accessing the computed delta.
     *         The delta of object data might be computed eagerly or only when being accessed it.
     */
    suspend fun computeDelta(versionHash: String, baseVersionHash: String?): ObjectData
}

suspend fun IRepositoriesManager.getBranchNames(repositoryId: RepositoryId): Set<String> {
    return getBranches(repositoryId).map { it.branchName }.toSet()
}

class RepositoryAlreadyExistsException(val name: String) : IllegalStateException("Repository '$name' already exists")

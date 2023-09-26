/*
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
package org.modelix.model.client2

import org.modelix.model.IVersion
import org.modelix.model.api.IIdGenerator
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId

/**
 * This interface is meant exclusively for model client usage.
 *
 * It is designed to ensure decoupling between model client usage operations and other aspects,
 * such as lifecycle management.
 * Users of this interface cannot incidentally depend on non-usage functionality.
 * See also: [Interface segregation principle](https://en.wikipedia.org/wiki/Interface_segregation_principle)
 *
 * Specifically, this interface should not be used for managing the client's lifecycle,
 * as the lifecycle management may vary depending on the specific implementation.
 * If you need to manage the client's lifecycle, use the methods in the class interface of the concrete implementations,
 * such as [ModelClientV2].
 */
interface IModelClientV2 {
    fun getClientId(): Int
    fun getIdGenerator(): IIdGenerator
    fun getUserId(): String?

    suspend fun initRepository(repository: RepositoryId): IVersion
    suspend fun listRepositories(): List<RepositoryId>

    suspend fun listBranches(repository: RepositoryId): List<BranchReference>

    suspend fun loadVersion(versionHash: String, baseVersion: IVersion?): IVersion

    /**
     * The pushed version is merged automatically by the server with the current head.
     * The merge result is returned.
     * @param baseVersion Some version that is known to exist on the server.
     *                    Is used for optimizing the amount of data sent to the server.
     */
    suspend fun push(branch: BranchReference, version: IVersion, baseVersion: IVersion?): IVersion

    suspend fun pull(branch: BranchReference, lastKnownVersion: IVersion?): IVersion

    suspend fun pullHash(branch: BranchReference): String

    /**
     * While `pull` returns immediately `poll` returns as soon as a new version, that is different from the given
     * `lastKnownVersion`, is pushed to the server or after some timout specified by the server (usually ~30 seconds).
     */
    suspend fun poll(branch: BranchReference, lastKnownVersion: IVersion?): IVersion

    suspend fun pollHash(branch: BranchReference, lastKnownVersion: IVersion?): String
}

package org.modelix.model.client2

import org.modelix.kotlin.utils.DeprecationInfo
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INode
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.modelql.core.IMonoStep

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
    suspend fun getServerId(): String
    fun getClientId(): Int
    fun getIdGenerator(): IIdGenerator
    fun getUserId(): String?

    fun getStore(repository: RepositoryId): IAsyncObjectStore

    @Deprecated("Provide a RepositoryConfig")
    suspend fun initRepository(repository: RepositoryId, useRoleIds: Boolean = true): IVersion

    @Deprecated("Provide a RepositoryConfig")
    suspend fun initRepositoryWithLegacyStorage(repository: RepositoryId): IVersion

    suspend fun initRepository(config: RepositoryConfig): IVersion

    suspend fun listRepositories(): List<RepositoryId>
    suspend fun deleteRepository(repository: RepositoryId): Boolean
    suspend fun listBranches(repository: RepositoryId): List<BranchReference>

    /**
     * Deletes a branch from a repository if it exists.
     *
     * @param branch the branch to delete
     * @return true if the branch existed and could be deleted, else false.
     */
    suspend fun deleteBranch(branch: BranchReference): Boolean

    @Deprecated("repository ID is required for permission checks")
    @DeprecationInfo("3.7.0", "May be removed with the next major release. Also remove the endpoint from the model-server.")
    suspend fun loadVersion(versionHash: String, baseVersion: IVersion?): IVersion

    suspend fun loadVersion(repositoryId: RepositoryId, versionHash: String, baseVersion: IVersion?): IVersion

    suspend fun lazyLoadVersion(repositoryId: RepositoryId, versionHash: String): IVersion

    suspend fun lazyLoadVersion(branch: BranchReference): IVersion

    /**
     * The pushed version is merged automatically by the server with the current head.
     * The merge result is returned.
     * @param baseVersion Some version that is known to exist on the server.
     *                    Is used for optimizing the amount of data sent to the server.
     */
    suspend fun push(branch: BranchReference, version: IVersion, baseVersion: IVersion?): IVersion

    suspend fun pull(
        branch: BranchReference,
        lastKnownVersion: IVersion?,
        filter: ObjectDeltaFilter = ObjectDeltaFilter(),
    ): IVersion

    suspend fun pullIfExists(branch: BranchReference): IVersion?

    suspend fun pullHash(branch: BranchReference): String

    /**
     * While `pull` returns immediately `poll` returns as soon as a new version, that is different from the given
     * `lastKnownVersion`, is pushed to the server or after some timeout specified by the server (usually ~30 seconds).
     */
    suspend fun poll(branch: BranchReference, lastKnownVersion: IVersion?): IVersion

    suspend fun pollHash(branch: BranchReference, lastKnownHash: String?): String

    suspend fun pollHash(branch: BranchReference, lastKnownVersion: IVersion?): String

    suspend fun <R> query(branch: BranchReference, body: (IMonoStep<INode>) -> IMonoStep<R>): R

    suspend fun <R> query(repositoryId: RepositoryId, versionHash: String, body: (IMonoStep<INode>) -> IMonoStep<R>): R
}

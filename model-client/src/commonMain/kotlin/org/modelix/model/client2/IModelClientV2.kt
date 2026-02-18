package org.modelix.model.client2

import io.ktor.http.Url
import org.modelix.datastructures.history.IHistoryQueries
import org.modelix.datastructures.model.MutationParameters
import org.modelix.datastructures.model.historyAsMutationParameters
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.kotlin.utils.DeprecationInfo
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.BranchInfo
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.modelql.core.IMonoStep
import org.modelix.streams.getSuspending

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

    /**
     * Copy the entire content of the [source] repository to a new [target] repository.
     * All branches and all objects, including indices and even unreachable garbage is copied.
     *
     * Not supported on repositories that use [RepositoryConfig.legacyGlobalStorage].
     * `READ` permission on [source] and `CREATE` permission on target is required.
     *
     * @param target a random ID will be generated if no ID is provided.
     * @return the ID of the created repository
     */
    suspend fun forkRepository(source: RepositoryId, target: RepositoryId? = null): RepositoryId

    @Deprecated("Provide a RepositoryConfig")
    suspend fun initRepositoryWithLegacyStorage(repository: RepositoryId): IVersion

    suspend fun initRepository(config: RepositoryConfig): IVersion
    suspend fun getRepositoryConfig(repository: RepositoryId): RepositoryConfig

    /**
     * Migrate the repository to a different configuration.
     * Should only be used when there are no active clients.
     * There are no guarantees how a client behaves during a config change.
     */
    suspend fun changeRepositoryConfig(config: RepositoryConfig): RepositoryConfig

    suspend fun listRepositories(): List<RepositoryId>
    suspend fun deleteRepository(repository: RepositoryId): Boolean
    suspend fun listBranches(repository: RepositoryId): List<BranchReference>
    suspend fun listBranchesWithHashes(repository: RepositoryId): List<BranchInfo>

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
    suspend fun push(branch: BranchReference, version: IVersion, baseVersion: IVersion?, force: Boolean = false): IVersion
    suspend fun push(branch: BranchReference, version: IVersion, baseVersions: List<IVersion>, force: Boolean = false): IVersion

    /**
     * The pushed version is merged automatically by the server with the current head.
     *
     * If [failIfExists] is true, the push fails if the version already exists on the server.
     * @return The resulting version on the server or null iff the version already exists and [failIfExists] is true.
     * @param baseVersion Some version that is known to exist on the server.
     *                    Is used for optimizing the amount of data sent to the server.
     */
    suspend fun push(branch: BranchReference, version: IVersion, baseVersion: IVersion?, force: Boolean = false, failIfExists: Boolean): IVersion?
    suspend fun push(branch: BranchReference, version: IVersion, baseVersions: List<IVersion>, force: Boolean = false, failIfExists: Boolean): IVersion?

    suspend fun pull(
        branch: BranchReference,
        lastKnownVersion: IVersion?,
        filter: ObjectDeltaFilter = ObjectDeltaFilter(),
    ): IVersion

    suspend fun pullIfExists(branch: BranchReference): IVersion?

    suspend fun pullHash(branch: BranchReference): String
    suspend fun pullHashIfExists(branch: BranchReference): String?

    /**
     * While `pull` returns immediately `poll` returns as soon as a new version, that is different from the given
     * `lastKnownVersion`, is pushed to the server or after some timeout specified by the server (usually ~30 seconds).
     */
    suspend fun poll(branch: BranchReference, lastKnownVersion: IVersion?): IVersion

    suspend fun pollHash(branch: BranchReference, lastKnownHash: String?): String

    suspend fun pollHash(branch: BranchReference, lastKnownVersion: IVersion?): String

    suspend fun <R> query(branch: BranchReference, body: (IMonoStep<INode>) -> IMonoStep<R>): R

    suspend fun <R> query(repositoryId: RepositoryId, versionHash: String, body: (IMonoStep<INode>) -> IMonoStep<R>): R

    fun getFrontendUrl(branch: BranchReference): Url

    fun queryHistory(repositoryId: RepositoryId, headVersion: ObjectHash): IHistoryQueries

    /**
     * Reset the model content to a previous version.
     * A single entry is added to the history containing a single operation.
     * The new version will just reference the old model hash, meaning this is a very lightweight operations.
     */
    suspend fun revertTo(branch: BranchReference, versionHash: ObjectHash): ObjectHash
}

@DelicateModelixApi
suspend fun IModelClientV2.diffAsMutationParameters(repositoryId: RepositoryId, newVersion: ObjectHash, oldVersion: ObjectHash): List<MutationParameters<INodeReference>> {
    val version = lazyLoadVersion(repositoryId, newVersion.toString()) as CLVersion
    return version.historyAsMutationParameters(oldVersion).toList().getSuspending(version.graph)
}

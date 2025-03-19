package org.modelix.model.server.handlers

import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.ITransactionManager
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.StoreManager

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
    @RequiresTransaction
    fun maybeInitAndGetSeverId(): String

    @RequiresTransaction
    fun getRepositories(): Set<RepositoryId>

    @RequiresTransaction
    fun createRepository(repositoryId: RepositoryId, userName: String?, useRoleIds: Boolean = true, legacyGlobalStorage: Boolean = false): CLVersion

    @RequiresTransaction
    fun removeRepository(repository: RepositoryId): Boolean

    @RequiresTransaction
    fun getBranches(repositoryId: RepositoryId): Set<BranchReference>

    /**
     * Same as [removeBranches] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
    @RequiresTransaction
    fun removeBranches(repository: RepositoryId, branchNames: Set<String>)

    @RequiresTransaction
    fun getVersion(branch: BranchReference): CLVersion?
    fun getVersion(repository: RepositoryId, versionHash: String): CLVersion?

    @RequiresTransaction
    fun getVersionHash(branch: BranchReference): String?
    suspend fun pollVersionHash(branch: BranchReference, lastKnown: String?): String

    @RequiresTransaction
    fun mergeChanges(branch: BranchReference, newVersionHash: String): String
    suspend fun mergeChangesWithoutPush(branch: BranchReference, newVersionHash: String): String
    suspend fun computeDelta(repository: RepositoryId?, versionHash: String, baseVersionHash: String?): ObjectData =
        computeDelta(repository, versionHash, ObjectDeltaFilter(knownVersions = setOfNotNull(baseVersionHash)))
    suspend fun computeDelta(repository: RepositoryId?, versionHash: String, filter: ObjectDeltaFilter): ObjectData

    /**
     * The data of a repository is stored separately from other repositories, but that wasn't always the case.
     * For backwards compatibility, existing repositories remain in the global storage.
     *
     * @return null if the repository doesn't exist
     */
    fun isIsolated(repository: RepositoryId): Boolean?

    fun getStoreManager(): StoreManager
    fun getTransactionManager(): ITransactionManager
}

@RequiresTransaction
fun IRepositoriesManager.getBranchNames(repositoryId: RepositoryId): Set<String> {
    return getBranches(repositoryId).map { it.branchName }.toSet()
}

fun IRepositoriesManager.getStoreClient(repository: RepositoryId?, immutable: Boolean): IStoreClient {
    return getStoreManager().getStoreClient(repository?.takeIf { isIsolated(it) ?: false }, immutable)
}

fun IRepositoriesManager.getAsyncStore(repository: RepositoryId?): IAsyncObjectStore {
    return getStoreManager().getAsyncStore(repository?.takeIf { isIsolated(it) ?: false })
}

fun IRepositoriesManager.getLegacyObjectStore(repository: RepositoryId?): IDeserializingKeyValueStore {
    return getStoreManager().getLegacyObjectStore(repository?.takeIf { isIsolated(it) ?: false })
}

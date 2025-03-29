package org.modelix.model.server.handlers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import org.modelix.model.ModelMigrations
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.PBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.commonBaseVersion
import org.modelix.model.lazy.diff
import org.modelix.model.lazy.fullDiff
import org.modelix.model.persistent.HashUtil
import org.modelix.model.persistent.SerializationUtil
import org.modelix.model.server.store.IRepositoryAwareStore
import org.modelix.model.server.store.ITransactionManager
import org.modelix.model.server.store.ObjectInRepository
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.StoreManager
import org.modelix.model.server.store.assertWrite
import org.modelix.model.server.store.pollEntry
import org.modelix.model.server.store.runReadIO
import org.modelix.streams.IExecutableStream
import org.modelix.streams.IStream
import org.slf4j.LoggerFactory
import java.util.UUID

// The methods in this class are almost cohesive, so the number of functions is fine.
@Suppress("complexity.TooManyFunctions")
class RepositoriesManager(val stores: StoreManager) : IRepositoriesManager {
    constructor(store: IRepositoryAwareStore) : this(StoreManager(store))

    init {
        @RequiresTransaction
        fun migrateLegacyRepositoriesList(infoBranch: IBranch) {
            val legacyRepositories = listLegacyRepositories(infoBranch).groupBy { it.repositoryId }
            if (legacyRepositories.isNotEmpty()) {
                ensureRepositoriesAreInList(legacyRepositories.keys)
                for ((legacyRepository, legacyBranches) in legacyRepositories) {
                    ensureBranchesAreInList(legacyRepository, legacyBranches.map { it.branchName }.toSet())
                }
            }
        }

        fun doMigrations() {
            @OptIn(RequiresTransaction::class)
            stores.getTransactionManager().runWrite {
                val repositoryId = RepositoryId("info")
                val v1BranchKey = repositoryId.getBranchReference().getKey()
                val infoVersionHash = stores.getGlobalStoreClient().get(v1BranchKey) ?: return@runWrite
                val infoVersion = CLVersion.loadFromHash(infoVersionHash, getLegacyObjectStore(repositoryId))
                val infoBranch: IBranch = PBranch(infoVersion.getTree(), IdGeneratorDummy())

                ModelMigrations.useResolvedConceptsFromMetaModel(infoBranch)
                migrateLegacyRepositoriesList(infoBranch)
            }
        }

        doMigrations()
    }

    override fun getStoreManager(): StoreManager = stores

    override fun getTransactionManager(): ITransactionManager {
        return stores.getTransactionManager()
    }

    fun generateClientId(repositoryId: RepositoryId): Long {
        return stores.getGlobalStoreClient().generateId("$KEY_PREFIX:${repositoryId.id}:clientId")
    }

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
    override fun maybeInitAndGetSeverId(): String {
        val store = stores.getGlobalStoreClient()
        var serverId = store[SERVER_ID_KEY]
        if (serverId == null) {
            serverId = store[LEGACY_SERVER_ID_KEY2]
                ?: store[LEGACY_SERVER_ID_KEY]
                ?: UUID.randomUUID().toString().replace("[^a-zA-Z0-9]".toRegex(), "")
            store.put(SERVER_ID_KEY, serverId)
            store.put(LEGACY_SERVER_ID_KEY, serverId)
            store.put(LEGACY_SERVER_ID_KEY2, serverId)
        }
        return serverId
    }

    @RequiresTransaction
    override fun getRepositories(): Set<RepositoryId> {
        return getRepositories(false) + getRepositories(true)
    }

    @RequiresTransaction
    fun getRepositories(isolated: Boolean): Set<RepositoryId> {
        val repositoriesList = stores.genericStore[ObjectInRepository.global(repositoriesListKey(isolated))]
        val emptyRepositoriesList = repositoriesList.isNullOrBlank()
        return if (emptyRepositoriesList) {
            emptySet()
        } else {
            repositoriesList.lines().map { RepositoryId(it) }.toSet()
        }
    }

    /**
     * The data of a repository is stored separately from other repositories, but that wasn't always the case.
     * For backwards compatibility, existing repositories remain in the global storage.
     */
    override fun isIsolated(repository: RepositoryId): Boolean? {
        // The repository might not exist, but new repositories will be created with isolated storage.
        // If a repository is not part of the legacy ones it's considered isolated.
        @OptIn(RequiresTransaction::class)
        return stores.getTransactionManager().runRead {
            if (getRepositories(true).contains(repository)) return@runRead true
            if (getRepositories(false).contains(repository)) return@runRead false
            return@runRead null
        }
    }

    @OptIn(RequiresTransaction::class)
    private fun repositoryExists(repositoryId: RepositoryId) = getRepositories().contains(repositoryId)

    @RequiresTransaction
    override fun createRepository(
        repositoryId: RepositoryId,
        userName: String?,
        useRoleIds: Boolean,
        legacyGlobalStorage: Boolean,
    ): CLVersion {
        getTransactionManager().assertWrite()
        val isolated = !legacyGlobalStorage
        val globalStore = stores.getGlobalStoreClient()
        val masterBranch = repositoryId.getBranchReference()
        if (repositoryExists(repositoryId)) throw RepositoryAlreadyExistsException(repositoryId.id)
        val existingRepositories = getRepositories(isolated)
        globalStore.put(
            repositoriesListKey(isolated),
            (existingRepositories + repositoryId).joinToString("\n") { it.id },
            false,
        )
        stores.genericStore.put(branchListKey(repositoryId, isolated), masterBranch.branchName, false)
        val initialVersion = CLVersion.createRegularVersion(
            id = stores.idGenerator.generate(),
            time = Clock.System.now().epochSeconds.toString(),
            author = userName,
            tree = CLTree.builder(stores.getAsyncStore(repositoryId.takeIf { isolated }))
                .useRoleIds(useRoleIds).build(),
            baseVersion = null,
            operations = emptyArray(),
        )
        putVersionHash(masterBranch, initialVersion.getContentHash())
        return initialVersion
    }

    @RequiresTransaction
    fun getBranchNames(repositoryId: RepositoryId): Set<String> {
        return stores.genericStore[branchListKey(repositoryId)]?.ifEmpty { null }?.lines()?.toSet().orEmpty()
    }

    @RequiresTransaction
    override fun getBranches(repositoryId: RepositoryId): Set<BranchReference> {
        return getBranchNames(repositoryId)
            .map { repositoryId.getBranchReference(it) }
            .sortedBy { it.branchName }
            .toSet()
    }

    @RequiresTransaction
    private fun ensureRepositoriesAreInList(repositoryIds: Set<RepositoryId>) {
        if (repositoryIds.isEmpty()) return
        val missingRepositories = repositoryIds - getRepositories()
        if (missingRepositories.isNotEmpty()) {
            // Isolated repositories are created and added to the list in `createRepository`.
            // Unknown repositories are assumed to be legacy ones without isolated storage.
            stores.getGlobalStoreClient().put(repositoriesListKey(false), (getRepositories(false) + missingRepositories).joinToString("\n") { it.id })
        }
    }

    @RequiresTransaction
    private fun ensureBranchesAreInList(repository: RepositoryId, branchNames: Set<String>) {
        if (branchNames.isEmpty()) return
        val key = branchListKey(repository)
        val existingBranches = stores.genericStore[key]?.lines()?.toSet().orEmpty()
        val missingBranches = branchNames - existingBranches
        if (missingBranches.isNotEmpty()) {
            stores.genericStore.put(key, (existingBranches + missingBranches).joinToString("\n"))
        }
    }

    @RequiresTransaction
    private fun ensureBranchInList(branch: BranchReference) {
        ensureRepositoriesAreInList(setOf(branch.repositoryId))
        ensureBranchesAreInList(branch.repositoryId, setOf(branch.branchName))
    }

    @RequiresTransaction
    override fun removeRepository(repository: RepositoryId): Boolean {
        val genericStore = stores.genericStore

        if (!repositoryExists(repository)) {
            return false
        }

        for (branchName in getBranchNames(repository)) {
            putVersionHash(repository.getBranchReference(branchName), null)
        }
        genericStore.put(branchListKey(repository), null)
        val isolated = checkNotNull(isIsolated(repository)) { "Repository not found: $repository" }
        val existingRepositories = getRepositories(isolated)
        val remainingRepositories = existingRepositories - repository
        stores.getGlobalStoreClient().put(repositoriesListKey(isolated), remainingRepositories.joinToString("\n") { it.id })
        genericStore.removeRepositoryObjects(repository)
        return true
    }

    @RequiresTransaction
    override fun removeBranches(repository: RepositoryId, branchNames: Set<String>) {
        if (branchNames.isEmpty()) return
        val key = branchListKey(repository)
        val existingBranches = stores.genericStore[key]?.lines()?.toSet().orEmpty()
        val remainingBranches = existingBranches - branchNames
        stores.genericStore.put(key, remainingBranches.joinToString("\n"))
        for (branchName in branchNames) {
            putVersionHash(repository.getBranchReference(branchName), null)
        }
    }

    @RequiresTransaction
    override fun mergeChanges(branch: BranchReference, newVersionHash: String): String {
        val headHash = getVersionHash(branch)
        if (headHash == newVersionHash) return headHash
        val mergedHash = validateAndMerge(newVersionHash, headHash, branch.repositoryId)
        ensureBranchInList(branch)
        putVersionHash(branch, mergedHash)
        return mergedHash
    }

    override suspend fun mergeChangesWithoutPush(branch: BranchReference, newVersionHash: String): String {
        @OptIn(RequiresTransaction::class)
        val headHash = getTransactionManager().runReadIO {
            getVersionHash(branch)
        }
        return validateAndMerge(newVersionHash, headHash, branch.repositoryId)
    }

    private fun validateAndMerge(
        newVersionHash: String,
        headHash: String?,
        repositoryId: RepositoryId,
    ): String {
        if (headHash == newVersionHash) return headHash
        val asyncStore = getAsyncStore(repositoryId)
        val newVersion = CLVersion.loadFromHash(newVersionHash, asyncStore)
        val mergedHash = if (headHash == null) {
            validateVersion(newVersion, null)
            newVersionHash
        } else {
            val legacyObjectStore = getLegacyObjectStore(repositoryId)
            val headVersion = CLVersion.loadFromHash(headHash, legacyObjectStore)
            require(headVersion.getTree().getId() == newVersion.getTree().getId()) {
                "Attempt to merge a model with ID '${newVersion.getTree().getId()}'" +
                    " into one with ID '${headVersion.getTree().getId()}'"
            }
            validateVersion(newVersion, headVersion)
            val mergedVersion = VersionMerger(stores.idGenerator)
                .mergeChange(headVersion, newVersion)
            mergedVersion.getContentHash()
        }
        return mergedHash
    }

    private fun validateVersion(newVersion: CLVersion, oldVersion: CLVersion?) {
        // ensure there are no missing objects
        newVersion.graph.getStreamExecutor().iterate({ newVersion.fullDiff(oldVersion) }) { }

        // TODO check invariants of the model (consistent parent-child relations, single root, containment cycles)
    }

    @RequiresTransaction
    override fun getVersion(branch: BranchReference): CLVersion? {
        return getVersionHash(branch)?.let { getVersion(branch.repositoryId, it) }
    }

    override fun getVersion(repository: RepositoryId, versionHash: String): CLVersion? {
        val store = getAsyncStore(repository.takeIf { isIsolated(repository) == true })
        return store.getStreamExecutor().query { CLVersion.tryLoadFromHash(versionHash, store).orNull() }
    }

    @RequiresTransaction
    override fun getVersionHash(branch: BranchReference): String? {
        val isolated = isIsolated(branch.repositoryId)
        if (isolated == null) {
            // migrate existing but unknown legacy branch
            val legacyHash = stores.genericStore[legacyBranchKey(branch)] ?: return null
            ensureBranchInList(branch)
            putVersionHash(branch, legacyHash)
            return legacyHash
        } else {
            return stores.genericStore[branchKey(branch, isolated = isolated)]
        }
    }

    @RequiresTransaction
    private fun putVersionHash(branch: BranchReference, hash: String?) {
        val isolated = isIsolated(branch.repositoryId) ?: false
        stores.genericStore.put(branchKey(branch, isolated), hash, false)
        if (!isolated) {
            stores.genericStore.put(legacyBranchKey(branch), hash, false)
        }
    }

    override suspend fun pollVersionHash(branch: BranchReference, lastKnown: String?): String {
        return pollEntry(stores.genericStore, branchKey(branch), lastKnown)
            ?: throw IllegalStateException("No version found for branch '${branch.branchName}' in repository '${branch.repositoryId}'")
    }

    override suspend fun computeDelta(repository: RepositoryId?, versionHash: String, filter: ObjectDeltaFilter): ObjectData {
        if (filter.knownVersions.contains(versionHash)) return ObjectData.empty

        val store = stores.getAsyncStore(repository?.takeIf { isIsolated(it) ?: false })
        return store.getStreamExecutor().queryManyLater {
            val version = CLVersion.loadFromHash(versionHash, store)

            if (filter.knownVersions.isNotEmpty()) {
                val commonBase = IStream.many(filter.knownVersions).flatMap {
                    CLVersion.tryLoadFromHash(it, store)
                }.fold<CLVersion?>(version) { acc, it -> acc?.commonBaseVersion(it) }
                commonBase.flatMap { commonBase -> version.diff(filter, commonBase) }
            } else {
                version.diff(filter, null)
            }.map { it.getHashString() to it.data.serialize() }
        }.let { ObjectDataFlow(it) }
    }

    private fun branchKey(branch: BranchReference, isolated: Boolean = isIsolated(branch.repositoryId) ?: true): ObjectInRepository {
        return if (isolated) {
            ObjectInRepository(branch.repositoryId.id, "$KEY_PREFIX:branches:${SerializationUtil.escape(branch.branchName)}")
        } else {
            ObjectInRepository.global("$KEY_PREFIX:repositories:${branch.repositoryId.id}:branches:${branch.branchName}")
        }
    }

    private fun legacyBranchKey(branchReference: BranchReference): ObjectInRepository {
        check(isIsolated(branchReference.repositoryId) != true) { "Not a legacy repository: " + branchReference.repositoryId }
        return ObjectInRepository.global(branchReference.getKey())
    }

    private fun branchListKey(repositoryId: RepositoryId, isolated: Boolean = isIsolated(repositoryId) ?: true): ObjectInRepository {
        return if (isolated) {
            ObjectInRepository(repositoryId.id, "$KEY_PREFIX:branches")
        } else {
            return ObjectInRepository.global("$KEY_PREFIX:repositories:${repositoryId.id}:branches")
        }
    }

    private fun listLegacyRepositories(infoBranch: IBranch): Set<BranchReference> {
        val result: MutableSet<BranchReference> = HashSet()
        infoBranch.runReadT { t: IReadTransaction ->
            for (infoNodeId in t.getChildren(ITree.ROOT_ID, "info")) {
                for (repositoryNodeId in t.getChildren(infoNodeId, "repositories")) {
                    val repositoryId = t.getProperty(repositoryNodeId, "id")?.let { RepositoryId(it) } ?: continue
                    result.add(repositoryId.getBranchReference())
                    for (branchNodeId in t.getChildren(repositoryNodeId, "branches")) {
                        val branchName = t.getProperty(branchNodeId, "name") ?: continue
                        result.add(repositoryId.getBranchReference(branchName))
                    }
                }
            }
        }
        return result
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RepositoriesManager::class.java)
        const val KEY_PREFIX = ":v2"
        private const val SHARED_REPOSITORIES_LIST_KEY = "$KEY_PREFIX:repositories"
        private const val ISOLATED_REPOSITORIES_LIST_KEY = "$KEY_PREFIX:isolated-repositories"
        const val LEGACY_SERVER_ID_KEY = "repositoryId"
        const val LEGACY_SERVER_ID_KEY2 = "server-id"
        const val SERVER_ID_KEY = "$KEY_PREFIX:server-id"

        fun repositoriesListKey(isolated: Boolean): String {
            return if (isolated) ISOLATED_REPOSITORIES_LIST_KEY else SHARED_REPOSITORIES_LIST_KEY
        }
    }
}

class RepositoryAlreadyExistsException(val name: String) : IllegalStateException("Repository '$name' already exists")

sealed interface ObjectData {
    suspend fun asMap(): Map<String, String>
    fun asStream(): IExecutableStream.Many<Pair<String, String>>

    companion object {
        val empty = ObjectDataMap(emptyMap())
    }
}

class ObjectDataMap(private val byHashObjects: Map<String, String>) : ObjectData {
    init {
        HashUtil.checkObjectHashes(byHashObjects)
    }

    override suspend fun asMap(): Map<String, String> = byHashObjects
    override fun asStream(): IExecutableStream.Many<Pair<String, String>> {
        return IExecutableStream.many(byHashObjects.entries).mapMany { it.map { it.key to it.value } }
    }
}

class ObjectDataFlow(private val hashObjectFlow: IExecutableStream.Many<Pair<String, String>>) : ObjectData {
    override suspend fun asMap(): Map<String, String> = hashObjectFlow.mapOne { it.toMap({ it.first }, { it.second }) }.querySuspending()
    override fun asStream(): IExecutableStream.Many<Pair<String, String>> = hashObjectFlow
}

private fun Flow<Pair<String, String>>.checkObjectHashes(): Flow<Pair<String, String>> {
    return onEach { HashUtil.checkObjectHash(it.first, it.second) }
}

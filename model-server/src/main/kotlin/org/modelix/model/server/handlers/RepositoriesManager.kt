package org.modelix.model.server.handlers

import com.badoo.reaktive.coroutinesinterop.asFlow
import com.badoo.reaktive.observable.map
import gnu.trove.set.hash.THashSet
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import org.apache.commons.collections4.map.LRUMap
import org.modelix.model.ModelMigrations
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.PBranch
import org.modelix.model.async.getRecursively
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.computeDelta
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.HashUtil
import org.modelix.model.persistent.SerializationUtil
import org.modelix.model.server.api.v2.toMap
import org.modelix.model.server.store.IRepositoryAwareStore
import org.modelix.model.server.store.ITransactionManager
import org.modelix.model.server.store.ObjectInRepository
import org.modelix.model.server.store.StoreManager
import org.modelix.model.server.store.assertWrite
import org.modelix.model.server.store.pollEntry
import org.modelix.streams.endOfSynchronousPipeline
import org.slf4j.LoggerFactory
import java.lang.ref.SoftReference
import java.util.UUID

// The methods in this class are almost cohesive, so the number of functions is fine.
@Suppress("complexity.TooManyFunctions")
class RepositoriesManager(val stores: StoreManager) : IRepositoriesManager {
    constructor(store: IRepositoryAwareStore) : this(StoreManager(store))

    init {
        fun migrateLegacyRepositoriesList(infoBranch: IBranch) {
            val legacyRepositories = listLegacyRepositories(infoBranch).groupBy { it.repositoryId }
            if (legacyRepositories.isNotEmpty()) {
                // To not use `runTransactionSuspendable` like everywhere else,
                // because this is blocking initialization code anyways.
                ensureRepositoriesAreInList(legacyRepositories.keys)
                for ((legacyRepository, legacyBranches) in legacyRepositories) {
                    ensureBranchesAreInList(legacyRepository, legacyBranches.map { it.branchName }.toSet())
                }
            }
        }

        fun doMigrations() {
            stores.getTransactionManager().runWrite {
                val repositoryId = RepositoryId("info")
                val v1BranchKey = repositoryId.getBranchReference().getKey()
                val infoVersionHash = stores.getGlobalStoreClient().get(v1BranchKey) ?: return@runWrite
                val infoVersion = CLVersion(infoVersionHash, getLegacyObjectStore(repositoryId))
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

    override fun getRepositories(): Set<RepositoryId> {
        return getRepositories(false) + getRepositories(true)
    }

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
        return stores.getTransactionManager().runRead {
            if (getRepositories(true).contains(repository)) return@runRead true
            if (getRepositories(false).contains(repository)) return@runRead false
            return@runRead null
        }
    }

    private fun repositoryExists(repositoryId: RepositoryId) = getRepositories().contains(repositoryId)

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
            tree = CLTree(null, null, stores.getLegacyObjectStore(repositoryId.takeIf { isolated }), useRoleIds = useRoleIds),
            baseVersion = null,
            operations = emptyArray(),
        )
        putVersionHash(masterBranch, initialVersion.getContentHash())
        return initialVersion
    }

    fun getBranchNames(repositoryId: RepositoryId): Set<String> {
        return stores.genericStore[branchListKey(repositoryId)]?.ifEmpty { null }?.lines()?.toSet().orEmpty()
    }

    override fun getBranches(repositoryId: RepositoryId): Set<BranchReference> {
        return getBranchNames(repositoryId)
            .map { repositoryId.getBranchReference(it) }
            .sortedBy { it.branchName }
            .toSet()
    }

    /**
     * Must be executed inside a transaction
     */
    private fun ensureRepositoriesAreInList(repositoryIds: Set<RepositoryId>) {
        if (repositoryIds.isEmpty()) return
        val missingRepositories = repositoryIds - getRepositories()
        if (missingRepositories.isNotEmpty()) {
            // Isolated repositories are created and added to the list in `createRepository`.
            // Unknown repositories are assumed to be legacy ones without isolated storage.
            stores.getGlobalStoreClient().put(repositoriesListKey(false), (getRepositories(false) + missingRepositories).joinToString("\n") { it.id })
        }
    }

    /**
     * Must be executed inside a transaction
     */
    private fun ensureBranchesAreInList(repository: RepositoryId, branchNames: Set<String>) {
        if (branchNames.isEmpty()) return
        val key = branchListKey(repository)
        val existingBranches = stores.genericStore[key]?.lines()?.toSet().orEmpty()
        val missingBranches = branchNames - existingBranches
        if (missingBranches.isNotEmpty()) {
            stores.genericStore.put(key, (existingBranches + missingBranches).joinToString("\n"))
        }
    }

    /**
     * Must be executed inside a transaction
     */
    private fun ensureBranchInList(branch: BranchReference) {
        ensureRepositoriesAreInList(setOf(branch.repositoryId))
        ensureBranchesAreInList(branch.repositoryId, setOf(branch.branchName))
    }

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

    /**
     * Same as [removeBranches] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
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

    override fun mergeChanges(branch: BranchReference, newVersionHash: String): String {
        return mergeChangesBlocking(branch, newVersionHash)
    }

    /**
     * Same as [mergeChanges] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
    override fun mergeChangesBlocking(branch: BranchReference, newVersionHash: String): String {
        val headHash = getVersionHash(branch)
        val mergedHash = if (headHash == null) {
            newVersionHash
        } else {
            val legacyObjectStore = getLegacyObjectStore(branch.repositoryId)
            val headVersion = CLVersion(headHash, legacyObjectStore)
            val newVersion = CLVersion(newVersionHash, legacyObjectStore)
            require(headVersion.getTree().getId() == newVersion.getTree().getId()) {
                "Attempt to merge a model with ID '${newVersion.getTree().getId()}'" +
                    " into one with ID '${headVersion.getTree().getId()}'"
            }
            val mergedVersion = VersionMerger(legacyObjectStore, stores.idGenerator)
                .mergeChange(headVersion, newVersion)
            mergedVersion.getContentHash()
        }
        ensureBranchInList(branch)
        putVersionHash(branch, mergedHash)
        return mergedHash
    }

    override fun getVersion(branch: BranchReference): CLVersion? {
        return getVersionHash(branch)?.let { getVersion(branch.repositoryId, it) }
    }

    override fun getVersion(repository: RepositoryId, versionHash: String): CLVersion? {
        val legacyObjectStore = getLegacyObjectStore(repository.takeIf { isIsolated(repository) == true })
        return CLVersion.tryLoadFromHash(versionHash, legacyObjectStore)
    }

    /**
     * Same as [getVersionHash] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
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

    private val versionDeltaCache = VersionDeltaCache(stores)
    override suspend fun computeDelta(repository: RepositoryId?, versionHash: String, baseVersionHash: String?): ObjectData {
        if (versionHash == baseVersionHash) return ObjectData.empty
        if (baseVersionHash == null) {
            // no need to cache anything if there is no delta computation happening
            return allObjectDataAsFlow(repository, versionHash)
        }

        return versionDeltaCache.getOrComputeDelta(repository?.takeIf { isIsolated(it) ?: false }, versionHash, baseVersionHash)
    }

    private fun allObjectDataAsFlow(repository: RepositoryId?, versionHash: String): ObjectDataFlow {
        val asyncStore = getAsyncStore(repository)
        return asyncStore.getRecursively(KVEntryReference(versionHash, CPVersion.DESERIALIZER), THashSet())
            .map { it.first.getHash() to it.second.serialize() }
            .endOfSynchronousPipeline()
            .asFlow()
            .checkObjectHashes()
            .flowOn(Dispatchers.IO)
            .let { ObjectDataFlow(it) }
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
    fun asFlow(): Flow<Pair<String, String>>

    companion object {
        val empty = ObjectDataMap(emptyMap())
    }
}

class ObjectDataMap(private val byHashObjects: Map<String, String>) : ObjectData {
    init {
        HashUtil.checkObjectHashes(byHashObjects)
    }

    override suspend fun asMap(): Map<String, String> = byHashObjects
    override fun asFlow(): Flow<Pair<String, String>> = byHashObjects.entries.asFlow().map { it.toPair() }
}

class ObjectDataFlow(private val hashObjectFlow: Flow<Pair<String, String>>) : ObjectData {
    override suspend fun asMap(): Map<String, String> = hashObjectFlow.toMap()
    override fun asFlow(): Flow<Pair<String, String>> = hashObjectFlow
}

private fun Flow<Pair<String, String>>.checkObjectHashes(): Flow<Pair<String, String>> {
    return onEach { HashUtil.checkObjectHash(it.first, it.second) }
}

class VersionDeltaCache(val stores: StoreManager) {

    companion object {
        private val LOG = LoggerFactory.getLogger(VersionDeltaCache::class.java)
    }

    private val cacheMap = LRUMap<Triple<RepositoryId?, String, String?>, SoftReference<Deferred<ObjectDataMap>>>(10)

    suspend fun getOrComputeDelta(repository: RepositoryId?, versionHash: String, baseVersionHash: String): ObjectDataMap {
        return coroutineScope {
            val deferredDelta = synchronized(cacheMap) {
                val key = Triple(repository, versionHash, baseVersionHash)
                val existingDeferredDelta = cacheMap[key]?.get()
                if (existingDeferredDelta != null) {
                    LOG.debug("Version delta found in cache for {}.", key)
                    existingDeferredDelta
                } else {
                    LOG.debug("Version delta not found in cache for {}.", key)
                    val legacyObjectStore = stores.getLegacyObjectStore(repository)
                    val version = CLVersion(versionHash, legacyObjectStore)
                    val baseVersion = CLVersion(baseVersionHash, legacyObjectStore)
                    val newDeferredDelta = async(Dispatchers.IO) {
                        LOG.debug("Computing for delta for {}.", key)
                        val result = ObjectDataMap(version.computeDelta(baseVersion))
                        LOG.debug("Computed version delta for {}.", key)
                        result
                    }
                    cacheMap[key] = SoftReference(newDeferredDelta)
                    newDeferredDelta
                }
            }
            deferredDelta.await()
        }
    }
}

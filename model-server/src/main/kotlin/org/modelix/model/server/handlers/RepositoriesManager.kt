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
package org.modelix.model.server.handlers

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.apache.commons.collections4.map.LRUMap
import org.modelix.model.ModelMigrations
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.PBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.computeDelta
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.HashUtil
import org.modelix.model.persistent.SerializationUtil
import org.modelix.model.server.api.v2.toMap
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.ObjectInRepository
import org.modelix.model.server.store.forGlobalRepository
import org.modelix.model.server.store.forRepository
import org.modelix.model.server.store.getGenericStore
import org.modelix.model.server.store.pollEntry
import org.modelix.model.server.store.runTransactionSuspendable
import org.modelix.model.server.store.withGlobalRepository
import org.modelix.model.server.store.withRepository
import org.modelix.model.server.store.withRepositoryInCoroutine
import org.slf4j.LoggerFactory
import java.lang.ref.SoftReference
import java.util.UUID

// The methods in this class are almost cohesive, so the number of functions is fine.
@Suppress("complexity.TooManyFunctions")
class RepositoriesManager(val client: LocalModelClient) : IRepositoriesManager {

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
            store.withGlobalRepository {
                store.runTransaction {
                    val v1BranchKey = RepositoryId("info").getBranchReference().getKey()
                    val infoVersionHash = client[v1BranchKey] ?: return@runTransaction
                    val infoVersion = CLVersion(infoVersionHash, client.storeCache)
                    val infoBranch: IBranch = PBranch(infoVersion.getTree(), IdGeneratorDummy())

                    ModelMigrations.useResolvedConceptsFromMetaModel(infoBranch)
                    migrateLegacyRepositoriesList(infoBranch)
                }
            }
        }

        doMigrations()
    }

    private val store: IStoreClient get() = client.store
    private val objectStore: IDeserializingKeyValueStore get() = client.storeCache

    fun generateClientId(repositoryId: RepositoryId): Long {
        return client.store.generateId("$KEY_PREFIX:${repositoryId.id}:clientId")
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
    override suspend fun maybeInitAndGetSeverId(): String {
        val store = store.getGenericStore().forGlobalRepository()
        return store.runTransactionSuspendable {
            var serverId = store[SERVER_ID_KEY]
            if (serverId == null) {
                serverId = store[LEGACY_SERVER_ID_KEY2]
                    ?: store[LEGACY_SERVER_ID_KEY]
                    ?: UUID.randomUUID().toString().replace("[^a-zA-Z0-9]".toRegex(), "")
                store.put(SERVER_ID_KEY, serverId)
                store.put(LEGACY_SERVER_ID_KEY, serverId)
                store.put(LEGACY_SERVER_ID_KEY2, serverId)
            }
            serverId
        }
    }

    override fun getRepositories(): Set<RepositoryId> {
        return getRepositories(false) + getRepositories(true)
    }

    fun getRepositories(isolated: Boolean): Set<RepositoryId> {
        val repositoriesList = store.getGenericStore()[ObjectInRepository.global(repositoriesListKey(isolated))]
        val emptyRepositoriesList = repositoriesList.isNullOrBlank()
        return if (emptyRepositoriesList) {
            emptySet()
        } else {
            repositoriesList!!.lines().map { RepositoryId(it) }.toSet()
        }
    }

    /**
     * The data of a repository is stored separately from other repositories, but that wasn't always the case.
     * For backwards compatibility, existing repositories remain in the global storage.
     */
    override fun isIsolated(repository: RepositoryId): Boolean? {
        // The repository might not exist, but new repositories will be created with isolated storage.
        // If a repository is not part of the legacy ones it's considered isolated.
        if (getRepositories(true).contains(repository)) return true
        if (getRepositories(false).contains(repository)) return false
        return null
    }

    private fun repositoryExists(repositoryId: RepositoryId) = getRepositories().contains(repositoryId)

    override suspend fun createRepository(
        repositoryId: RepositoryId,
        userName: String?,
        useRoleIds: Boolean,
        legacyGlobalStorage: Boolean,
    ): CLVersion = store.runTransactionSuspendable {
        val isolated = !legacyGlobalStorage
        store.withRepository(repositoryId.takeIf { isolated }) {
            val masterBranch = repositoryId.getBranchReference()
            if (repositoryExists(repositoryId)) throw RepositoryAlreadyExistsException(repositoryId.id)
            val existingRepositories = getRepositories(isolated)
            store.put(repositoriesListKey(isolated), (existingRepositories + repositoryId).joinToString("\n") { it.id }, false)
            store.getGenericStore().put(branchListKey(repositoryId, isolated), masterBranch.branchName, false)
            val initialVersion = CLVersion.createRegularVersion(
                id = client.idGenerator.generate(),
                time = Clock.System.now().epochSeconds.toString(),
                author = userName,
                tree = CLTree(null, null, client.storeCache, useRoleIds = useRoleIds),
                baseVersion = null,
                operations = emptyArray(),
            )
            putVersionHash(masterBranch, initialVersion.getContentHash())
            initialVersion
        }
    }

    fun getBranchNames(repositoryId: RepositoryId): Set<String> {
        return store.getGenericStore()[branchListKey(repositoryId)]?.lines()?.toSet().orEmpty()
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
            store.put(repositoriesListKey(false), (getRepositories(false) + missingRepositories).joinToString("\n") { it.id })
        }
    }

    /**
     * Must be executed inside a transaction
     */
    private fun ensureBranchesAreInList(repository: RepositoryId, branchNames: Set<String>) {
        if (branchNames.isEmpty()) return
        val key = branchListKey(repository)
        val existingBranches = store.getGenericStore()[key]?.lines()?.toSet().orEmpty()
        val missingBranches = branchNames - existingBranches
        if (missingBranches.isNotEmpty()) {
            store.getGenericStore().put(key, (existingBranches + missingBranches).joinToString("\n"))
        }
    }

    /**
     * Must be executed inside a transaction
     */
    private fun ensureBranchInList(branch: BranchReference) {
        ensureRepositoriesAreInList(setOf(branch.repositoryId))
        ensureBranchesAreInList(branch.repositoryId, setOf(branch.branchName))
    }

    override suspend fun removeRepository(repository: RepositoryId): Boolean {
        return store.runTransactionSuspendable {
            if (!repositoryExists(repository)) {
                return@runTransactionSuspendable false
            }

            for (branchName in getBranchNames(repository)) {
                putVersionHash(repository.getBranchReference(branchName), null)
            }
            store.getGenericStore().put(branchListKey(repository), null)
            val isolated = checkNotNull(isIsolated(repository)) { "Repository not found: $repository" }
            val existingRepositories = getRepositories(isolated)
            val remainingRepositories = existingRepositories - repository
            store.put(repositoriesListKey(isolated), remainingRepositories.joinToString("\n") { it.id })

            true
        }
    }

    override suspend fun removeBranches(repository: RepositoryId, branchNames: Set<String>) {
        return store.runTransactionSuspendable {
            removeBranchesBlocking(repository, branchNames)
        }
    }

    /**
     * Same as [removeBranches] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
    override fun removeBranchesBlocking(repository: RepositoryId, branchNames: Set<String>) {
        if (branchNames.isEmpty()) return
        store.runTransaction {
            val key = branchListKey(repository)
            val existingBranches = store.getGenericStore()[key]?.lines()?.toSet().orEmpty()
            val remainingBranches = existingBranches - branchNames
            store.getGenericStore().put(key, remainingBranches.joinToString("\n"))
            for (branchName in branchNames) {
                putVersionHash(repository.getBranchReference(branchName), null)
            }
        }
    }

    override suspend fun mergeChanges(branch: BranchReference, newVersionHash: String): String {
        return store.runTransactionSuspendable {
            mergeChangesBlocking(branch, newVersionHash)
        }
    }

    /**
     * Same as [mergeChanges] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
    override fun mergeChangesBlocking(branch: BranchReference, newVersionHash: String): String =
        runWithRepositoryBlocking(branch.repositoryId) {
            store.runTransaction {
                val headHash = getVersionHashBlocking(branch)
                val mergedHash = if (headHash == null) {
                    newVersionHash
                } else {
                    val headVersion = CLVersion(headHash, client.storeCache)
                    val newVersion = CLVersion(newVersionHash, client.storeCache)
                    require(headVersion.getTree().getId() == newVersion.getTree().getId()) {
                        "Attempt to merge a model with ID '${newVersion.getTree().getId()}'" +
                            " into one with ID '${headVersion.getTree().getId()}'"
                    }
                    val mergedVersion = VersionMerger(client.storeCache, client.idGenerator)
                        .mergeChange(headVersion, newVersion)
                    mergedVersion.getContentHash()
                }
                ensureBranchInList(branch)
                putVersionHash(branch, mergedHash)
                mergedHash
            }
        }

    override suspend fun getVersion(branch: BranchReference): CLVersion? {
        return getVersionHash(branch)?.let { getVersion(branch.repositoryId, it) }
    }

    override suspend fun getVersion(repository: RepositoryId, versionHash: String): CLVersion? {
        val storeForVersion = if (isIsolated(repository) == true) client.store.forRepository(repository) else client.store.getGenericStore().forGlobalRepository()
        return CLVersion.tryLoadFromHash(versionHash, ObjectStoreCache(LocalModelClient(storeForVersion)))
    }

    override suspend fun getVersionHash(branch: BranchReference): String? {
        return store.runTransactionSuspendable {
            getVersionHashBlocking(branch)
        }
    }

    /**
     * Same as [getVersionHash] but blocking.
     * Caller is expected to execute it outside the request thread.
     */
    private fun getVersionHashBlocking(branch: BranchReference): String? {
        return store.runTransaction {
            val isolated = isIsolated(branch.repositoryId)
            if (isolated == null) {
                // migrate existing but unknown legacy branch
                val legacyHash = store.getGenericStore()[legacyBranchKey(branch)] ?: return@runTransaction null
                ensureBranchInList(branch)
                putVersionHash(branch, legacyHash)
                return@runTransaction legacyHash
            } else {
                return@runTransaction store.getGenericStore()[branchKey(branch, isolated = isolated)]
            }
        }
    }

    private fun putVersionHash(branch: BranchReference, hash: String?) {
        val isolated = isIsolated(branch.repositoryId) ?: false
        store.getGenericStore().put(branchKey(branch, isolated), hash, false)
        if (!isolated) {
            store.getGenericStore().put(legacyBranchKey(branch), hash, false)
        }
    }

    override suspend fun pollVersionHash(branch: BranchReference, lastKnown: String?): String {
        return pollEntry(client.store.getGenericStore(), branchKey(branch), lastKnown)
            ?: throw IllegalStateException("No version found for branch '${branch.branchName}' in repository '${branch.repositoryId}'")
    }

    private val versionDeltaCache = VersionDeltaCache(client.storeCache)
    override suspend fun computeDelta(versionHash: String, baseVersionHash: String?): ObjectData {
        if (versionHash == baseVersionHash) return ObjectData.empty
        if (baseVersionHash == null) {
            // no need to cache anything if there is no delta computation happening
            return allObjectDataAsFlow(versionHash)
        }

        return versionDeltaCache.getOrComputeDelta(versionHash, baseVersionHash)
    }

    private fun allObjectDataAsFlow(versionHash: String): ObjectDataFlow {
        val hashObjectFlow = channelFlow {
            // Our bulk query is blocking, therefor we explicitly launch it on one of the Dispatchers.IO.
            // Without it, the consumer could accidentally start the flow on this thread and block it.
            launch(Dispatchers.IO) {
                // Use a bulk query to make as few request to the underlying store as possible.
                val bulkQuery = objectStore.newBulkQuery()
                // It is unsatisfactory that we have to keep already emitted hashes in memory.
                // But without changing the underlying model,
                // we have to do this to not emit objects more than once.
                val seenHashes = mutableSetOf<String>()
                fun emitObjects(entry: KVEntryReference<*>) {
                    if (seenHashes.contains(entry.getHash())) return
                    seenHashes.add(entry.getHash())
                    bulkQuery.query(entry).onReceive {
                        val value = checkNotNull(it) { "No value received for ${entry.getHash()}" }
                        // Use `send` instead of `trySend`,
                        // because `trySend` fails if the channel capacity is full.
                        // This might happen if the data is produced faster than consumed.
                        // A better solution would be to have bulk queries which itself are asynchronous
                        // but doing that needs more consideration.
                        runBlocking {
                            // Maybe we should avoid Flow<Pair<String, String>> and use Flow<String>.
                            // This needs profiling
                            channel.send(entry.getHash() to value.serialize())
                        }
                        for (referencedEntry in value.getReferencedEntries()) {
                            emitObjects(referencedEntry)
                        }
                    }
                }
                emitObjects(KVEntryReference(versionHash, CPVersion.DESERIALIZER))
                LOG.debug("Starting to bulk query all objects.")
                bulkQuery.executeQuery()
            }
        }
        val checkedHashObjectFlow = hashObjectFlow.checkObjectHashes()
        val objectData = ObjectDataFlow(checkedHashObjectFlow)
        return objectData
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

    override suspend fun <R> runWithRepository(repository: RepositoryId, body: suspend () -> R): R {
        return store.withRepositoryInCoroutine(repository.takeIf { isIsolated(repository) == true }, body)
    }

    private fun <R> runWithRepositoryBlocking(repository: RepositoryId, body: () -> R): R {
        return store.withRepository(repository.takeIf { isIsolated(repository) == true }, body)
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

class VersionDeltaCache(val store: IDeserializingKeyValueStore) {

    companion object {
        private val LOG = LoggerFactory.getLogger(VersionDeltaCache::class.java)
    }

    private val cacheMap = LRUMap<Pair<String, String?>, SoftReference<Deferred<ObjectDataMap>>>(10)

    suspend fun getOrComputeDelta(versionHash: String, baseVersionHash: String): ObjectDataMap {
        return coroutineScope {
            val deferredDelta = synchronized(cacheMap) {
                val key = versionHash to baseVersionHash
                val existingDeferredDelta = cacheMap[key]?.get()
                if (existingDeferredDelta != null) {
                    LOG.debug("Version delta found in cache for {}.", key)
                    existingDeferredDelta
                } else {
                    LOG.debug("Version delta not found in cache for {}.", key)
                    val version = CLVersion(versionHash, store)
                    val baseVersion = CLVersion(baseVersionHash, store)
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

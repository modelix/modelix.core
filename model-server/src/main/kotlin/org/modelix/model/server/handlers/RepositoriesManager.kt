package org.modelix.model.server.handlers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import org.modelix.datastructures.history.HistoryIndexNode
import org.modelix.datastructures.history.merge
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.getHash
import org.modelix.datastructures.model.withIdTranslation
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.datastructures.objects.asObject
import org.modelix.model.IVersion
import org.modelix.model.ModelMigrations
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.TreeType
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.ITree
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PBranch
import org.modelix.model.api.getOriginalOrCurrentReference
import org.modelix.model.async.LazyLoadingObjectGraph
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.diff
import org.modelix.model.mutable.asModel
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.mutable.asMutableSingleThreaded
import org.modelix.model.persistent.HashUtil
import org.modelix.model.persistent.SerializationUtil
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.model.server.store.GlobalStorageMigration
import org.modelix.model.server.store.IRepositoryAwareStore
import org.modelix.model.server.store.ITransactionManager
import org.modelix.model.server.store.ObjectInRepository
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.StoreManager
import org.modelix.model.server.store.assertWrite
import org.modelix.model.server.store.pollEntry
import org.modelix.model.server.store.runReadIO
import org.modelix.model.sync.bulk.INodeAssociation
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.streams.IExecutableStream
import org.modelix.streams.IStream
import org.modelix.streams.getBlocking
import org.modelix.streams.iterateBlocking
import org.modelix.streams.plus
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
        config: RepositoryConfig,
        userName: String?,
    ): CLVersion {
        getTransactionManager().assertWrite()
        val isolated = !config.legacyGlobalStorage
        val globalStore = stores.getGlobalStoreClient()
        val repositoryId = RepositoryId(config.repositoryId)
        val masterBranch = repositoryId.getBranchReference()
        if (repositoryExists(repositoryId)) throw RepositoryAlreadyExistsException(repositoryId.id)
        val existingRepositories = getRepositories(isolated)
        globalStore.put(
            repositoriesListKey(isolated),
            (existingRepositories + repositoryId).joinToString("\n") { it.id },
            false,
        )
        stores.genericStore.put(branchListKey(repositoryId, isolated), masterBranch.branchName, false)

        val tree = config.createEmptyTree()

        val initialVersion = CLVersion.builder()
            .time(Clock.System.now())
            .author(userName)
            .tree(tree)
            .buildLegacy()
        initialVersion.write()
        validateVersion(initialVersion, null)
        putVersionHash(masterBranch, initialVersion.getContentHash())
        return initialVersion
    }

    fun RepositoryConfig.createEmptyTree(): IGenericModelTree<INodeReference> {
        return IGenericModelTree.builder()
            .treeId(modelId)
            .storeRoleNames(legacyNameBasedRoles)
            .graph(LazyLoadingObjectGraph(getAsyncStore(RepositoryId(repositoryId))))
            .let {
                when (nodeIdType) {
                    RepositoryConfig.NodeIdType.INT64 -> it.withInt64Ids().build().withIdTranslation()
                    RepositoryConfig.NodeIdType.STRING -> it.withNodeReferenceIds().build()
                }
            }
    }

    @RequiresTransaction
    override fun forkRepository(
        source: RepositoryId,
        target: RepositoryId,
    ) {
        getTransactionManager().assertWrite()
        require(isIsolated(source) == true) {
            "Repository '$source' uses the legacy global storage"
        }
        if (repositoryExists(target)) throw RepositoryAlreadyExistsException(target.id)

        stores.genericStore.copyRepositoryObjects(source, target)

        val existingRepositories = getRepositories(true)
        val globalStore = stores.getGlobalStoreClient()
        globalStore.put(
            repositoriesListKey(true),
            (existingRepositories + target).joinToString("\n") { it.id },
            false,
        )
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
    override fun forcePush(branch: BranchReference, newVersionHash: String) {
        val headHash = getVersionHash(branch)
        if (headHash == newVersionHash) return
        val asyncStore = getAsyncStore(branch.repositoryId)
        val newVersion = CLVersion.loadFromHash(newVersionHash, asyncStore)
        val headVersion = headHash?.let { CLVersion.loadFromHash(it, asyncStore) }
        validateVersion(newVersion, headVersion)
        ensureBranchInList(branch)
        putVersionHash(branch, newVersionHash)
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
            require(headVersion.getModelTree().getId() == newVersion.getModelTree().getId()) {
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
        if (System.getenv("MODELIX_VALIDATE_VERSIONS") != "true") return

        if (newVersion.getObjectHash() == oldVersion?.getObjectHash()) return

        // Deleting the root node isn't allowed
        val mainTree = newVersion.getModelTree()
        check(mainTree.containsNode(mainTree.getRootNodeId()).getBlocking(mainTree))

        // ensure there are no missing objects
        // newVersion.graph.getStreamExecutor().iterate({ newVersion.diff(oldVersion) }) { }
        if (oldVersion != null) {
            // If the object diff is buggy, client and server will skip over the same objects.
            // The model diff should also iterate over all new objects and is used for additional validation.
            newVersion.getModelTree().getChanges(oldVersion.getModelTree(), false).iterateBlocking(newVersion.getModelTree()) { }
        }

        // TODO check invariants of the model (consistent parent-child relations, single root, containment cycles)

        // check that the operations actually reproduce the model
        val baseVersion = newVersion.baseVersion
        if (baseVersion == null) {
            check(newVersion.numberOfOperations == 0)
            check(newVersion.operations.count() == 0)
        } else {
            val mutableTree = baseVersion.getModelTree().asMutableSingleThreaded()
            newVersion.operationsAsStream().iterateBlocking(mainTree) { op ->
                op.apply(mutableTree)
            }
            check(mutableTree.getTransaction().tree.getHash() == newVersion.getModelTree().getHash()) {
                "Recorded operations don't produce the provided result"
            }
        }
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
        if (hash != null) {
            // eager update of the index
            getVersion(branch.repositoryId, hash)?.let { getOrCreateHistoryIndex(branch.repositoryId, it) }
        }
    }

    @RequiresTransaction
    fun getOrCreateHistoryIndex(repositoryId: RepositoryId, version: CLVersion): Object<HistoryIndexNode> {
        val key = versionHistoryKey(repositoryId, version.getObjectHash())
        val indexHash = stores.genericStore.get(key)?.let { ObjectHash(it) }
        val graph = stores.getAsyncStore(repositoryId.takeIf { isIsolated(it) ?: false }).asObjectGraph()
        if (indexHash != null) {
            return graph.fromHash(indexHash, HistoryIndexNode).resolveNow()
        } else {
            val parentIndices = version.getParentVersions().map { getOrCreateHistoryIndex(repositoryId, it as CLVersion) }
            val newElement = HistoryIndexNode.of(version.obj).asObject(graph)
            val newIndex = when (parentIndices.size) {
                0 -> newElement
                1 -> parentIndices.single().merge(newElement).getBlocking(graph)
                2 -> parentIndices[0].merge(parentIndices[1]).merge(newElement).getBlocking(graph)
                else -> error("impossible")
            }
            newIndex.write()
            stores.genericStore.put(key, newIndex.getHashString())
            return newIndex
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

            val diff = IStream.many(filter.knownVersions).flatMap { CLVersion.tryLoadFromHash(it, store) }.toList()
                .flatMap { knownVersions ->
                    version.diff(knownVersions, filter)
                }
            val versionHash = version.getObjectHash()
            // The version itself is always included, because version objects on the client are weakly referenced,
            // and it may already be garbage collected.
            (IStream.of(version.asObject()) + diff.filter { it.getHash() != versionHash })
                .map { it.getHashString() to it.data.serialize() }
        }.let { ObjectDataFlow(it) }
    }

    /**
     * Possible migrations:
     * - name<-->id based roles: not supported (requires metamodel to map role names to role IDs)
     * - global<-->isolated storage: works (copies all reachable objects and updates repository lists)
     * - int64-->string node IDs: works (references preserved as strings)
     * - string-->int64 node IDs: works only if string IDs are valid hex-encoded int64 values
     * - changed model ID: not supported (NodeAssociationForIdMigration cannot resolve nodes across different model IDs)
     */
    @RequiresTransaction
    override fun migrateRepository(
        newConfig: RepositoryConfig,
        branch: BranchReference,
        author: String?,
    ) {
        val repositoryId = RepositoryId(newConfig.repositoryId)
        val currentConfig = getConfig(repositoryId, branch)

        // Validate that the migration is supported
        validateMigration(currentConfig, newConfig)

        val currentIsolated = isIsolated(repositoryId)!!
        val targetIsolated = !newConfig.legacyGlobalStorage

        // Handle storage migration first
        if (currentIsolated != targetIsolated) {
            migrateStorage(repositoryId, currentIsolated, targetIsolated)
        }

        // Skip tree migration if nothing relevant has changed
        if (currentConfig.legacyNameBasedRoles == newConfig.legacyNameBasedRoles &&
            currentConfig.nodeIdType == newConfig.nodeIdType &&
            currentConfig.modelId == newConfig.modelId
        ) {
            return
        }

        // Perform tree migration for other config changes
        val branches = getBranches(repositoryId)
        for (branch in branches) {
            val oldVersion = getVersion(branch) ?: continue
            val sourceModel = oldVersion.getModelTree().asModelSingleThreaded()
            val targetTree = newConfig.createEmptyTree().asMutableSingleThreaded()
            val targetModel = targetTree.asModel()
            ModelSynchronizer(
                sourceRoot = sourceModel.getRootNode(),
                targetRoot = targetModel.getRootNode(),
                nodeAssociation = NodeAssociationForIdMigration(targetModel),
            ).synchronize()
            val newVersion = IVersion.builder()
                .tree(targetTree.getTransaction().tree)
                .baseVersion(oldVersion)
                .author(author)
                .currentTime()
                .build()
            putVersionHash(branch, newVersion.getContentHash())
        }
    }

    private fun validateMigration(currentConfig: RepositoryConfig, newConfig: RepositoryConfig) {
        // Check for role storage migration (name-based <-> id-based)
        if (currentConfig.legacyNameBasedRoles != newConfig.legacyNameBasedRoles) {
            throw UnsupportedMigrationException(
                "Migration between name-based and id-based role storage is not supported. " +
                    "This requires a metamodel to map role names to role IDs.",
            )
        }

        // Check for model ID changes
        if (currentConfig.modelId != newConfig.modelId) {
            throw UnsupportedMigrationException(
                "Changing the model ID is not supported. " +
                    "Node references include the model ID and cannot be resolved across different model IDs.",
            )
        }

        // Note: Storage migration (global <-> isolated) is supported and handled separately
        // Note: int64 -> string node IDs is supported
        // Note: string -> int64 node IDs is supported if strings are valid hex-encoded int64 values
        //       (validation happens at runtime during the migration)
    }

    @RequiresTransaction
    fun migrateStorage(repositoryId: RepositoryId, fromIsolated: Boolean, toIsolated: Boolean) {
        // Collect branches, version hashes, and branch names BEFORE updating repository lists
        val branches = getBranches(repositoryId)
        val versionHashes = branches.mapNotNull { getVersionHash(it) }.toSet()
        val branchNames = getBranchNames(repositoryId)

        if (fromIsolated && !toIsolated) {
            // isolated → global: copy all objects from this repository to global storage
            stores.genericStore.copyRepositoryObjects(repositoryId, RepositoryId(""))
        }

        // Update repository lists
        val fromRepositories = getRepositories(fromIsolated)
        val toRepositories = getRepositories(toIsolated)
        stores.getGlobalStoreClient().put(
            repositoriesListKey(fromIsolated),
            (fromRepositories - repositoryId).joinToString("\n") { it.id },
        )
        stores.getGlobalStoreClient().put(
            repositoriesListKey(toIsolated),
            (toRepositories + repositoryId).joinToString("\n") { it.id },
        )

        // Update branch list
        stores.genericStore.put(branchListKey(repositoryId, fromIsolated), null)
        stores.genericStore.put(branchListKey(repositoryId, toIsolated), branchNames.joinToString("\n"))

        // Migrate version hashes to new storage location
        for (branch in branches) {
            val versionHash = stores.genericStore[branchKey(branch, fromIsolated)]
            if (versionHash != null) {
                stores.genericStore.put(branchKey(branch, toIsolated), versionHash, false)
                stores.genericStore.put(branchKey(branch, fromIsolated), null, false)
            }
        }

        if (!fromIsolated && toIsolated) {
            // global → isolated: Copy only objects reachable from this repository's versions
            GlobalStorageMigration(stores).copyReachableObjectsToIsolatedStorage(repositoryId, versionHashes)
        }
    }

    @RequiresTransaction
    override fun getConfig(repositoryId: RepositoryId, branchReference: BranchReference): RepositoryConfig {
        val version = requireNotNull(getVersion(branchReference)) {
            "No version found in repository $repositoryId"
        }
        val treeData = version.obj.data.getTree(TreeType.MAIN).resolveNow().data
        return RepositoryConfig(
            legacyNameBasedRoles = !treeData.usesRoleIds,
            legacyGlobalStorage = isIsolated(repositoryId) == false,
            nodeIdType = if (treeData.trieWithNodeRefIds != null) RepositoryConfig.NodeIdType.STRING else RepositoryConfig.NodeIdType.INT64,
            primaryTreeType = if (treeData.trieWithNodeRefIds != null) RepositoryConfig.TreeType.PATRICIA_TRIE else RepositoryConfig.TreeType.HASH_ARRAY_MAPPED_TRIE,
            modelId = treeData.id.id,
            repositoryId = repositoryId.id,
            repositoryName = repositoryId.id,
            alternativeNames = emptySet(),
        )
    }

    private fun branchKey(branch: BranchReference, isolated: Boolean = isIsolated(branch.repositoryId) ?: true): ObjectInRepository {
        return if (isolated) {
            ObjectInRepository(branch.repositoryId.id, "$KEY_PREFIX:branches:${SerializationUtil.escape(branch.branchName)}")
        } else {
            ObjectInRepository.global("$KEY_PREFIX:repositories:${branch.repositoryId.id}:branches:${branch.branchName}")
        }
    }

    private fun versionHistoryKey(repositoryId: RepositoryId, versionHash: ObjectHash, isolated: Boolean = isIsolated(repositoryId) ?: true): ObjectInRepository {
        return if (isolated) {
            ObjectInRepository(repositoryId.id, "$KEY_PREFIX:historyIndex:$versionHash")
        } else {
            ObjectInRepository.global("$KEY_PREFIX:repositories:${repositoryId.id}:historyIndex:$versionHash")
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

private class NodeAssociationForIdMigration(val targetModel: IMutableModel) : INodeAssociation {
    override fun resolveTarget(sourceNode: IReadableNode): IWritableNode? {
        return targetModel.tryResolveNode(NodeReference(sourceNode.getOriginalOrCurrentReference()))
    }

    override fun associate(
        sourceNode: IReadableNode,
        targetNode: IWritableNode,
    ) {
        val sourceReference = sourceNode.getNodeReference()
        val expectedTargetReference = NodeReference(sourceNode.getOriginalOrCurrentReference())
        val actualTargetReference = targetNode.getNodeReference()
        require(expectedTargetReference == actualTargetReference) {
            "Cannot associate $sourceReference with $actualTargetReference, expected: $expectedTargetReference"
        }
    }
}

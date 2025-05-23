package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.model.IVersion
import org.modelix.model.TreeId
import org.modelix.model.VersionMerger
import org.modelix.model.api.ChildLinkReferenceByName
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.client.ReplicatedRepository
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.INodeIdGenerator
import org.modelix.model.mutable.ModelixIdGenerator
import org.modelix.model.mutable.getRootNode
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.test.RandomModelChangeGenerator
import org.modelix.streams.getBlocking
import java.util.Collections
import java.util.SortedSet
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ReplicatedRepositoryTest {

    private fun runTest(block: suspend ApplicationTestBuilder.(scope: CoroutineScope) -> Unit) = testApplication {
        application {
            installDefaultServerPlugins()
            val storeClient = InMemoryStoreClient()
            val repositoriesManager = RepositoriesManager(storeClient)
            ModelReplicationServer(repositoriesManager).init(this)
            KeyValueLikeModelServer(repositoriesManager).init(this)
            IdsApiImpl(repositoriesManager).init(this)
        }

        coroutineScope {
            block(this)
        }
    }

    @Test
    fun `sequential write from multiple clients`() = runTest {
        val url = "http://localhost/v2"
        val modelClient = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        val modelClient2 = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        modelClient.initRepository(repositoryId)

        val idGenerator: (TreeId) -> INodeIdGenerator<INodeReference> =
            { ModelixIdGenerator(modelClient2.getIdGenerator(), it) }

        modelClient.getReplicatedModel(repositoryId.getBranchReference(), idGenerator).use { replicatedModel ->
            modelClient2.getReplicatedModel(repositoryId.getBranchReference(), idGenerator).use { replicatedModel2 ->
                val branch1 = replicatedModel.start()
                val branch2 = replicatedModel2.start()

                val rand = Random(34554)

                for (changeId in 1..10) {
                    println("change set $changeId")
                    val branchToChange = if (rand.nextBoolean()) {
                        println("changing branch 1")
                        branch1
                    } else {
                        println("changing branch 2")
                        branch2
                    }
                    branchToChange.runWrite {
                        val changeGenerator = RandomModelChangeGenerator(branchToChange.getRootNode().asLegacyNode(), rand)
                        repeat(1000) { _ ->
                            changeGenerator.applyRandomChange()
                        }
                    }

                    val syncTime = measureTime {
                        for (timeout in 1..1000) {
                            if (branch1.treeHash() == branch2.treeHash()) break
                            delay(1.milliseconds)
                        }
                    }
                    println("synced after $syncTime")
                    val data1 = branch1.runRead {
                        println("reading on branch 1: " + branch1.treeHash())
                        branch1.getRootNode().asLegacyNode().asData()
                    }
                    val data2 = branch2.runRead {
                        println("reading on branch 2: " + branch2.treeHash())
                        branch2.getRootNode().asLegacyNode().asData()
                    }
                    assertEquals(data1, data2)
                }
            }
        }
    }

    @RepeatedTest(value = 10)
    fun concurrentWrite(repetitionInfo: RepetitionInfo) = runTest { scope ->
        val url = "http://localhost/v2"
        val clients = (1..3).map {
            ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        }

        val repositoryId = RepositoryId("repo1")
        val initialVersion = clients[0].initRepository(repositoryId)
        val branchId = repositoryId.getBranchReference("my-branch")
        clients[0].push(branchId, initialVersion, initialVersion)
        val models = clients.map { client ->
            client.getReplicatedModel(branchId, { ModelixIdGenerator(client.getIdGenerator(), it) }, scope).also {
                it.start()
            }
        }

        try {
            val createdNodes: MutableSet<INodeReference> = Collections.synchronizedSet(HashSet<INodeReference>())

            coroutineScope {
                suspend fun launchWriter(model: ReplicatedModel, seed: Int) {
                    launch {
                        val rand = Random(seed)
                        repeat(10) {
                            delay(rand.nextLong(50, 100))
                            model.getModel().executeWrite {
                                createdNodes += model.getModel().getRootNode().addNewChild(
                                    ChildLinkReferenceByName("role"),
                                    -1,
                                    NullConcept.getReference(),
                                ).getNodeReference()
                            }
                        }
                    }
                }
                models.forEachIndexed { index, model ->
                    launchWriter(model, 56456 + index + repetitionInfo.currentRepetition * 100000)
                    delay(200.milliseconds)
                }
            }

            assertEquals(clients.size * 10, createdNodes.size)

            runCatching {
                withTimeout(30.seconds) {
                    models.forEach { model ->
                        while (getChildren(model) != createdNodes) delay(100.milliseconds)
                    }
                }
            }

            // models.forEach { it.resetToServerVersion() }

            val serverVersion = clients[0].pull(branchId, null)
            val childrenOnServer = getChildren(serverVersion.getModelTree())

            assertEquals(createdNodes, childrenOnServer)

            for (model in models) {
                assertEquals(createdNodes, getChildren(model))
            }
        } finally {
            models.forEach { it.dispose() }
        }
    }

    private interface IRandomOperation {
        suspend fun isApplicable(): Boolean
        suspend fun apply()
    }

    /**
     * Similar to the concurrentWrite test, but without race conditions.
     * Doesn't use the ReplicatedModel which allows to narrow down the cause of issues.
     * Makes it easier to test and fix performance issues.
     */
    @RepeatedTest(value = 10)
    fun deterministicConcurrentWrite(repetitionInfo: RepetitionInfo) = runTest {
        val url = "http://localhost/v2"
        val clients = (1..3).map {
            ModelClientV2.builder().url(url).client(client).lazyAndBlockingQueries().build().also { it.init() }
        }

        val repositoryId = RepositoryId("repo1")
        val branchId = repositoryId.getBranchReference("my-branch")
        run {
            val initialVersion = clients[0].initRepository(repositoryId) as CLVersion
            clients[0].push(branchId, initialVersion, initialVersion)
        }
        val initiallyPulledVersions = clients.associateWith<IModelClientV2, CLVersion> { it.pull(branchId, null) as CLVersion }
        val localVersions = ClientSpecificVersionMap(initiallyPulledVersions)
        val remoteVersions = ClientSpecificVersionMap(initiallyPulledVersions)
        val lastKnownRemoteVersion = ClientSpecificVersionMap(initiallyPulledVersions)

        val createdNodes: MutableSet<INodeReference> = Collections.synchronizedSet(HashSet<INodeReference>())
        val rand = Random(repetitionInfo.currentRepetition + 8745000)
        val nodesToCreate = clients.size * 10

        fun createOpsForClient(client: ModelClientV2) = listOf<IRandomOperation>(
            object : IRandomOperation {
                override suspend fun isApplicable(): Boolean {
                    return createdNodes.size < nodesToCreate
                }

                override suspend fun apply() {
                    // Change local version
                    val baseVersion = localVersions[client]
                    val branch = OTBranch(PBranch(baseVersion.getTree(), client.getIdGenerator()), client.getIdGenerator())
                    branch.runWriteT { t ->
                        createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).let {
                            PNodeReference(it, branch.transaction.tree.getId())
                        }
                    }
                    val (ops, tree) = branch.getPendingChanges()
                    val newVersion = CLVersion.createRegularVersion(
                        id = client.getIdGenerator().generate(),
                        time = null,
                        author = client.getUserId(),
                        tree = tree,
                        baseVersion = baseVersion,
                        operations = ops.map { it.getOriginalOp() }.toTypedArray(),
                    )
                    localVersions[client] = newVersion
                }
            },
            object : IRandomOperation {
                override suspend fun isApplicable(): Boolean {
                    return remoteVersions[client].getContentHash() != localVersions[client].getContentHash()
                }

                override suspend fun apply() {
                    // Merge local into remote
                    remoteVersions[client] = VersionMerger(client.getIdGenerator())
                        .mergeChange(remoteVersions[client], localVersions[client])
                }
            },
            object : IRandomOperation {
                override suspend fun isApplicable(): Boolean {
                    return remoteVersions[client].getContentHash() != localVersions[client].getContentHash()
                }

                override suspend fun apply() {
                    // Merge remote into local
                    localVersions[client] = VersionMerger(client.getIdGenerator())
                        .mergeChange(localVersions[client], remoteVersions[client])
                }
            },
            object : IRandomOperation {
                override suspend fun isApplicable(): Boolean {
                    return remoteVersions[client].getContentHash() != lastKnownRemoteVersion[client].getContentHash()
                }

                override suspend fun apply() {
                    // Push to server
                    val receivedVersion = client.push(branchId, remoteVersions[client], lastKnownRemoteVersion[client]) as CLVersion
                    lastKnownRemoteVersion[client] = receivedVersion
                    remoteVersions[client] = VersionMerger(client.getIdGenerator())
                        .mergeChange(remoteVersions[client], receivedVersion)
                }
            },
            object : IRandomOperation {
                override suspend fun isApplicable(): Boolean {
                    return client.pullHash(branchId) != remoteVersions[client].getContentHash()
                }

                override suspend fun apply() {
                    // Pull from server
                    val receivedVersion = client.pull(branchId, lastKnownRemoteVersion[client]) as CLVersion
                    lastKnownRemoteVersion[client] = receivedVersion
                    remoteVersions[client] = VersionMerger(client.getIdGenerator())
                        .mergeChange(remoteVersions[client], receivedVersion)
                }
            },
        )

        while (true) {
            val applicableOps = clients.flatMap { createOpsForClient(it) }.filter { it.isApplicable() }
            if (applicableOps.isEmpty()) break
            applicableOps.random(rand).apply()
        }

        assertEquals(nodesToCreate, createdNodes.size)

        val serverVersion = clients[0].pull(branchId, null)
        val childrenOnServer = getChildren(serverVersion.getModelTree())

        assertEquals(createdNodes, childrenOnServer)

        for (client in clients) {
            assertEquals(createdNodes, getChildren(localVersions[client]))
        }
    }

    @Ignore("Not stable yet. See https://issues.modelix.org/issue/MODELIX-554/Unstable-ModelClient-v1")
    @RepeatedTest(value = 10)
    fun clientCompatibility(repetitionInfo: RepetitionInfo) = runTest { scope ->
        val url = "http://localhost/v2"
        val clients = (1..2).map {
            ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        }
        val v1clients = (1..2).map {
            RestWebModelClient("http://localhost/", providedClient = client)
        }

        val repositoryId = RepositoryId("repo1")
        val initialVersion = clients[0].initRepository(repositoryId)
        val branchId = repositoryId.getBranchReference("my-branch")
        clients[0].push(branchId, initialVersion, initialVersion)
        val models = clients.map { client ->
            client.getReplicatedModel(branchId, { ModelixIdGenerator(client.getIdGenerator(), it) }, scope).also {
                it.start()
            }
        }
        val v1models = v1clients.map { ReplicatedRepository(it, repositoryId, branchId.branchName, { "user" }) }

        try {
            val createdNodes: MutableSet<INodeReference> = Collections.synchronizedSet(HashSet<INodeReference>())

            coroutineScope {
                suspend fun launchWriter(model: ReplicatedModel, seed: Int) = launch {
                    val rand = Random(seed)
                    repeat(10) {
                        delay(rand.nextLong(50, 100))

                        model.getModel().executeWrite {
                            createdNodes += model.getModel().getRootNode().addNewChild(
                                ChildLinkReferenceByName("role"),
                                -1,
                                NullConcept.getReference(),
                            ).getNodeReference()
                        }
                    }
                }
                models.forEachIndexed { index, model ->
                    launchWriter(model, 56456 + index + repetitionInfo.currentRepetition * 100000)
                    delay(200.milliseconds)
                }
                suspend fun launchWriterv1(repository: ReplicatedRepository, seed: Int) {
                    launch {
                        val rand = Random(seed)
                        repeat(10) {
                            delay(rand.nextLong(50, 100))
                            repository.branch.runWriteT { t ->
                                createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).let {
                                    PNodeReference(it, repository.branch.transaction.tree.getId())
                                }
                            }
                        }
                    }
                }
                v1models.forEachIndexed { index, model ->
                    launchWriterv1(model, 56456 + index + repetitionInfo.currentRepetition * 100000)
                    delay(200.milliseconds)
                }
            }

            assertEquals((clients.size + v1clients.size) * 10, createdNodes.size)

            runCatching {
                withTimeout(30.seconds) {
                    models.forEach { model ->
                        while (getChildren(model) != createdNodes) delay(100.milliseconds)
                    }
                    v1models.forEach { model ->
                        while (getChildren(model) != createdNodes) delay(100.milliseconds)
                    }
                }
            }

            // models.forEach { it.resetToServerVersion() }

            val serverVersion = clients[0].pull(branchId, null)
            val childrenOnServer = getChildren(serverVersion.getModelTree())

            assertEquals(createdNodes, childrenOnServer)

            for (model in models) {
                assertEquals(createdNodes, getChildren(model))
            }
            for (model in v1models) {
                assertEquals(createdNodes, getChildren(model))
            }
        } finally {
            models.forEach { it.dispose() }
            v1models.forEach { it.dispose() }
            v1clients.forEach { it.dispose() }
        }
    }

    @Test
    fun `used id specified for client is used in replicated model`() = runTest {
        val userId = "a_user_id"
        val url = "http://localhost/v2"
        val modelClient = ModelClientV2
            .builder()
            .url(url)
            .client(client)
            .userId(userId)
            .build()
            .also { it.init() }
        val repositoryId = RepositoryId("repo1")
        modelClient.initRepository(repositoryId)
        val idGenerator = { treeId: TreeId -> ModelixIdGenerator(modelClient.getIdGenerator(), treeId) }
        modelClient.getReplicatedModel(repositoryId.getBranchReference(), idGenerator).use { replicatedModel ->
            val tree = replicatedModel.start()
            val initialVersion = modelClient.pull(replicatedModel.branchRef, null)

            tree.runWrite {
                tree.getRootNode().addNewChild(
                    ChildLinkReferenceByName("role"),
                    -1,
                    NullConcept.getReference(),
                ).getNodeReference()
            }
            while (replicatedModel.getCurrentVersion() == initialVersion) {
                delay(10)
            }

            assertEquals(userId, replicatedModel.getCurrentVersion().author)
        }
    }
}

private fun getChildren(version: CLVersion): SortedSet<INodeReference> = getChildren(version.getModelTree())
private fun getChildren(repository: ReplicatedRepository): SortedSet<INodeReference> = getChildren(repository)
private fun getChildren(model: ReplicatedModel): SortedSet<INodeReference> = getChildren(model.getVersionedModelTree())
private fun getChildren(modelTree: IMutableModelTree): SortedSet<INodeReference> = modelTree.runRead {
    getChildren(it.tree)
}
private fun getChildren(modelTree: IGenericModelTree<INodeReference>): SortedSet<INodeReference> =
    modelTree.getChildren(modelTree.getRootNodeId()).toList().getBlocking(modelTree)
        .toSortedSet(compareBy { it.serialize() })

private fun IMutableModelTree.treeHash(): String = runRead { t -> t.tree.asObject().getHashString() }

class ClientSpecificVersionMap(initialEntries: Map<IModelClientV2, IVersion>) {
    private val map = mutableMapOf<IModelClientV2, CLVersion>()
    init {
        putAll(initialEntries)
    }
    operator fun get(client: IModelClientV2): CLVersion {
        return map[client]!!
    }
    operator fun set(client: IModelClientV2, version: IVersion) {
        version as CLVersion
        map[client] = version
    }
    fun putAll(entries: Map<IModelClientV2, IVersion>) {
        entries.forEach { set(it.key, it.value) }
    }
}

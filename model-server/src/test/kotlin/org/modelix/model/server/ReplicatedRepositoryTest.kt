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
import org.modelix.model.IVersion
import org.modelix.model.ModelFacade
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.client.ReplicatedRepository
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.getTreeObject
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.test.RandomModelChangeGenerator
import java.util.Collections
import java.util.SortedSet
import java.util.TreeSet
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

        modelClient.getReplicatedModel(repositoryId.getBranchReference()).use { replicatedModel ->
            modelClient2.getReplicatedModel(repositoryId.getBranchReference()).use { replicatedModel2 ->
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
                        val changeGenerator = RandomModelChangeGenerator(branchToChange.getRootNode(), rand)
                        repeat(1000) { _ ->
                            changeGenerator.applyRandomChange()
                        }
                        println("new tree: " + (branchToChange.transaction.tree).getTreeObject().getHashString())
                    }

                    val syncTime = measureTime {
                        for (timeout in 1..1000) {
                            if (branch1.treeHash() == branch2.treeHash()) break
                            delay(1.milliseconds)
                        }
                    }
                    println("synced after $syncTime")
                    val data1 = branch1.computeRead {
                        println("reading on branch 1: " + branch1.treeHash())
                        branch1.getRootNode().asData()
                    }
                    val data2 = branch2.computeRead {
                        println("reading on branch 2: " + branch2.treeHash())
                        branch2.getRootNode().asData()
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
        val models = clients.map { client -> client.getReplicatedModel(branchId, scope).also { it.start() } }

        try {
            val createdNodes: MutableSet<String> = Collections.synchronizedSet(TreeSet<String>())

            coroutineScope {
                suspend fun launchWriter(model: ReplicatedModel, seed: Int) {
                    launch {
                        val rand = Random(seed)
                        repeat(10) {
                            delay(rand.nextLong(50, 100))
                            model.getBranch().runWriteT { t ->
                                createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).toString(16)
                            }
                        }
                    }
                }
                models.forEachIndexed { index, model ->
                    launchWriter(model, 56456 + index + repetitionInfo.currentRepetition * 100000)
                    delay(200.milliseconds)
                }
            }

            fun getChildren(model: ReplicatedModel): SortedSet<String> {
                return getChildren(model.getBranch())
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
            val childrenOnServer = getChildren(PBranch(serverVersion.getTree(), IdGeneratorDummy()))

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

        val createdNodes: MutableSet<String> = Collections.synchronizedSet(TreeSet<String>())
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
                        createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).toString(16)
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

        fun getChildren(model: CLVersion): SortedSet<String> {
            return getChildren(PBranch(model.getTree(), IdGeneratorDummy()))
        }

        assertEquals(nodesToCreate, createdNodes.size)

        val serverVersion = clients[0].pull(branchId, null)
        val childrenOnServer = getChildren(PBranch(serverVersion.getTree(), IdGeneratorDummy()))

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
        val models = clients.map { client -> client.getReplicatedModel(branchId, scope).also { it.start() } }
        val v1models = v1clients.map { ReplicatedRepository(it, repositoryId, branchId.branchName, { "user" }) }

        try {
            val createdNodes: MutableSet<String> = Collections.synchronizedSet(TreeSet<String>())

            coroutineScope {
                suspend fun launchWriter(model: ReplicatedModel, seed: Int) {
                    launch {
                        val rand = Random(seed)
                        repeat(10) {
                            delay(rand.nextLong(50, 100))
                            model.getBranch().runWriteT { t ->
                                createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).toString(16)
                            }
                        }
                    }
                }
                models.forEachIndexed { index, model ->
                    launchWriter(model, 56456 + index + repetitionInfo.currentRepetition * 100000)
                    delay(200.milliseconds)
                }
                suspend fun launchWriterv1(model: ReplicatedRepository, seed: Int) {
                    launch {
                        val rand = Random(seed)
                        repeat(10) {
                            delay(rand.nextLong(50, 100))
                            model.branch.runWriteT { t ->
                                createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).toString(16)
                            }
                        }
                    }
                }
                v1models.forEachIndexed { index, model ->
                    launchWriterv1(model, 56456 + index + repetitionInfo.currentRepetition * 100000)
                    delay(200.milliseconds)
                }
            }

            fun getChildren(model: ReplicatedModel): SortedSet<String> {
                return getChildren(model.getBranch())
            }
            fun getChildren(model: ReplicatedRepository): SortedSet<String> {
                return getChildren(model.branch)
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
            val childrenOnServer = getChildren(PBranch(serverVersion.getTree(), IdGeneratorDummy()))

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

    @Ignore
    @Test
    fun mergePerformanceTest() {
        val rand = Random(917563)
        val idGenerator = IdGenerator.getInstance(100)
        val initialTree = ModelFacade.newLocalTree()
        val initialVersion = CLVersion.createRegularVersion(
            idGenerator.generate(),
            null,
            null,
            initialTree,
            null,
            emptyArray(),
        )
        val versions: MutableList<CLVersion> = mutableListOf(initialVersion)
        val nonMergedVersions = mutableSetOf<CLVersion>()
        var headVersion: CLVersion = initialVersion
        val createdNodes: MutableSet<String> = Collections.synchronizedSet(TreeSet<String>())

        fun mergeVersion(versionToMerge: CLVersion) {
            headVersion = VersionMerger(idGenerator).mergeChange(headVersion, versionToMerge)
            nonMergedVersions.remove(versionToMerge)
        }

        repeat(100) {
            val baseVersion = versions[rand.nextInt(versions.size)]
            val branch = OTBranch(PBranch(baseVersion.getTree(), idGenerator), idGenerator)
            branch.runWriteT { t ->
                createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).toString(16)
            }
            val (ops, newTree) = branch.getPendingChanges()
            val newVersion = CLVersion.createRegularVersion(
                idGenerator.generate(),
                null,
                null,
                newTree,
                baseVersion,
                ops.map { it.getOriginalOp() }.toTypedArray(),
            )
            versions += newVersion
            nonMergedVersions -= baseVersion
            nonMergedVersions += newVersion

            while (nonMergedVersions.size > rand.nextInt(10)) {
                mergeVersion(nonMergedVersions.random(rand))
            }
        }

        while (nonMergedVersions.isNotEmpty()) {
            mergeVersion(nonMergedVersions.random(rand))
        }

        val headChildren = getChildren(PBranch(headVersion.getTree(), IdGeneratorDummy()))

        assertEquals(createdNodes, headChildren)
    }

    private fun getChildren(branch: IBranch): SortedSet<String> {
        return branch.computeRead {
            branch.getRootNode().allChildren.map { (it as PNodeAdapter).nodeId.toString(16) }.toSortedSet()
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
        modelClient.getReplicatedModel(repositoryId.getBranchReference()).use { replicatedModel ->
            val branch = replicatedModel.start() as OTBranch
            val initialVersion = modelClient.pull(replicatedModel.branchRef, null) as CLVersion

            branch.computeWriteT {
                it.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?)
            }
            while (replicatedModel.getCurrentVersion() == initialVersion) {
                delay(10)
            }

            assertEquals(userId, replicatedModel.getCurrentVersion().author)
        }
    }
}

private fun IBranch.treeHash(): String = computeReadT { t -> (t.tree).getTreeObject().getHashString() }

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

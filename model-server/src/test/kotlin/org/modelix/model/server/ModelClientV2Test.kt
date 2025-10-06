package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import mu.KotlinLogging
import org.modelix.datastructures.model.MutationParameters
import org.modelix.datastructures.model.getHash
import org.modelix.datastructures.objects.IObjectData
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.ITree
import org.modelix.model.api.NullChildLink
import org.modelix.model.api.PBranch
import org.modelix.model.api.addNewChild
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.VersionNotFoundException
import org.modelix.model.client2.diffAsMutationParameters
import org.modelix.model.client2.runWrite
import org.modelix.model.client2.runWriteOnBranch
import org.modelix.model.client2.runWriteOnModel
import org.modelix.model.client2.runWriteOnTree
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.MissingEntryException
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mutable.ModelixIdGenerator
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.RevertToOp
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.modelql.core.count
import org.modelix.modelql.core.filter
import org.modelix.modelql.core.isNotEmpty
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.descendants
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val LOG = KotlinLogging.logger { }

class ModelClientV2Test {

    private lateinit var statistics: StoreClientWithStatistics
    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            try {
                installDefaultServerPlugins()
                statistics = StoreClientWithStatistics(InMemoryStoreClient())
                val repoManager = RepositoriesManager(statistics)
                ModelReplicationServer(repoManager).init(this)
                IdsApiImpl(repoManager).init(this)
            } catch (ex: Throwable) {
                LOG.error("", ex)
            }
        }
        block()
    }

    @Test
    fun `can create and write repository`() = runTest {
        val client = createModelClient()

        val repositoryId = RepositoryId("repo1")
        val initialVersion = client.initRepository(repositoryId)
        assertEquals(
            0,
            initialVersion.getTree().asAsyncTree()
                .let { it.getStreamExecutor().querySuspending { it.getAllChildren(ITree.ROOT_ID).count() } },
        )

        val branch = OTBranch(PBranch(initialVersion.getTree(), client.getIdGenerator()), client.getIdGenerator())
        branch.runWriteT { t ->
            t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?)
        }
        val (ops, newTree) = branch.getPendingChanges()
        val newVersion = CLVersion.createRegularVersion(
            client.getIdGenerator().generate(),
            null,
            null,
            newTree,
            initialVersion as CLVersion,
            ops.map { it.getOriginalOp() }.toTypedArray(),
        )

        assertEquals(
            setOf(repositoryId.getBranchReference()),
            client.listBranches(repositoryId).toSet(),
        )

        val branchId = repositoryId.getBranchReference("my-branch")
        val mergedVersion = client.push(branchId, newVersion, initialVersion)
        assertEquals(1, mergedVersion.getTree().getAllChildren(ITree.ROOT_ID).count())

        assertEquals(
            client.listBranches(repositoryId).toSet(),
            setOf(repositoryId.getBranchReference(), branchId),
        )
    }

    @Test
    fun modelqlSmokeTest() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()
        val initialVersion = client.initRepository(repositoryId)
        val size = client.query(branchRef) { it.allChildren().count() }
        assertEquals(0, size)

        val size2 = client.query(repositoryId, initialVersion.getContentHash()) { it.allChildren().count() }
        assertEquals(0, size2)
    }

    @Test
    fun `modelQL is executed efficiently using bulk requests`() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()
        client.initRepository(repositoryId)

        client.runWrite(branchRef) { rootNode ->
            fun createNodes(parentNode: INode, numberOfNodes: Int, rand: Random) {
                if (numberOfNodes == 0) return
                if (numberOfNodes == 1) {
                    parentNode.addNewChild(NullChildLink, 0)
                    return
                }
                val numChildren = rand.nextInt(10, 20).coerceAtMost(numberOfNodes)
                val subtreeSize = numberOfNodes / numChildren
                val remainder = numberOfNodes % numChildren
                for (i in 1..numChildren) {
                    createNodes(parentNode.addNewChild(NullChildLink, 0), subtreeSize - 1 + (if (i == 1) remainder else 0), rand)
                }
            }

            createNodes(rootNode, 10_000, Random(76398))
        }

        val requestsBefore = statistics.getTotalRequests()
        val size = client.query(branchRef) { it.descendants(false).filter { it.allChildren().isNotEmpty() }.count() }
        val requestsAfter = statistics.getTotalRequests()
        assertEquals(2581, size)
        assertEquals(4, requestsAfter - requestsBefore)
    }

    @Test
    fun testSlashesInPathSegmentsFromRepositoryIdAndBranchId() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId("repo/v1")
        val initialVersion = client.initRepository(repositoryId)
        val branchId = repositoryId.getBranchReference("my-branch/v1")
        client.push(branchId, initialVersion, null)
        assertEquals(
            client.listBranches(repositoryId).toSet(),
            setOf(repositoryId.getBranchReference(), branchId),
        )
    }

    @Test
    fun `user id can be provided to client after creation`() = runTest {
        val modelClient = createModelClient()
        val userId = "a_user_id"
        modelClient.setClientProvidedUserId(userId)

        assertEquals(userId, modelClient.getUserId())
    }

    @Test
    fun `user id provided by client can be removed`() = runTest {
        val url = "http://localhost/v2"
        val userId = "a_user_id"
        val modelClient = ModelClientV2
            .builder()
            .url(url)
            .client(client)
            .userId(userId)
            .build()
        modelClient.init()

        assertEquals(userId, modelClient.getUserId())
        modelClient.setClientProvidedUserId(null)

        assertEquals("unit-tests@example.com", modelClient.getUserId())
    }

    @Test
    fun `newly created repository can be removed`() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())
        client.initRepository(repositoryId)

        val success = client.deleteRepository(repositoryId)
        val containsRepository = client.listRepositories().contains(repositoryId)

        assertTrue(success)
        assertFalse(containsRepository)
    }

    @Test
    fun `non-existing repository cannot be removed`() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())

        val success = client.deleteRepository(repositoryId)
        val containsRepository = client.listRepositories().contains(repositoryId)

        assertFalse(success)
        assertFalse(containsRepository)
    }

    @Test
    fun `branches from non-existing repositories cannot be removed`() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())

        val success = client.deleteBranch(BranchReference(repositoryId, "doesntmatter"))

        assertFalse(success)
    }

    @Test
    fun `non-existing branches from existing repositories cannot be removed`() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())
        client.initRepository(repositoryId)

        val success = client.deleteBranch(BranchReference(repositoryId, "doesnotexist"))

        assertFalse(success)
    }

    @Test
    fun `existing branches from existing repositories can be removed`() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())
        client.initRepository(repositoryId)
        val branchToDelete = BranchReference(repositoryId, "todelete")
        client.push(
            branchToDelete,
            requireNotNull(
                client.pullIfExists(BranchReference(repositoryId, "master")),
            ) { "the master branch must always exist" },
            null,
        )

        val success = client.deleteBranch(BranchReference(repositoryId, branchToDelete.branchName))

        assertTrue(success)
        assertFalse(client.listBranches(repositoryId).contains(branchToDelete))
    }

    @Test
    fun `pulling existing versions pulls all referenced objects`() = runTest {
        // Arrange
        val modelClientForArrange = createModelClient()
        val modelClientForAssert = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val branchId = repositoryId.getBranchReference("my-branch")
        modelClientForArrange.runWrite(branchId) { root ->
            // Creating many children makes the flow emitting many values at once.
            repeat(100) {
                root.addNewChild("aChild", -1, null as IConceptReference?)
            }
        }

        // Act
        val versionPulled = modelClientForAssert.pull(
            branchId,
            null,
            ObjectDeltaFilter(
                knownVersions = emptySet(),
                includeOperations = true,
                includeHistory = true,
                includeTrees = true,
            ),
        ) as CLVersion

        // Assert
        fun checkAllReferencedEntriesExistInStore(referencingEntry: IObjectData) {
            try {
                for (entryReference in referencingEntry.getAllReferences()) {
                    // Check that the store also provides each referenced KVEntry.
                    // `getValue` would fail if this is not the case.
                    val referencedEntry = entryReference.resolveLater().query()
                    checkAllReferencedEntriesExistInStore(referencedEntry.data)
                }
            } catch (ex: MissingEntryException) {
                throw RuntimeException("Referenced by ${referencingEntry.serialize()}", ex)
            }
        }
        checkAllReferencedEntriesExistInStore(versionPulled.data!!)
    }

    @Test
    fun `writing no data does not create empty versions`() = runTest {
        // Arrange
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val branchId = repositoryId.getBranchReference("master")
        modelClient.initRepository(repositoryId)
        val versionAfterBeforeWrite = modelClient.pullIfExists(branchId)!!

        // Act
        modelClient.runWriteOnBranch(branchId) {
            // do nothing
        }

        // Assert
        val versionAfterRunWrite = modelClient.pullIfExists(branchId)!!
        assertEquals(versionAfterBeforeWrite.getContentHash(), versionAfterRunWrite.getContentHash())
    }

    @Test
    fun `client can load version`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("aRepo")
        val initialVersion = modelClient.initRepository(repositoryId)

        val loadedVersion = modelClient.loadVersion(repositoryId, initialVersion.getContentHash(), initialVersion)

        assertEquals(initialVersion.getContentHash(), loadedVersion.getContentHash())
    }

    @Test
    fun `client can load version (deprecated endpoint without repository)`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("aRepo")
        val initialVersion = modelClient.initRepository(repositoryId)

        val loadedVersion = modelClient.loadVersion(initialVersion.getContentHash(), initialVersion)

        assertEquals(initialVersion.getContentHash(), loadedVersion.getContentHash())
    }

    @Test
    fun `create repository with useRoleIds true`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("useRoleIdsTrue")
        val initialVersion = modelClient.initRepository(repositoryId)

        assertTrue(initialVersion.getTree().usesRoleIds())
    }

    @Ignore
    @Test
    fun `create repository with useRoleIds false`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("useRoleIdsFalse")
        val initialVersion = modelClient.initRepository(repositoryId, useRoleIds = false)

        assertFalse(initialVersion.getTree().usesRoleIds())
    }

    @Test
    fun `can load version without knowing the repository`() = runTest {
        val modelClient = createModelClient()

        val initialVersions = (0..4).map {
            modelClient.initRepository(RepositoryId("repo$it"), legacyGlobalStorage = it % 2 != 0)
        }

        for (i in 0..4) {
            println("repo$i")
            val expectedHash = initialVersions[i].getContentHash()

            @Suppress("DEPRECATION") // calling this deprecated method is the purpose of this test
            val loadedVersion = modelClient.loadVersion(expectedHash, null)
            assertEquals(expectedHash, loadedVersion.getContentHash())
        }
    }

    @Test
    fun `loading unknown version throws VersionNotFoundException`() = runTest {
        val modelClient = createModelClient()
        assertFailsWith<VersionNotFoundException> {
            modelClient.loadVersion(HashUtil.sha256("xyz"), null)
        }
    }

    @Test
    fun `revert to old version`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("my-repo")
        val branch = repositoryId.getBranchReference()
        val initialVersion = modelClient.initRepository(repositoryId)

        val versionA = modelClient.runWriteOnModel(branch) { rootNode ->
            rootNode.setPropertyValue(IPropertyReference.fromName("name"), "a")
        }
        val versionB = modelClient.runWriteOnModel(branch) { rootNode ->
            rootNode.setPropertyValue(IPropertyReference.fromName("name"), "b")
        }
        val revertedVersionHash = modelClient.revertTo(branch, versionA.getObjectHash())

        val revertedVersion = modelClient.pull(branch, null) as CLVersion
        assertEquals(revertedVersionHash, revertedVersion.getObjectHash())
        assertEquals(versionB.getObjectHash(), revertedVersion.baseVersion?.getObjectHash())
        val op = revertedVersion.operations.single() as RevertToOp
        assertEquals(versionB.getObjectHash(), op.latestKnownVersionRef.getHash())
        assertEquals(versionA.getObjectHash(), op.versionToRevertToRef.getHash())
        assertEquals(versionA.getModelTree().getHash(), revertedVersion.getModelTree().getHash())
    }

    @Test
    fun `history diff as MutationParameters`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("my-repo")
        val branch = repositoryId.getBranchReference()
        val initialVersion = modelClient.initRepository(repositoryId)

        val rootId = initialVersion.getModelTree().getRootNodeId()
        val idGenerator = ModelixIdGenerator(modelClient.getIdGenerator(), initialVersion.getModelTree().getId())
        val child1Id = idGenerator.generate(rootId)
        val child2Id = idGenerator.generate(rootId)
        val expected = listOf(
            MutationParameters.Property(rootId, IPropertyReference.fromName("name"), "my root node"),
            MutationParameters.AddNew(rootId, IChildLinkReference.fromName("roleA"), -1, listOf(child1Id to NullConcept.getReference())),
            MutationParameters.AddNew(rootId, IChildLinkReference.fromName("roleA"), -1, listOf(child2Id to NullConcept.getReference())),
        )

        var lastVersion = initialVersion
        for (mutationParameters in expected) {
            lastVersion = modelClient.runWriteOnTree(branch) { tree ->
                tree.getWriteTransaction().mutate(mutationParameters)
            }
        }

        val actual = modelClient.diffAsMutationParameters(repositoryId, lastVersion.getObjectHash(), initialVersion.getObjectHash())
        assertEquals(expected, actual)
    }
}

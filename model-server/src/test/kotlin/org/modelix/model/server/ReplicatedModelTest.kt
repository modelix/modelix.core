package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.ChildLinkReferenceByName
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mutable.DummyIdGenerator
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.ModelixIdGenerator
import org.modelix.model.mutable.getRootNode
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ReplicatedModelTest {

    @Test
    fun startsWithLatestVersion() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())

        // Step 1: prepare repository with two versions beside the initial version
        // Step 1.1: create an empty repository
        val initialVersion = client.initRepository(repositoryId) as CLVersion

        // Step 1.2: add a new child node to get a new version
        val defaultBranchReference = repositoryId.getBranchReference()
        addHelloChild(initialVersion, client, defaultBranchReference)

        // Step 2: in a new client, fetch the latest repository data
        val newClient = createModelClient()
        // we do not provide an initial version, so we expect to fetch the latest one (with one "hello" child)
        coroutineScope {
            val scope = this
            ReplicatedModel(newClient, defaultBranchReference, { DummyIdGenerator() }, scope).use { replicatedModel ->
                try {
                    replicatedModel.getModel()
                    // if we get here, then we missed an expected exception
                    assertFalse(true)
                } catch (ex: Exception) {
                    /*
                    Expected exception, because we did not specify an initial version.
                    So without an explicit start we do not expect anything useful here.
                     */
                    assertTrue(ex.instanceOf(IllegalStateException::class))
                }

                val model = replicatedModel.start()
                // Step 3: wait a bit so replicated model can fetch the new versions from the server
                waitUntilChildArrives(model, scope, 500)

                // Step 4: check, eventually we must have the one "hello" child
                val children = getHelloChildrenOfRootNode(model)
                assertEquals(1, children.size)
            }
        }
    }

    @Test
    fun startsWithSpecificVersion() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())
        val defaultBranchReference = repositoryId.getBranchReference()

        // Step 1: prepare repository with two versions beside the initial version
        // Step 1.1: create an empty repository
        val initialVersion = client.initRepository(repositoryId) as CLVersion

        // Step 1.2: add a new child node to get a new version
        addHelloChild(initialVersion, client, defaultBranchReference)

        // Step 2: in a new client, fetch the oneHelloChildVersion
        val newClient = createModelClient()

        // Step 2.1: to avoid version was not created by this client exception
        val initialVersionClone = newClient.loadVersion(repositoryId, initialVersion.getContentHash(), null)

        val scope = CoroutineScope(Dispatchers.Default)
        // Step 2.2: we provide an initial version, so we expect to fetch it first (0 "hello" child)
        ReplicatedModel(
            newClient,
            defaultBranchReference,
            providedScope = scope,
            idGenerator = { ModelixIdGenerator(newClient.getIdGenerator(), it) },
            initialRemoteVersion = initialVersionClone as CLVersion,
        ).use { replicatedModel ->
            val tree = replicatedModel.getVersionedModelTree()

            // Step 3: check, here we must have 0 "hello" child
            val emptyChildren = getHelloChildrenOfRootNode(tree)
            assertTrue(emptyChildren.isEmpty())

            replicatedModel.start()
            // Step 4: wait a bit so replicated model can fetch the new versions from the server
            waitUntilChildArrives(tree, scope, 500)

            // Step 5: check, eventually we must have 1 "hello" child
            val children = getHelloChildrenOfRootNode(tree)
            assertEquals(1, children.size)
        }
    }

    @Test
    fun versionAttributesAreStoredOnCreatedVersions() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())
        val branchReference = repositoryId.getBranchReference()
        client.initRepository(repositoryId)

        val attrs = mapOf("env" to "staging", "pipeline" to "ci-42")
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            ReplicatedModel(
                client,
                branchReference,
                idGenerator = { ModelixIdGenerator(client.getIdGenerator(), it) },
                providedScope = scope,
                versionAttributes = { attrs },
            ).use { replicatedModel ->
                val tree = replicatedModel.start()
                tree.runWrite { t ->
                    t.mutate(MutationParameters.Property(t.tree.getRootNodeId(), IPropertyReference.fromName("test"), "value"))
                }
                withTimeout(5000.milliseconds) {
                    while (replicatedModel.getCurrentVersion().getAttributes() != attrs) {
                        delay(50.milliseconds)
                    }
                }
                assertEquals(attrs, replicatedModel.getCurrentVersion().getAttributes())
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun versionAttributesCanChangeBetweenVersions() = runTest {
        val client = createModelClient()
        val repositoryId = RepositoryId(UUID.randomUUID().toString())
        val branchReference = repositoryId.getBranchReference()
        client.initRepository(repositoryId)

        val currentAttrs = mutableMapOf("run" to "1")
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            ReplicatedModel(
                client,
                branchReference,
                idGenerator = { ModelixIdGenerator(client.getIdGenerator(), it) },
                providedScope = scope,
                versionAttributes = { currentAttrs.toMap() },
            ).use { replicatedModel ->
                val tree = replicatedModel.start()

                // First write — closure returns run=1
                tree.runWrite { t ->
                    t.mutate(MutationParameters.Property(t.tree.getRootNodeId(), IPropertyReference.fromName("a"), "1"))
                }
                withTimeout(5000) {
                    while (replicatedModel.getCurrentVersion().getAttributes() != mapOf("run" to "1")) delay(50)
                }
                assertEquals(mapOf("run" to "1"), replicatedModel.getCurrentVersion().getAttributes())

                // Mutate the source map — closure now returns run=2
                currentAttrs["run"] = "2"

                tree.runWrite { t ->
                    t.mutate(MutationParameters.Property(t.tree.getRootNodeId(), IPropertyReference.fromName("a"), "2"))
                }
                withTimeout(5000) {
                    while (replicatedModel.getCurrentVersion().getAttributes() != mapOf("run" to "2")) delay(50)
                }
                assertEquals(mapOf("run" to "2"), replicatedModel.getCurrentVersion().getAttributes())
            }
        } finally {
            scope.cancel()
        }
    }

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installDefaultServerPlugins()
            val repoManager = RepositoriesManager(InMemoryStoreClient())
            ModelReplicationServer(repoManager).init(this)
            IdsApiImpl(repoManager).init(this)
        }
        block()
    }

    private fun waitUntilChildArrives(modelTree: IMutableModelTree, scope: CoroutineScope, timeout: Long) {
        val barrier = CountDownLatch(1)
        scope.launch {
            var childArrived = false
            while (!childArrived) {
                childArrived = getHelloChildrenOfRootNode(modelTree).isNotEmpty()
            }
            barrier.countDown()
        }
        // wait at most timeout ms for the child to arrive
        barrier.await(timeout, TimeUnit.MILLISECONDS)
    }

    private suspend fun addHelloChild(
        baseVersion: CLVersion,
        client: ModelClientV2,
        branchReference: BranchReference,
    ): CLVersion {
        val branch =
            OTBranch(
                PBranch(baseVersion.getTree(), client.getIdGenerator()),
                client.getIdGenerator(),
            )
        branch.runWriteT { t ->
            t.addNewChild(ITree.ROOT_ID, "hello", -1, null as ConceptReference?)
        }
        val (ops, newTree) = branch.getPendingChanges()
        val newVersion = CLVersion.createRegularVersion(
            client.getIdGenerator().generate(),
            null,
            null,
            newTree,
            baseVersion,
            ops.map { it.getOriginalOp() }.toTypedArray(),
        )
        return client.push(branchReference, newVersion, baseVersion) as CLVersion
    }

    private fun getHelloChildrenOfRootNode(tree: IMutableModelTree): List<Any> =
        tree.runRead { tree.getRootNode().getChildren(ChildLinkReferenceByName("hello")).toList() }
}

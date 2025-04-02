package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.modelix.model.api.ChildLinkFromName
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
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
            ReplicatedModel(newClient, defaultBranchReference, scope).use { replicatedModel ->
                try {
                    replicatedModel.getBranch()
                    // if we get here, then we missed an expected exception
                    assertFalse(true)
                } catch (ex: Exception) {
                    /*
                    Expected exception, because we did not specify an initial version.
                    So without an explicit start we do not expect anything useful here.
                     */
                    assertTrue(ex.instanceOf(IllegalStateException::class))
                }

                val branch = replicatedModel.start()
                // Step 3: wait a bit so replicated model can fetch the new versions from the server
                waitUntilChildArrives(branch, scope, 500)

                // Step 4: check, eventually we must have the one "hello" child
                val children = getHelloChildrenOfRootNode(branch)
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
            initialRemoteVersion = initialVersionClone as CLVersion,
        ).use { replicatedModel ->
            val branch = replicatedModel.getBranch()

            // Step 3: check, here we must have 0 "hello" child
            val emptyChildren = getHelloChildrenOfRootNode(branch)
            assertTrue(emptyChildren.isEmpty())

            replicatedModel.start()
            // Step 4: wait a bit so replicated model can fetch the new versions from the server
            waitUntilChildArrives(branch, scope, 500)

            // Step 5: check, eventually we must have 1 "hello" child
            val children = getHelloChildrenOfRootNode(branch)
            assertEquals(1, children.size)
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

    private fun waitUntilChildArrives(branch: IBranch, scope: CoroutineScope, timeout: Long) {
        val barrier = CountDownLatch(1)
        scope.launch {
            var childArrived = false
            while (!childArrived) {
                childArrived = getHelloChildrenOfRootNode(branch).isNotEmpty()
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

    private fun getHelloChildrenOfRootNode(branch: IBranch) =
        branch.computeReadT { it.branch.getRootNode().getChildren(ChildLinkFromName("hello")).toList() }
}

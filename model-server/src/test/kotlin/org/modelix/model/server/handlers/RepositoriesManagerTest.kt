package org.modelix.model.server.handlers

import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.model.server.api.RepositoryConfig.NodeIdType
import org.modelix.model.server.store.IRepositoryAwareStore
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.RequiresTransaction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoriesManagerTest {

    val store = spyk<IRepositoryAwareStore>(InMemoryStoreClient())
    private val repoManager = RepositoriesManager(store)

    @RequiresTransaction
    private fun initRepository(repoId: RepositoryId) {
        repoManager.createRepository(repoId, "testUser", useRoleIds = true, legacyGlobalStorage = false)
    }

    @AfterTest
    fun cleanup() {
        store.close()
    }

    @Test
    fun `deleting default branch works`() = runTest {
        val repoId = RepositoryId("branch-removal")
        @OptIn(RequiresTransaction::class)
        repoManager.getTransactionManager().runWrite {
            initRepository(repoId)
            repoManager.removeBranches(repoId, setOf("master"))
        }
        @OptIn(RequiresTransaction::class)
        val branches = repoManager.getTransactionManager().runRead { repoManager.getBranches(repoId) }
        assertTrue { branches.none { it.branchName == "master" } }
    }

    @Test
    fun `deleting default branch keeps getConfig intact`() = runTest {
        val repoId = RepositoryId("branch-get-nondefault-config")
        val otherBranch = repoId.getBranchReference("versions/1.0.0")
        @OptIn(RequiresTransaction::class)
        repoManager.getTransactionManager().runWrite {
            this@RepositoriesManagerTest.repoManager.createRepository(
                repoId,
                "testUser",
                legacyGlobalStorage = true,
            )
            repoManager.forcePush(otherBranch, repoManager.getVersionHash(repoId.getBranchReference("master"))!!)
            repoManager.removeBranches(repoId, setOf("master"))
        }

        @OptIn(RequiresTransaction::class)
        val config = repoManager.getTransactionManager().runRead {
            repoManager.getConfig(repoId, otherBranch)
        }
        assertTrue { config.legacyGlobalStorage }
    }

    fun testConfigGetsCreatedAsSpecified(config: RepositoryConfig) = runTest {
        val repoId = RepositoryId(config.repositoryId)
        val branch = repoId.getBranchReference()
        @OptIn(RequiresTransaction::class)
        repoManager.getTransactionManager().runWrite {
            this@RepositoriesManagerTest.repoManager.createRepository(
                config,
                "testUser",
            )
        }

        @OptIn(RequiresTransaction::class)
        val newConfig = repoManager.getTransactionManager().runRead {
            repoManager.getConfig(repoId, branch)
        }
        assertEquals(config, newConfig)
    }

    @Test
    fun `createRepository as specified with legacyNameBasedRoles=true`() =
        testConfigGetsCreatedAsSpecified(
            RepositoryConfig(
                repositoryId = "createRepository1",
                modelId = "",
                repositoryName = "createRepository1",
                legacyNameBasedRoles = true,
            ),
        )

    @Test
    fun `createRepository as specified with legacyNameBasedRoles=false`() =
        testConfigGetsCreatedAsSpecified(
            RepositoryConfig(
                repositoryId = "createRepository2",
                modelId = "",
                repositoryName = "createRepository2",
                legacyNameBasedRoles = false,
            ),
        )

    @Test
    fun `createRepository as specified with legacyNameBasedRoles=false for INT64`() =
        testConfigGetsCreatedAsSpecified(
            RepositoryConfig(
                repositoryId = "createRepository3",
                modelId = "",
                repositoryName = "createRepository3",
                nodeIdType = NodeIdType.INT64,
                legacyNameBasedRoles = false,
            ),
        )

    @Test
    fun `createRepository as specified with legacyNameBasedRoles=true for INT64`() =
        testConfigGetsCreatedAsSpecified(
            RepositoryConfig(
                repositoryId = "createRepository4",
                modelId = "",
                repositoryName = "createRepository4",
                nodeIdType = NodeIdType.INT64,
                legacyNameBasedRoles = true,
            ),
        )

    @Test
    fun `repository data is removed when removing repository`() = runTest {
        val repoId = RepositoryId("abc")
        @OptIn(RequiresTransaction::class)
        repoManager.getTransactionManager().runWrite {
            initRepository(repoId)
            repoManager.removeRepository(repoId)
        }
        @OptIn(RequiresTransaction::class)
        store.runWriteTransaction {
            verify(exactly = 1) { store.removeRepositoryObjects(repoId) }
        }
    }

    @Test
    fun `data of other repositories remains intact when removing a repository`() = runTest {
        val existingRepo = RepositoryId("existing")
        val toBeDeletedRepo = RepositoryId("tobedeleted")
        @OptIn(RequiresTransaction::class)
        repoManager.getTransactionManager().runWrite {
            initRepository(existingRepo)
            initRepository(toBeDeletedRepo)

            fun getExistingRepositoryData() = store.getAll().filterKeys { it.getRepositoryId() == existingRepo.id }

            val dataBeforeDeletion = getExistingRepositoryData()
            repoManager.removeRepository(toBeDeletedRepo)
            val dataAfterDeletion = getExistingRepositoryData()

            assertTrue(dataBeforeDeletion.isNotEmpty(), "Expected repository data was not found.")
            assertEquals(dataBeforeDeletion, dataAfterDeletion, "Unexpected change in repository data.")
        }
    }
}

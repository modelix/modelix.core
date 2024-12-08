package org.modelix.model.server.handlers

import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.store.IRepositoryAwareStore
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoriesManagerTest {

    val store = spyk<IRepositoryAwareStore>(InMemoryStoreClient())
    private val repoManager = RepositoriesManager(store)

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
        repoManager.getTransactionManager().runWrite {
            initRepository(repoId)
            repoManager.removeBranches(repoId, setOf("master"))
        }
        val branches = repoManager.getTransactionManager().runRead { repoManager.getBranches(repoId) }
        assertTrue { branches.none { it.branchName == "master" } }
    }

    @Test
    fun `repository data is removed when removing repository`() = runTest {
        val repoId = RepositoryId("abc")
        repoManager.getTransactionManager().runWrite {
            initRepository(repoId)
            repoManager.removeRepository(repoId)
        }
        verify(exactly = 1) { store.removeRepositoryObjects(repoId) }
    }

    @Test
    fun `data of other repositories remains intact when removing a repository`() = runTest {
        val existingRepo = RepositoryId("existing")
        val toBeDeletedRepo = RepositoryId("tobedeleted")
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

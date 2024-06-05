package org.modelix.model.server.handlers

import kotlinx.coroutines.test.runTest
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.IsolatingStore
import org.modelix.model.server.store.LocalModelClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoriesManagerWithInMemoryStoreClientTest : RepositoriesManagerTest(InMemoryStoreClient())
class RepositoriesManagerWithIgniteStoreClientTest : RepositoriesManagerTest(IgniteStoreClient(inmemory = true))

abstract class RepositoriesManagerTest(val store: IsolatingStore) {
    protected val repoManager = RepositoriesManager(LocalModelClient(store))
    protected suspend fun initRepository(repoId: RepositoryId) {
        repoManager.createRepository(repoId, "testUser", useRoleIds = true, legacyGlobalStorage = false)
    }

    @AfterTest
    fun cleanup() {
        store.close()
    }

    @Test
    fun `repository data is removed when removing repository`() = runTest {
        val repoId = RepositoryId("abc")
        initRepository(repoId)

        fun getRepositoryData() = store.getAll().filterKeys { it.getRepositoryId() == repoId.id }

        val dataBeforeDeletion = getRepositoryData()
        repoManager.removeRepository(repoId)
        val dataAfterDeletion = getRepositoryData()

        assertTrue(dataBeforeDeletion.isNotEmpty(), "Expected data to be present before deletion.")
        assertTrue(dataAfterDeletion.isEmpty(), "Leftover repository data was found after deletion. entries=${dataAfterDeletion.size}")
    }

    @Test
    fun `data of other repositories remains intact when removing a repository`() = runTest {
        val existingRepo = RepositoryId("existing")
        val toBeDeletedRepo = RepositoryId("tobedeleted")
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

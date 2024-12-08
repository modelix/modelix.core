package org.modelix.model.server.store

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.modelix.model.lazy.RepositoryId
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryIsolatingStoreTest {

    private val store = InMemoryStoreClient()

    @Nested
    inner class RemoveRepositoryObjectsTest {
        @Test
        fun `repository data is removed successfully`() {
            val repoId = RepositoryId("abc")
            val entries = mapOf(
                ObjectInRepository(repoId.id, "key0") to "value0",
                ObjectInRepository(repoId.id, "key1") to "value1",
            )
            store.runWrite {
                store.putAll(entries)

                store.removeRepositoryObjects(repoId)
            }

            assertTrue { store.runRead { store.getAll() }.isEmpty() }
        }

        @Test
        fun `data of other repositories stays intact`() {
            val existingId = RepositoryId("existing")
            val existingEntries = mapOf(
                ObjectInRepository(existingId.id, "key0") to "value0",
                ObjectInRepository(existingId.id, "key1") to "value1",
            )
            store.runWrite { store.putAll(existingEntries) }

            val toDeleteId = RepositoryId("toDelete")
            val toDeleteEntries = mapOf(
                ObjectInRepository(toDeleteId.id, "key0") to "value0",
                ObjectInRepository(toDeleteId.id, "key1") to "value1",
            )
            store.runWrite {
                store.putAll(toDeleteEntries)
                store.removeRepositoryObjects(toDeleteId)
            }

            assertEquals(existingEntries, store.runRead { store.getAll() })
        }

        @Test
        fun `removing a non-existing repository does not throw`() {
            assertDoesNotThrow {
                store.runWrite { store.removeRepositoryObjects(RepositoryId("invalid")) }
            }
        }
    }

    @AfterTest
    fun cleanup() {
        store.close()
    }
}

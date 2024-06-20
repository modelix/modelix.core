/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.ObjectInRepository
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IgnitePostgresRepositoryRemovalTest {
    private val store = IgniteStoreClient()

    private val toDelete = RepositoryId("repository-removal-toDelete")
    private val existing = RepositoryId("repository-removal-existing")

    @AfterTest
    fun cleanup() {
        dbConnection.prepareStatement("DELETE FROM modelix.model WHERE repository IN ( ? , ? )").use {
            it.setString(1, toDelete.id)
            it.setString(2, existing.id)
        }
        store.putAll(store.getAll().keys.associateWith { null })
        store.close()
    }

    @Test
    fun `data is removed from the cache`() {
        val entries = mapOf(
            ObjectInRepository(toDelete.id, "key0") to "value0",
            ObjectInRepository(toDelete.id, "key1") to "value1",
        )
        store.putAll(entries)

        store.removeRepositoryObjects(toDelete)

        assertTrue { store.getAll(entries.keys).isEmpty() }
    }

    @Test
    fun `data is removed from the database`() {
        dbConnection.prepareStatement("INSERT INTO modelix.model (repository, key, value) VALUES (?, 'myKey', 'myValue')")
            .use {
                it.setString(1, toDelete.id)
                it.execute()
                check(it.updateCount == 1)
            }

        store.removeRepositoryObjects(toDelete)

        dbConnection.prepareStatement("SELECT * FROM modelix.model WHERE repository = ?").use {
            it.setString(1, toDelete.id)
            val result = it.executeQuery()
            assertFalse("Database contained leftover repository data.") { result.isBeforeFirst }
        }
    }

    @Test
    fun `removal does not affect other repository data in the database`() {
        dbConnection.prepareStatement("INSERT INTO modelix.model (repository, key, value) VALUES (?, 'myKey', 'myValue')")
            .use {
                it.setString(1, existing.id)
                it.execute()
                check(it.updateCount == 1)
                it.setString(1, toDelete.id)
                it.execute()
                check(it.updateCount == 1)
            }

        store.removeRepositoryObjects(toDelete)

        dbConnection.prepareStatement("SELECT * FROM modelix.model WHERE repository = ?").use {
            it.setString(1, existing.id)
            val result = it.executeQuery()
            assertTrue("Other repository data was removed from the database.") { result.isBeforeFirst }
        }
    }

    @Test
    fun `removal does not affect other repository data in the cache`() {
        val existingEntries = mapOf(
            ObjectInRepository(existing.id, "key0") to "value0",
            ObjectInRepository(existing.id, "key1") to "value1",
        )
        store.putAll(existingEntries)

        val toDeleteEntries = mapOf(
            ObjectInRepository(toDelete.id, "key0") to "value0",
            ObjectInRepository(toDelete.id, "key1") to "value1",
        )
        store.putAll(toDeleteEntries)
        store.removeRepositoryObjects(toDelete)

        assertEquals(existingEntries, store.getAll())
    }

    @Test
    fun `removing a non-existing repository does not throw`() {
        assertDoesNotThrow {
            store.removeRepositoryObjects(RepositoryId("invalid"))
        }
    }

    companion object {
        val dbConnection: Connection = DriverManager.getConnection(System.getProperty("jdbc.url"), "modelix", "modelix")

        @JvmStatic
        @AfterAll
        fun closeDbConnection() {
            dbConnection.close()
        }
    }
}

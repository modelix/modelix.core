package org.modelix.model.server.store

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.modelix.model.lazy.RepositoryId
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IgnitePostgresRepositoryRemovalTest {
    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var store: IgniteStoreClient
    private lateinit var dbConnection: Connection

    private val toDelete = RepositoryId("repository-removal-toDelete")
    private val existing = RepositoryId("repository-removal-existing")

    @BeforeTest
    fun beforeTest() {
        postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16.2-alpine"))
            .withDatabaseName("modelix")
            .withUsername("modelix")
            .withPassword("modelix")
            .withExposedPorts(5432)
        postgres.start()

        val jdbcProperties = Properties()
        jdbcProperties.setProperty("jdbc.url", "jdbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/")

        store = IgniteStoreClient(jdbcProperties)

        dbConnection = DriverManager.getConnection(System.getProperty("jdbc.url"), "modelix", "modelix")
    }

    @AfterTest
    fun afterTest() {
        store.dispose()
        postgres.stop()
    }

    @Test
    fun `data is removed from the cache`() {
        val entries = mapOf(
            ObjectInRepository(toDelete.id, "key0") to "value0",
            ObjectInRepository(toDelete.id, "key1") to "value1",
        )
        @OptIn(RequiresTransaction::class)
        store.runWrite { store.putAll(entries) }

        @OptIn(RequiresTransaction::class)
        store.runWrite { store.removeRepositoryObjects(toDelete) }

        @OptIn(RequiresTransaction::class)
        assertTrue { store.runRead { store.getAll(entries.keys) }.isEmpty() }
    }

    @Test
    fun `data is removed from the database`() {
        dbConnection.prepareStatement("INSERT INTO modelix.model (repository, key, value) VALUES (?, 'myKey', 'myValue')")
            .use {
                it.setString(1, toDelete.id)
                it.execute()
                check(it.updateCount == 1)
            }

        @OptIn(RequiresTransaction::class)
        store.getTransactionManager().runWrite { store.removeRepositoryObjects(toDelete) }

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

        @OptIn(RequiresTransaction::class)
        store.runWrite { store.removeRepositoryObjects(toDelete) }

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
        @OptIn(RequiresTransaction::class)
        store.runWrite { store.putAll(existingEntries) }

        val toDeleteEntries = mapOf(
            ObjectInRepository(toDelete.id, "key0") to "value0",
            ObjectInRepository(toDelete.id, "key1") to "value1",
        )
        @OptIn(RequiresTransaction::class)
        store.runWrite { store.putAll(toDeleteEntries) }
        @OptIn(RequiresTransaction::class)
        store.runWrite { store.removeRepositoryObjects(toDelete) }

        @OptIn(RequiresTransaction::class)
        assertEquals(existingEntries, store.runRead { store.getAll() })
    }

    @Test
    fun `removing a non-existing repository does not throw`() {
        assertDoesNotThrow {
            @OptIn(RequiresTransaction::class)
            store.runWrite { store.removeRepositoryObjects(RepositoryId("invalid")) }
        }
    }
}

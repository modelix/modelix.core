package org.modelix.model.server.store

import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IgnitePostgresTest {
    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var store: IgniteStoreClient

    @BeforeTest
    fun beforeTest() {
        postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16.2-alpine"))
            .withDatabaseName("modelix")
            .withUsername("modelix")
            .withPassword("modelix")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("/legacy-database.sql"),
                "/docker-entrypoint-initdb.d/initdb.sql",
            )
            .withExposedPorts(5432)
        postgres.start()

        val jdbcProperties = Properties()
        jdbcProperties.setProperty("jdbc.url", "jdbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/")

        store = IgniteStoreClient(jdbcProperties)
    }

    @AfterTest
    fun afterTest() {
        store.dispose()
        postgres.stop()
    }

    @Test
    fun `can get values for multiple keys when Ignite has not cached the keys yet`() {
        // The actual keys are irrelevant for this test.
        // A fresh client will have no keys cached.
        val keys = listOf("zK4Y2*xIEWlYlQGJL2Va4Z0ESgpWgnSQcOmnPeqt34PA", "zxgZN*oLuudxsu42ppSEGnCib8LkrSvauQk2B6T7AW6o")
            .map { ObjectInRepository.global(it) }

        val values = store.getAll(keys)

        assertTrue(values.filterNotNull().isNotEmpty())
    }

    @Test
    fun `store immutable object in repository`() {
        val value = "immutable value " + System.nanoTime()
        val hash = HashUtil.sha256(value)
        val repositoryId = RepositoryId("repo1")
        val key = ObjectInRepository(repositoryId.id, hash)

        assertEquals(null, store.get(key))
        store.put(key, value)
        assertEquals(value, store.get(key))

        assertEquals(null, store.get(ObjectInRepository.global(hash)))
    }

    @Test
    fun `store immutable object in global storage`() {
        val value = "immutable value " + System.nanoTime()
        val hash = HashUtil.sha256(value)
        val key = ObjectInRepository.global(hash)

        assertEquals(null, store.get(key))
        store.put(key, value)
        assertEquals(value, store.get(key))
    }

    @Test
    fun `store mutable object in repository`() {
        val value = "mutable value " + System.nanoTime()
        val hash = "mutable key " + System.nanoTime()
        val repositoryId = RepositoryId("repo1")
        val key = ObjectInRepository(repositoryId.id, hash)

        assertEquals(null, store.get(key))
        store.put(key, value)
        assertEquals(value, store.get(key))

        assertEquals(null, store.get(ObjectInRepository.global(hash)))
    }

    @Test
    fun `store mutable object in global storage`() {
        val value = "mutable value " + System.nanoTime()
        val hash = "mutable key " + System.nanoTime()
        val key = ObjectInRepository.global(hash)

        assertEquals(null, store.get(key))
        store.put(key, value)
        assertEquals(value, store.get(key))
    }

    @Test
    fun `read mutable legacy entry`() {
        val key = ObjectInRepository.global(":v2:repositories")
        assertEquals("courses", store.get(key))
    }

    @Test
    fun `overwrite mutable legacy entry`() {
        val key = ObjectInRepository.global(":v2:repositories:courses:branches")
        assertEquals("master", store.get(key))
        store.put(key, "new value")
        assertEquals("new value", store.get(key))
    }

    @Test
    fun `delete overwritten mutable legacy entry`() {
        val key = ObjectInRepository.global(":v2:repositories:courses:branches:master")
        assertEquals("7fQeo*xrdfZuHZtaKhbp0OosarV5tVR8N3pW8JPkl7ZE", store.get(key))
        store.put(key, "new value")
        assertEquals("new value", store.get(key))
        store.put(key, null)
        assertEquals(null, store.get(key))
    }

    @Test
    fun `read immutable legacy entry`() {
        val key = ObjectInRepository.global("2YAqw*tJWjb2t2RMO8ypIvHn8QpHjwmIUdhdFygV9lUE")
        assertEquals("S/5/0/W7HBQ*K6ZbUt76ti7r2pTscLVsWd8oiqIOIsKTpQB8Aw", store.get(key))
    }
}

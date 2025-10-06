package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import mu.KotlinLogging
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.IRepositoryAwareStore
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.InMemoryStoreClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val LOG = KotlinLogging.logger { }

class InMemoryRepositoryForkTest : RepositoryForkTest() {
    override fun createStoreClient() = InMemoryStoreClient()
}

class PostgresRepositoryForkTest : RepositoryForkTest() {
    override fun createStoreClient() = IgniteStoreClient(jdbcProperties)

    private lateinit var jdbcProperties: Properties
    private lateinit var postgres: PostgreSQLContainer<*>

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

        jdbcProperties = Properties()
        jdbcProperties.setProperty("jdbc.url", "jdbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/")
    }

    @AfterTest
    fun afterTest() {
        postgres.stop()
    }
}

abstract class RepositoryForkTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        createStoreClient().use { store ->
            application {
                try {
                    installDefaultServerPlugins()
                    val repoManager = RepositoriesManager(store)
                    ModelReplicationServer(repoManager).init(this)
                    IdsApiImpl(repoManager).init(this)
                } catch (ex: Throwable) {
                    LOG.error("", ex)
                }
            }
            block()
        }
    }

    protected abstract fun createStoreClient(): IRepositoryAwareStore

    @Test
    fun `fork repository`() = runTest {
        val modelClient = createModelClient()
        val repositoryId = RepositoryId("my-repo")
        val initialVersion = modelClient.initRepository(repositoryId)
        val forkedRepository = modelClient.forkRepository(repositoryId)
        val forkedVersion = modelClient.pull(forkedRepository.getBranchReference(), null)

        assertEquals(initialVersion.getObjectHash(), forkedVersion.getObjectHash())
    }
}

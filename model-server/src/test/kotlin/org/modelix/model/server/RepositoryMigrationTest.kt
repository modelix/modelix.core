package org.modelix.model.server

import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeReference
import org.modelix.model.client.IdGenerator
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.client2.runWriteOnBranch
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.model.server.api.RepositoryConfig.NodeIdType
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.RequiresTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryMigrationTest {
    private val config = RepositoryConfig(
        nodeIdType = NodeIdType.INT64,
        modelId = "d9330ca8-2145-4d1f-9b50-8f5aed1804cf",
        repositoryId = "d9330ca8-2145-4d1f-9b50-8f5aed1804cf",
        repositoryName = "my-repo",
    )

    private val modelData = ModelData(
        root = NodeData(
            id = PNodeReference(config.modelId, ITree.ROOT_ID).serialize(),
            children = listOf(
                NodeData(
                    id = "id1",
                    concept = "concept1",
                    role = "role2",
                    references = mapOf("ref1" to "id2"),
                ),
                NodeData(
                    id = "id2",
                    concept = "concept2",
                    role = "role2",
                    references = mapOf("ref1" to "id1"),
                ),
            ),
        ),
    )

    // A repository configured with int64 IDs is expected to generate new IDs and store the provided ID in the
    // #originalRef# property.
    // language=json
    private val expectedImportData = NodeData.fromJson(
        """
        {
            "id": "modelix:d9330ca8-2145-4d1f-9b50-8f5aed1804cf/1",
            "children": [
                {
                    "id": "modelix:d9330ca8-2145-4d1f-9b50-8f5aed1804cf/1c800000001",
                    "concept": "concept1",
                    "role": "role2",
                    "properties": {
                        "#originalRef#": "id1"
                    },
                    "references": {
                        "ref1": "modelix:d9330ca8-2145-4d1f-9b50-8f5aed1804cf/1c800000002"
                    }
                },
                {
                    "id": "modelix:d9330ca8-2145-4d1f-9b50-8f5aed1804cf/1c800000002",
                    "concept": "concept2",
                    "role": "role2",
                    "properties": {
                        "#originalRef#": "id2"
                    },
                    "references": {
                        "ref1": "modelix:d9330ca8-2145-4d1f-9b50-8f5aed1804cf/1c800000001"
                    }
                }
            ],
            "properties": {
                "#originalRef#": "modelix:d9330ca8-2145-4d1f-9b50-8f5aed1804cf/1"
            }
        }
        """,
    ).toJson()

    // Helper functions to reduce duplication
    @OptIn(RequiresTransaction::class)
    private fun createRepositoryWithData(
        repositoryManager: RepositoriesManager,
        config: RepositoryConfig,
    ): CLVersion {
        return repositoryManager.getTransactionManager().runWrite {
            val emptyVersion = repositoryManager.createRepository(config, null)
            val versionWithData = emptyVersion.runWrite(IdGenerator.newInstance(456), author = null) {
                modelData.load(it)
            }!! as CLVersion
            repositoryManager.mergeChanges(RepositoryId(config.repositoryId).getBranchReference(), versionWithData.getContentHash())
            versionWithData
        }
    }

    @OptIn(RequiresTransaction::class)
    private fun migrateRepository(
        repositoryManager: RepositoriesManager,
        newConfig: RepositoryConfig,
    ): CLVersion {
        return repositoryManager.getTransactionManager().runWrite {
            repositoryManager.migrateRepository(newConfig, null)
            repositoryManager.getVersion(RepositoryId(newConfig.repositoryId).getBranchReference())!!
        }
    }

    @OptIn(RequiresTransaction::class)
    private fun getRepositoryConfig(
        repositoryManager: RepositoriesManager,
        repositoryId: RepositoryId,
        branchRef: BranchReference,
    ): RepositoryConfig {
        return repositoryManager.getTransactionManager().runRead {
            repositoryManager.getConfig(repositoryId, branchRef)
        }
    }

    private fun extractNodeData(version: CLVersion): String {
        return version.getModelTree().asModelSingleThreaded().getRootNode().asData().toJson()
    }

    @Test
    fun `migrate int64 to string IDs`() = runTest {
        val repositoryManager = RepositoriesManager(InMemoryStoreClient())

        val version1 = createRepositoryWithData(repositoryManager, config)
        assertEquals(expectedImportData, extractNodeData(version1))

        val version2 = migrateRepository(repositoryManager, config.copy(nodeIdType = NodeIdType.STRING))
        // After migration the repository should use the IDs that were provided in the import data.
        assertEquals(modelData.root.toJson(), extractNodeData(version2))
    }

    @Test
    fun `migrate int64 to string IDs via client`() = testApplication {
        application {
            try {
                installDefaultServerPlugins()
                val repoManager = RepositoriesManager(InMemoryStoreClient())
                ModelReplicationServer(repoManager).init(this)
                IdsApiImpl(repoManager).init(this)
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }

        val repositoryId = RepositoryId(config.repositoryId)
        val modelClient = ModelClientV2.builder().url("http://localhost/v2").client(client).build().also { it.init() }
        modelClient.initRepository(config)
        modelClient.runWriteOnBranch(repositoryId.getBranchReference()) {
            modelData.load(it)
        }

        modelClient.changeRepositoryConfig(modelClient.getRepositoryConfig(repositoryId).copy(nodeIdType = NodeIdType.STRING))
        val version2 = modelClient.pull(
            repositoryId.getBranchReference(),
            null,
            filter = ObjectDeltaFilter(
                knownVersions = emptySet(),
                includeOperations = false,
                includeHistory = false,
                includeTrees = true,
            ),
        )

        // After migration the repository should use the IDs that were provided in the import data.
        assertEquals(modelData.root.toJson(), version2.getModelTree().asModelSingleThreaded().getRootNode().asData().toJson())
    }

    @Test
    fun `migrate global to isolated storage`() = runTest {
        val repositoryManager = RepositoriesManager(InMemoryStoreClient())
        val repositoryId = RepositoryId(config.repositoryId)
        val branchRef = repositoryId.getBranchReference()

        // Create repository with global storage (legacyGlobalStorage = true)
        val globalConfig = config.copy(legacyGlobalStorage = true)
        createRepositoryWithData(repositoryManager, globalConfig)

        // Verify initial config has global storage
        val configBeforeMigration = getRepositoryConfig(repositoryManager, repositoryId, branchRef)
        assertEquals(true, configBeforeMigration.legacyGlobalStorage, "Repository should start with global storage")

        // Migrate to isolated storage
        val version2 = migrateRepository(repositoryManager, globalConfig.copy(legacyGlobalStorage = false))

        // Verify config after migration has isolated storage
        val configAfterMigration = getRepositoryConfig(repositoryManager, repositoryId, branchRef)
        assertEquals(false, configAfterMigration.legacyGlobalStorage, "Repository should have isolated storage after migration")

        // Verify data is preserved after migration
        assertEquals(expectedImportData, extractNodeData(version2), "Data should be preserved after migration")
    }

    @Test
    fun `migrate int64 to string IDs and global to isolated storage simultaneously`() = runTest {
        val repositoryManager = RepositoriesManager(InMemoryStoreClient())
        val repositoryId = RepositoryId(config.repositoryId)
        val branchRef = repositoryId.getBranchReference()

        // Create repository with int64 IDs and global storage
        val initialConfig = config.copy(nodeIdType = NodeIdType.INT64, legacyGlobalStorage = true)
        val version1 = createRepositoryWithData(repositoryManager, initialConfig)

        // Verify initial configuration
        val configBeforeMigration = getRepositoryConfig(repositoryManager, repositoryId, branchRef)
        assertEquals(NodeIdType.INT64, configBeforeMigration.nodeIdType, "Repository should start with INT64 IDs")
        assertEquals(true, configBeforeMigration.legacyGlobalStorage, "Repository should start with global storage")

        // Verify initial data has generated IDs
        assertEquals(expectedImportData, extractNodeData(version1))

        // Migrate both: int64 -> string IDs AND global -> isolated storage
        val targetConfig = config.copy(nodeIdType = NodeIdType.STRING, legacyGlobalStorage = false)
        val version2 = migrateRepository(repositoryManager, targetConfig)

        // Verify config after migration
        val configAfterMigration = getRepositoryConfig(repositoryManager, repositoryId, branchRef)
        assertEquals(NodeIdType.STRING, configAfterMigration.nodeIdType, "Repository should have STRING IDs after migration")
        assertEquals(false, configAfterMigration.legacyGlobalStorage, "Repository should have isolated storage after migration")

        // After migration the repository should use the original IDs from import data
        assertEquals(modelData.root.toJson(), extractNodeData(version2), "Data should use original IDs after migration")
    }
}

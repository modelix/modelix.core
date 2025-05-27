package org.modelix.model.server

import kotlinx.coroutines.test.runTest
import org.modelix.model.api.IPropertyReference
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.runWriteWithNode
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.server.api.RepositoryConfig
import org.modelix.model.server.api.RepositoryConfig.NodeIdType
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.RequiresTransaction
import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryConfigTest {

    @Test
    fun `named based roles`() = runTest {
        val repositoryManager = RepositoriesManager(InMemoryStoreClient())

        @OptIn(RequiresTransaction::class)
        val version1 = repositoryManager.getTransactionManager().runWrite {
            repositoryManager.createRepository(
                RepositoryConfig(
                    legacyNameBasedRoles = true,
                    legacyGlobalStorage = false,
                    nodeIdType = NodeIdType.STRING,
                    primaryTreeType = RepositoryConfig.TreeType.PATRICIA_TRIE,
                    modelId = "d9330ca8-2145-4d1f-9b50-8f5aed1804cf",
                    repositoryId = "d9330ca8-2145-4d1f-9b50-8f5aed1804cf",
                    repositoryName = "my-repo",
                    alternativeNames = emptySet(),
                ),
                null,
            )
        }

        val version2 = version1.runWriteWithNode(IdGenerator.newInstance(1), null) {
            it.setPropertyValue(IPropertyReference.fromIdAndName("1234", "myRole"), "myValue")
        }

        val rootNode = version2.getModelTree().asModelSingleThreaded().getRootNode()
        val property = rootNode.getAllProperties().single()
        assertEquals("myValue", property.second)
        assertEquals("myRole", property.first.stringForLegacyApi())
    }

    @Test
    fun `id based roles`() = runTest {
        val repositoryManager = RepositoriesManager(InMemoryStoreClient())

        @OptIn(RequiresTransaction::class)
        val version1 = repositoryManager.getTransactionManager().runWrite {
            repositoryManager.createRepository(
                RepositoryConfig(
                    legacyNameBasedRoles = false,
                    legacyGlobalStorage = false,
                    nodeIdType = NodeIdType.STRING,
                    primaryTreeType = RepositoryConfig.TreeType.PATRICIA_TRIE,
                    modelId = "d9330ca8-2145-4d1f-9b50-8f5aed1804cf",
                    repositoryId = "d9330ca8-2145-4d1f-9b50-8f5aed1804cf",
                    repositoryName = "my-repo",
                    alternativeNames = emptySet(),
                ),
                null,
            )
        }

        val version2 = version1.runWriteWithNode(IdGenerator.newInstance(1), null) {
            it.setPropertyValue(IPropertyReference.fromIdAndName("1234", "myRole"), "myValue")
        }

        val rootNode = version2.getModelTree().asModelSingleThreaded().getRootNode()
        val property = rootNode.getAllProperties().single()
        assertEquals("myValue", property.second)
        assertEquals("1234", property.first.stringForLegacyApi())
    }
}

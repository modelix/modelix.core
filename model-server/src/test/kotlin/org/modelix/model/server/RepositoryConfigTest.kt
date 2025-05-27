package org.modelix.model.server

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import org.modelix.datastructures.model.addNewChild
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.ITree
import org.modelix.model.api.NodeReference
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.runWriteOnModel
import org.modelix.model.lazy.runWriteWithNode
import org.modelix.model.mutable.ModelixIdGenerator
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

    @Test
    fun `INT64 IDs and HASH_ARRAY_MAPPED_TRIE`() = runInt64Test { it.copy(primaryTreeType = RepositoryConfig.TreeType.HASH_ARRAY_MAPPED_TRIE) }

    @Test
    fun `INT64 IDs and PATRICIA_TRIE`() = runInt64Test { it.copy(primaryTreeType = RepositoryConfig.TreeType.PATRICIA_TRIE) }

    private fun runInt64Test(configOverride: (RepositoryConfig) -> RepositoryConfig) = runTest {
        val repositoryManager = RepositoriesManager(InMemoryStoreClient())

        @OptIn(RequiresTransaction::class)
        val version1 = repositoryManager.getTransactionManager().runWrite {
            repositoryManager.createRepository(
                RepositoryConfig(
                    legacyNameBasedRoles = false,
                    legacyGlobalStorage = false,
                    nodeIdType = NodeIdType.INT64,
                    primaryTreeType = RepositoryConfig.TreeType.HASH_ARRAY_MAPPED_TRIE,
                    modelId = "d9330ca8-2145-4d1f-9b50-8f5aed1804cf",
                    repositoryId = "d9330ca8-2145-4d1f-9b50-8f5aed1804cf",
                    repositoryName = "my-repo",
                    alternativeNames = emptySet(),
                ).let(configOverride),
                null,
            )
        }

        assertThrows<IllegalArgumentException>("Cannot access the node using the legacy API. Unsupported node ID type: myChildId") {
            val modelTree = version1.getModelTree()
            modelTree.addNewChild(
                modelTree.getRootNodeId(),
                IChildLinkReference.fromIdAndName("987", "myChild"),
                -1,
                NodeReference("myChildId"),
                ConceptReference("myConcept"),
            )
        }

        val version2 = version1.runWriteOnModel(ModelixIdGenerator(IdGenerator.newInstance(1), version1.getModelTree().getId()), null) {
            it.addNewChild(IChildLinkReference.fromIdAndName("987", "myChild"), -1, ConceptReference("myConcept"))
        }

        // The main reason to use INT64 IDs is to be compatible to the legacy API
        val tree = version2.getTree()
        val childId = tree.getAllChildren(ITree.ROOT_ID).single()
        assertEquals(0x100000001, childId)

        val childRef = version2.getModelTree().asModelSingleThreaded().getRootNode().getAllChildren().single().getNodeReference()
        assertEquals("modelix:d9330ca8-2145-4d1f-9b50-8f5aed1804cf/100000001", childRef.serialize())
    }
}

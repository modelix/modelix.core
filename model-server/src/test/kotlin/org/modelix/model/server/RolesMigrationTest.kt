package org.modelix.model.server

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import mu.KotlinLogging
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.client2.migrateRoles
import org.modelix.model.client2.runWriteOnTree
import org.modelix.model.data.ModelData
import org.modelix.model.data.asData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.mutable.load
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.Test
import kotlin.test.assertEquals

private val LOG = KotlinLogging.logger { }

class RolesMigrationTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            try {
                installDefaultServerPlugins()
                val repoManager = RepositoriesManager(InMemoryStoreClient())
                ModelReplicationServer(repoManager).init(this)
                IdsApiImpl(repoManager).init(this)
            } catch (ex: Throwable) {
                LOG.error("", ex)
            }
        }
        block()
    }

    @Test
    fun `migrate role names to role IDs`() = runTest {
        val client = createModelClient()

        val repositoryId = RepositoryId("repo1")
        val branchRef = repositoryId.getBranchReference()
        val initialVersion = client.initRepository(repositoryId, useRoleIds = false)

        val allRoles = listOf(
            IPropertyReference.fromIdAndName("p1", "myProperty"),
            IReferenceLinkReference.fromIdAndName("r1", "myReference"),
            IChildLinkReference.fromIdAndName("c1", "myChild"),
        )
        val rolesById = allRoles.associateBy { it.getUID()!! }
        val rolesByName = allRoles.associateBy { it.getSimpleName()!! }

        // language=json
        val modelData: ModelData = ModelData.fromJson(
            """
            {
              "root": {
                "id": "n001",
                "children": [
                  {
                    "id": "n002",
                    "concept": "MyConcept1",
                    "role": "myChild",
                    "children": [
                      {
                        "id": "n003",
                        "references": {
                          "myReference": "n004"
                        },
                        "properties": {
                          "myProperty": "myPropertyValue"
                        }
                      }
                    ]
                  },
                  {
                    "id": "n004"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        client.runWriteOnTree(branchRef) { modelData.load(it) }
        client.migrateRoles(branchRef) { oldRole ->
            rolesByName.getValue(oldRole.getNameOrId())
        }

        val versionWithIds = client.pull(
            branchRef,
            lastKnownVersion = null,
            filter = ObjectDeltaFilter(
                knownVersions = emptySet(),
                includeOperations = false,
                includeHistory = false,
                includeTrees = true,
            ),
        )

        val rootNodeWithIds = versionWithIds.getModelTree().asModelSingleThreaded().getRootNode()
        val actualJsonWithIds = rootNodeWithIds.asData().toJson()
        // language=json
        val expectedJsonWithIds = """
            {
              "root": {
                "id": "modelix:00000000-0000-0000-0000-000000000000/1",
                "children": [
                    {
                        "id": "n004"
                    },
                    {
                        "id": "n002",
                        "concept": "MyConcept1",
                        "role": "c1",
                        "children": [
                            {
                                "id": "n003",
                                "properties": {
                                    "p1": "myPropertyValue"
                                },
                                "references": {
                                    "r1": "n004"
                                }
                            }
                        ]
                    }
                ]
              }
            }
        """.let { ModelData.fromJson(it).root.copy(id = rootNodeWithIds.getNodeReference().serialize()).toJson() }

        assertEquals(expectedJsonWithIds, actualJsonWithIds)
    }
}

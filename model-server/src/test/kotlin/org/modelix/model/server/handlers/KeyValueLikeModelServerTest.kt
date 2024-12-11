package org.modelix.model.server.handlers

import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.createModelClient
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.streams.getSynchronous
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyValueLikeModelServerTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val store = InMemoryStoreClient()
        val repositoriesManager = RepositoriesManager(store)

        application {
            installDefaultServerPlugins()
            KeyValueLikeModelServer(repositoriesManager).init(this)
            ModelReplicationServer(repositoriesManager).init(this)
            IdsApiImpl(repositoriesManager).init(this)
        }

        block()
    }

    @Test
    fun `getRecursively returns transitive values`() = runTest {
        client.put {
            url {
                takeFrom("http://localhost/")
                appendPathSegments("put", "_N4rL*tula_QIYB-3If6bXDONEO5CnqBPrlURto-_j7k")
            }
            setBody("bar")
        }
        client.put {
            url {
                takeFrom("http://localhost/")
                appendPathSegments("put", "existingKey")
            }
            setBody("_N4rL*tula_QIYB-3If6bXDONEO5CnqBPrlURto-_j7k")
        }
        val actual = client.get {
            url {
                takeFrom("http://localhost/")
                appendPathSegments("getRecursively", "existingKey")
            }
        }.bodyAsText().let { Json.decodeFromString<JsonElement>(it) }

        val expected =
            Json.decodeFromString<JsonElement>(
                """
                [
                  {"key": "existingKey", "value": "_N4rL*tula_QIYB-3If6bXDONEO5CnqBPrlURto-_j7k"},
                  {"key": "_N4rL*tula_QIYB-3If6bXDONEO5CnqBPrlURto-_j7k", "value": "bar"}
              ]
            """,
            )
        assertEquals(expected, actual)
    }

    @Test
    fun `model client V1 can merge versions`() = runTest {
        val clientV1 = RestWebModelClient(baseUrl = "http://localhost/", providedClient = client)
        val clientV2 = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val branchA = repositoryId.getBranchReference("branchA")
        val branchB = repositoryId.getBranchReference("branchB")
        clientV2.initRepositoryWithLegacyStorage(repositoryId)
        clientV2.runWrite(branchA) { rootNode ->
            rootNode.setPropertyValue("propertyA", "valueA")
        }
        clientV2.runWrite(branchB) { rootNode ->
            rootNode.setPropertyValue("propertyB", "valueB")
        }
        val branchBHash = clientV2.pullHash(branchB)

        clientV1.putA(branchA.getKey(), branchBHash)

        val branchAVersion = clientV2.pull(branchA, null) as CLVersion
        assertTrue(branchAVersion.isMerge())
    }

    @Test
    fun `model client V1 can run a bulk query`() = runTest {
        val clientV1 = RestWebModelClient(baseUrl = "http://localhost/", providedClient = client)
        val clientV2 = createModelClient()
        val repositoryId = RepositoryId("repo1")
        val version = clientV2.initRepositoryWithLegacyStorage(repositoryId) as CLVersion
        val treeHash = checkNotNull(version.treeHash) { "Tree has should be loaded." }

        val bulkQuery = clientV1.storeCache.newBulkQuery()
        val bulkQueryValue = bulkQuery.query(treeHash)
        val bulkQueryResult = bulkQueryValue.getSynchronous()

        assertNotNull(bulkQueryResult)
    }
}

package org.modelix.model.server.handlers.ui

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.ITree
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.addNewChild
import org.modelix.model.client.successful
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.createModelClient
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.handlers.getLegacyObjectStore
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class ContentExplorerTest {

    private val repoManager = RepositoriesManager(InMemoryStoreClient())

    private fun runTest(body: suspend (ApplicationTestBuilder.() -> Unit)) = testApplication {
        application {
            installDefaultServerPlugins(unitTestMode = true)
            ModelReplicationServer(repoManager).init(this)
            ContentExplorer(repoManager).init(this)
            IdsApiImpl(repoManager).init(this)
        }
        body()
    }

    private fun ApplicationTestBuilder.createHttpClient() = createClient {
        install(ClientContentNegotiation) { json() }
    }

    @Test
    fun `node inspector finds root node`() = runTest {
        val client = createHttpClient()

        val delta: VersionDelta = client.post("/v2/repositories/node-inspector/init").body()

        val versionHash = delta.versionHash
        val version = CLVersion.loadFromHash(versionHash, repoManager.getLegacyObjectStore(RepositoryId("node-inspector")))
        val nodeId = ITree.ROOT_ID

        val response = client.get("/content/repositories/node-inspector/versions/$versionHash/$nodeId/")
        assertTrue(response.successful)
    }

    @Test
    fun `node inspector can handle unresolvable references`() = runTest {
        val modelClient = createModelClient()
        val repoId = RepositoryId("node-inspector-null-ref")
        val branchRef = repoId.getBranchReference("master")
        val refLinkName = "myUnresolvableRef"
        val refLinkTargetRef = NodeReference("notAResolvableId")

        modelClient.initRepository(repoId)

        modelClient.runWrite(branchRef) { root ->
            root.setReferenceTarget(IReferenceLink.fromName(refLinkName), refLinkTargetRef)
        }

        val versionHash = modelClient.pullHash(branchRef)

        val response = client.get("/content/repositories/${repoId.id}/versions/$versionHash/${ITree.ROOT_ID}/")
        val html = Jsoup.parse(response.bodyAsText())
        val nameCell = html.selectXpath("""//td[text()="$refLinkName"]""").first() ?: error("table cell not found")
        val row = checkNotNull(nameCell.parent()) { "table row not found" }
        val targetNodeIdCell = row.allElements[2] // index 0 is the row itself and 1 the nameCell
        val targetRefCell = row.allElements[3]

        assertTrue(response.successful)
        assertEquals("null", targetNodeIdCell.text())
        assertEquals(refLinkTargetRef.serialize(), targetRefCell.text())
    }

    @Test
    fun `nodes can be expanded`() = runTest {
        val client = createHttpClient()

        val delta: VersionDelta = client.post("/v2/repositories/node-expansion/init").body()

        val versionHash = delta.versionHash
        val version = CLVersion.loadFromHash(versionHash, repoManager.getLegacyObjectStore(RepositoryId("node-expansion")))
        val nodeId = ITree.ROOT_ID

        val expandedNodes = ContentExplorerExpandedNodes(setOf(nodeId.toString()), false)

        val response = client.post("/content/repositories/node-expansion/versions/$versionHash/") {
            contentType(ContentType.Application.Json)
            setBody(expandedNodes)
        }

        val html = Jsoup.parse(response.bodyAsText())
        val root: Element? = html.body().firstElementChild()

        assertTrue { response.successful }
        assertNotNull(root)
        assertTrue { root.`is`(Evaluator.Tag("ul")) }
        assertTrue { root.`is`(Evaluator.Class("treeRoot")) }
        assertTrue { root.childrenSize() > 0 }
    }

    @Test
    fun `nodes can be expanded to`() = runTest {
        val client = createHttpClient()
        val branchRef = RepositoryId("node-expand-to-test").getBranchReference("master")
        client.post("/v2/repositories/${branchRef.repositoryId}/init")
        val modelClient = ModelClientV2.builder()
            .client(client)
            .url("/v2")
            .build().also { it.init() }

        modelClient.use {
            val newestChild = modelClient.runWrite(branchRef) { rootNode ->
                rootNode.addNewChild(null)
                    .addNewChild(null)
                    .addNewChild(null)
            }
            val versionHash = modelClient.pullHash(branchRef)
            val expandToId = (newestChild as PNodeAdapter).nodeId

            val response = client.get("/content/repositories/${branchRef.repositoryId}/versions/$versionHash/") {
                parameter("expandTo", expandToId)
            }
            response shouldHaveStatus HttpStatusCode.OK
            response.bodyAsText() shouldContain expandToId.toString()
        }
    }

    @Test
    fun `expanding to non-existing node leads to not found response`() = runTest {
        val client = createHttpClient()
        val nodeId = "654321"

        val delta: VersionDelta = client.post("/v2/repositories/node-expand-to/init").body()
        val versionHash = delta.versionHash
        val response = client.get("/content/repositories/node-expand-to/versions/$versionHash/?expandTo=$nodeId")

        response shouldHaveStatus HttpStatusCode.NotFound
        response.bodyAsText() shouldContain nodeId
    }

    @Test
    fun `illegal expandTo value leads to bad request response`() = runTest {
        val client = createHttpClient()
        val nodeId = "illegalId"

        val delta: VersionDelta = client.post("/v2/repositories/node-expand-to/init").body()
        val versionHash = delta.versionHash
        val response = client.get("/content/repositories/node-expand-to/versions/$versionHash/?expandTo=$nodeId")

        response shouldHaveStatus HttpStatusCode.BadRequest
    }
}

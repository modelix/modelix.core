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

package org.modelix.model.server.handlers

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.ITree
import org.modelix.model.api.NodeReferenceById
import org.modelix.model.client.successful
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.createModelClient
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forContextRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class ContentExplorerTest {

    private val modelClient = LocalModelClient(InMemoryStoreClient().forContextRepository())
    private val repoManager = RepositoriesManager(modelClient)

    private fun runTest(body: suspend (ApplicationTestBuilder.() -> Unit)) = testApplication {
        application {
            installDefaultServerPlugins()
            ModelReplicationServer(repoManager).init(this)
            ContentExplorer(modelClient, repoManager).init(this)
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
        val version = CLVersion.loadFromHash(versionHash, modelClient.storeCache)
        val nodeId = checkNotNull(version.getTree().root?.id)

        val response = client.get("/content/repositories/node-inspector/versions/$versionHash/$nodeId/")
        assertTrue(response.successful)
    }

    @Test
    fun `node inspector can handle unresolvable references`() = runTest {
        val modelClient = createModelClient()
        val repoId = RepositoryId("node-inspector-null-ref")
        val branchRef = repoId.getBranchReference("master")
        val refLinkName = "myUnresolvableRef"
        val refLinkTargetRef = NodeReferenceById("notAResolvableId")

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
        val version = CLVersion.loadFromHash(versionHash, modelClient.storeCache)
        val nodeId = checkNotNull(version.getTree().root?.id)

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
}

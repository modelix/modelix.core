/*
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

package org.modelix.model.server

import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import org.json.JSONObject
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.ITree
import org.modelix.model.server.handlers.DeprecatedLightModelServer
import org.modelix.model.server.handlers.asObjectList
import org.modelix.model.server.handlers.buildJSONArray
import org.modelix.model.server.handlers.buildJSONObject
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class JsonAPITest {
    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(WebSockets)
            install(Resources)
            install(IgnoreTrailingSlash)
            DeprecatedLightModelServer(LocalModelClient(InMemoryStoreClient())).init(this)
        }
        block()
    }
    val repoId = "myrepo"

    @Test
    fun createNewRepo() = runTest {
        val response = client.post("/json/$repoId/init")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEmptyVersion(JSONObject(response.bodyAsText()))
    }

    @Test
    fun connectToWebsocket() = runTest {
        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }
        try {
            client.webSocketSession("ws://localhost/json/$repoId/ws")
        } catch (e: Exception) {
            fail("Could not connect to WebSocket", e)
        }
    }

    private fun assertEmptyVersion(json: JSONObject) {
        assertEquals(json.getJSONObject("root").getString("nodeId").toLong(), 1L)
        assertEquals(json.getString("repositoryId"), repoId)
        assertNotNull(json.optString("versionHash"), "versionHash missing")
    }

    @Test
    fun getByVersionHash() = runTest {
        val versionHash = JSONObject(client.post("/json/$repoId/init").assertOK().bodyAsText()).getString("versionHash")
        val response = client.get("/json/$repoId/$versionHash/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEmptyVersion(JSONObject(response.bodyAsText()))
    }

    private suspend fun ApplicationTestBuilder.getCurrentVersion(): JSONObject {
        val response = client.get("/json/$repoId/")
        val bodyAsText = response.bodyAsText()
        println(bodyAsText)
        assertEquals(HttpStatusCode.OK, response.status)
        return JSONObject(bodyAsText)
    }

    private suspend fun ApplicationTestBuilder.initVersion(): JSONObject {
        val response = client.post("/json/$repoId/init").assertOK()
        return JSONObject(response.bodyAsText())
    }

    fun HttpResponse.assertOK(): HttpResponse {
        assertEquals(HttpStatusCode.OK, status)
        return this
    }

    private suspend fun ApplicationTestBuilder.getCurrentVersionHash(): String {
        return getCurrentVersion().getString("versionHash")
    }

    @Test
    fun generateIds() = runTest {
        val quantity = 100
        val response = client.post("/json/generate-ids?quantity=$quantity")
        assertEquals(HttpStatusCode.OK, response.status)
        val jsonRange = JSONObject(response.bodyAsText())
        val range = jsonRange.let { LongRange(it.getLong("first"), it.getLong("last")) }
        assertEquals(quantity, range.count())
    }

    @Test
    fun createNodes() = runTest {
        val (id, version) = createNode(initVersion().getString("versionHash"), ITree.ROOT_ID, "entities", null) {
            put("name", "EntityA")
        }
        val entityJson = version.getJSONArray("nodes").asObjectList().first { it.getString("nodeId") != "1" }
        assertEquals(id, entityJson.getString("nodeId").toLong())
    }

    @Test
    fun concurrentPropertyChange() = runTest {
        val (nodeId, v1json) = createNode(initVersion().getString("versionHash"), ITree.ROOT_ID, "entities", null) {
            put("name", "EntityA")
        }
        val v1 = v1json.getString("versionHash")
        val queryAndAssert: suspend (String, String?) -> Unit = { role, expectedValue ->
            val merged = getCurrentVersion()
            val entity = getFirstEntity(merged)
            assertEquals(expectedValue, entity.getJSONObject("properties").getString(role))
        }
        changeNode(v1, nodeId, "name", "EntityB")
        changeNode(v1, nodeId, "color", "black")
        queryAndAssert("name", "EntityB")
        queryAndAssert("color", "black")

        changeNode(v1, nodeId, "name", "EntityC")
        queryAndAssert("name", "EntityC")
        queryAndAssert("color", "black")
    }

    @Test
    fun concurrentInsert() = runTest {
        val (nodeId, v1json) = createNode(initVersion().getString("versionHash"), ITree.ROOT_ID, "entities", null) {
            put("name", "EntityA")
        }
        val v1 = v1json.getString("versionHash")
        val queryAndAssert: suspend (String, String?) -> Unit = { role, expectedValue ->
            val merged = getCurrentVersion()
            val entity = getFirstEntity(merged)
            assertEquals(expectedValue, entity.getJSONObject("properties").getString(role))
        }
        val v2a = createNode(v1, nodeId, "properties", null) {
            put("name", "propertyA1")
        }
        val v2b = createNode(v1, nodeId, "properties", null) {
            put("name", "propertyA2")
        }
        val v3 = getCurrentVersion()
        assertEquals(2, getFirstEntity(v3).getJSONObject("children").getJSONArray("properties").length())
    }

    private suspend fun ApplicationTestBuilder.changeNode(versionHash: String, id: Long, role: String, value: String) {
        val response = client.post("/json/$repoId/$versionHash/update") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJSONArray(
                    buildJSONObject {
                        put("nodeId", id.toString())
                        put(
                            "properties",
                            buildJSONObject {
                                put(role, value)
                            },
                        )
                    },
                ).toString(2),
            )
        }
    }

    private suspend fun ApplicationTestBuilder.createNode(baseVersionHash: String, parentId: Long, role: String?, index: Int?, content: JSONObject.() -> Unit): Pair<Long, JSONObject> {
        val id = JSONObject(client.post("/json/generate-ids?quantity=1").bodyAsText()).getLong("first")
        val response = client.post("/json/$repoId/$baseVersionHash/update") {
            contentType(ContentType.Application.Json)
            val jsonString = buildJSONArray(
                buildJSONObject {
                    put("nodeId", id.toString())
                    put("parent", parentId)
                    put("role", role)
                    if (index != null) put("index", index)
                    put(
                        "properties",
                        buildJSONObject {
                            content(this)
                        },
                    )
                },
            ).toString(2)
            setBody(jsonString)
        }
        val responseBody = response.bodyAsText()
        println(responseBody)
        assertEquals(HttpStatusCode.OK, response.status)
        return id to JSONObject(responseBody)
    }

    private fun getFirstEntity(version: JSONObject) =
        version.getJSONObject("root").getJSONObject("children").getJSONArray("entities").getJSONObject(0)
}

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

package org.modelix.model.server

import com.google.gson.JsonParser
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Test
import org.modelix.authorization.installAuthentication
import org.modelix.model.InMemoryModels
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forGlobalRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class V1ApiTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val inMemoryModels = InMemoryModels()
        val store = InMemoryStoreClient().forGlobalRepository()
        val localModelClient = LocalModelClient(store)
        val repositoriesManager = RepositoriesManager(localModelClient)

        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            KeyValueLikeModelServer(repositoriesManager, store, inMemoryModels).init(this)
        }

        block()
    }

    @Test
    fun `counter returns different ids for same key`() = runTest {
        val url = "/counter/a"

        val response1 = client.post(url)
        val response2 = client.post(url)

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(HttpStatusCode.OK, response2.status)
        assertNotEquals(response1.bodyAsText(), response2.bodyAsText())
    }

    @Test
    fun `events are received after subscription`() = runTest {
        val key = "dylandog"
        val value = "a comic book"

        coroutineScope {
            val deferred = async { client.get("/poll/$key") }
            client.put("/put/$key") {
                setBody(value)
            }
            val response = deferred.await()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(value, response.bodyAsText())
        }
    }

    @Test
    fun `events are only received for the subscribed key`() = runTest {
        val key = "dylandog"
        val value = "a comic book"

        coroutineScope {
            val deferred = async { client.get("/poll/$key") }
            client.put("/put/topolino") {
                setBody(value)
            }
            client.put("/put/$key") {
                setBody("someOtherValue")
            }

            val response = deferred.await()

            assertEquals(HttpStatusCode.OK, response.status)
            assertNotEquals(value, response.bodyAsText())
        }
    }

    @Test
    fun `default email after token is generated`() = runTest {
        val response = client.get("/getEmail")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("unit-tests@example.com", response.bodyAsText())
    }

    @Test
    fun `storing and retrieving`() = runTest {
        val key = "abc"
        val value = "qwerty6789"
        client.put("/put/$key") {
            setBody(value)
        }
        val response = client.get("/get/$key")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(value, response.bodyAsText())
    }

    @Test
    fun `retrieving unexisting key`() = runTest {
        val response = client.get("/get/abc")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `retrieving forbidden key`() = runTest {
        val response = client.get("/get/$$\$_abc")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `retrieving multiple keys - all existing`() = runTest {
        val entries = mapOf(
            "aaa" to "value1",
            "bbb" to "value2",
            "ccc" to "value3",
        )
        for ((key, value) in entries) {
            client.put("/put/$key") { setBody(value) }
        }

        val response = client.put("/getAll") {
            setBody("""["aaa", "bbb", "ccc"]""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        //language=json
        assertJsonEquals(
            """
            [
              {"value": "value1", "key": "aaa"},
              {"value": "value2", "key": "bbb"},
              {"value": "value3", "key": "ccc"}
            ]
            """.trimIndent(),
            response.bodyAsText(),
        )
    }

    @Test
    fun `retrieving multiple keys - some existing`() = runTest {
        val entries = mapOf(
            "aaa" to "value1",
            "ccc" to "value3",
        )
        for ((key, value) in entries) {
            client.put("/put/$key") { setBody(value) }
        }
        val response = client.put("/getAll") {
            setBody("""["aaa", "bbb", "ccc"]""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        //language=json
        assertJsonEquals(
            """
            [
              {"value": "value1", "key": "aaa"},
              {"key": "bbb"},
              {"value": "value3", "key": "ccc"}
            ]
            """.trimIndent(),
            response.bodyAsText(),
        )
    }

    @Test
    fun `retrieving multiple keys - none existing`() = runTest {
        val response = client.put("/getAll") {
            setBody("['aaa', 'bbb', 'ccc']")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        //language=json
        assertJsonEquals(
            """
            [
              {"key": "aaa"},
              {"key": "bbb"},
              {"key": "ccc"}
            ]
            """.trimIndent(),
            response.bodyAsText(),
        )
    }

    @Test
    fun `putting multiple keys - some nulls - stored correctly`() = runTest {
        client.put("/putAll") {
            //language=json
            setBody(
                """
                [
                  {"value": "value1", "key": "aaa"},
                  {"key": "bbb"},
                  {"value": "value3", "key": "ccc"}
                ]
                """.trimIndent(),
            )
        }

        val response = client.put("/getAll") {
            setBody("""["aaa", "bbb", "ccc"]""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        //language=json
        assertJsonEquals(
            """
            [
              {"value": "value1", "key": "aaa"},
              {"key": "bbb"},
              {"value": "value3", "key": "ccc"}
            ]
            """.trimIndent(),
            response.bodyAsText(),
        )
    }

    @Test
    fun `putting multiple keys - some nulls - recognized correctly`() = runTest {
        val response = client.put("/putAll") {
            //language=json
            setBody(
                """
                [
                  {"value": "value1", "key": "aaa"},
                  {"key": "bbb"},
                  {"value": "value3", "key": "ccc"}
                ]
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("3 entries written", response.bodyAsText())
    }

    @Test
    fun `putting multiple keys`() = runTest {
        client.put("/putAll") {
            //language=json
            setBody(
                """
                [
                  {"value": "value1", "key": "aaa"},
                  {"value": "value2", "key": "bbb"},
                  {"value": "value3", "key": "ccc"}
                ]
                """.trimIndent(),
            )
        }

        val response = client.put("/getAll") {
            setBody("""["aaa", "bbb", "ccc"]""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        //language=json
        assertJsonEquals(
            """
            [
              {"value": "value1", "key": "aaa"},
              {"value": "value2", "key": "bbb"},
              {"value": "value3", "key": "ccc"}
            ]
            """.trimIndent(),
            response.bodyAsText(),
        )
    }

    @Test
    fun `get recursively`() = runTest {
        client.put("/put/_N4rL*tula_QIYB-3If6bXDONEO5CnqBPrlURto-_j7k") {
            setBody("bar")
        }
        client.put("/put/existingKey") {
            setBody("_N4rL*tula_QIYB-3If6bXDONEO5CnqBPrlURto-_j7k")
        }
        val response = client.get("/getRecursively/existingKey")

        assertEquals(HttpStatusCode.OK, response.status)
        //language=json
        assertJsonEquals(
            """
            [
              {"value": "_N4rL*tula_QIYB-3If6bXDONEO5CnqBPrlURto-_j7k", "key": "existingKey"},
              {"key": "_N4rL*tula_QIYB-3If6bXDONEO5CnqBPrlURto-_j7k", "value": "bar"}
            ]
            """.trimIndent(),
            response.bodyAsText(),
        )
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual))
    }
}

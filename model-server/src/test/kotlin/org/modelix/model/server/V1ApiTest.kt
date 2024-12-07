package org.modelix.model.server

import com.google.gson.JsonParser
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.Test
import org.modelix.authorization.installAuthentication
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class V1ApiTest {

    private fun runApiTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val repositoriesManager = RepositoriesManager(InMemoryStoreClient())

        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            KeyValueLikeModelServer(repositoriesManager).init(this)
        }

        block()
    }

    @Test
    fun `counter returns different ids for same key`() = runApiTest {
        val url = "/counter/a"

        val response1 = client.post(url)
        val response2 = client.post(url)

        assertEquals(HttpStatusCode.OK, response1.status)
        assertEquals(HttpStatusCode.OK, response2.status)
        assertNotEquals(response1.bodyAsText(), response2.bodyAsText())
    }

    @Test
    fun `events are received after subscription`() = runApiTest {
        val key = "dylandog"
        val value = "a comic book"

        /*
        The Mutex setup ensures the following execution order:
          1. GET is sent
          2. PUT is executed
          3. GET response is received
         */
        val mutex = Mutex(locked = true)
        val mutexClient = createMutexClient(mutex)

        val deferred = mutexClient.async {
            mutexClient.get("/poll/$key")
        }
        mutex.lock()
        client.put("/put/$key") {
            setBody(value)
        }

        val response = deferred.await()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(value, response.bodyAsText())
    }

    @Test
    fun `events are only received for the subscribed key`() = runApiTest {
        val key = "dylandog"
        val value = "a comic book"

        /*
        The Mutex setup ensures the following execution order:
          1. GET is sent
          2. PUTs are executed
          3. GET response is received
         */
        val mutex = Mutex(locked = true)
        val mutexClient = createMutexClient(mutex)
        val deferred = mutexClient.async { mutexClient.get("/poll/$key") }
        mutex.lock()
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

    private fun ApplicationTestBuilder.createMutexClient(mutex: Mutex) =
        client.config {
            install(
                createClientPlugin("mutexUnlock") {
                    on(Send) { request ->
                        mutex.unlock()
                        proceed(request)
                    }
                },
            )
        }

    @Test
    fun `default email after token is generated`() = runApiTest {
        val response = client.get("/getEmail")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("unit-tests@example.com", response.bodyAsText())
    }

    @Test
    fun `value can be stored and retrieved`() = runApiTest {
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
    fun `retrieving non-existent key leads to not found`() = runApiTest {
        val response = client.get("/get/abc")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `retrieving forbidden key leads to forbidden`() = runApiTest {
        val response = client.get("/get/$$\$_abc")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `multiple existing keys can be retrieved`() = runApiTest {
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
    fun `multiple partially existing keys can be retrieved`() = runApiTest {
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
    fun `multiple nonexistent keys can be retrieved`() = runApiTest {
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
    fun `multiple keys with some null values are stored correctly`() = runApiTest {
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
    fun `multiple keys with some null values are recognized correctly`() = runApiTest {
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
    fun `multiple keys with non-null values are stored correctly`() = runApiTest {
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
    fun `keys can be retrieved recursively`() = runApiTest {
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

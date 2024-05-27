/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
import org.modelix.authorization.installAuthentication
import org.modelix.model.InMemoryModels
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.forGlobalRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyValueLikeModelServerTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val storeClient = InMemoryStoreClient()
        val modelClient = LocalModelClient(storeClient.forGlobalRepository())
        val repositoriesManager = RepositoriesManager(modelClient)
        val handler = KeyValueLikeModelServer(repositoriesManager, modelClient.store, InMemoryModels())

        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            handler.init(this)
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
}

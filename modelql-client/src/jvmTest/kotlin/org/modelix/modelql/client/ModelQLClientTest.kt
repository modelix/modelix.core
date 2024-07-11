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
package org.modelix.modelql.client

import io.ktor.client.HttpClient
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.withTimeout
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import org.modelix.modelql.core.IFluxUnboundQuery
import org.modelix.modelql.core.buildFluxQuery
import org.modelix.modelql.core.contains
import org.modelix.modelql.core.count
import org.modelix.modelql.core.filter
import org.modelix.modelql.core.first
import org.modelix.modelql.core.firstOrNull
import org.modelix.modelql.core.flatMap
import org.modelix.modelql.core.map
import org.modelix.modelql.core.notEqualTo
import org.modelix.modelql.core.plus
import org.modelix.modelql.core.toList
import org.modelix.modelql.core.toSet
import org.modelix.modelql.core.zip
import org.modelix.modelql.server.ModelQLServer
import org.modelix.modelql.untyped.addNewChild
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.allProperties
import org.modelix.modelql.untyped.allReferences
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.descendants
import org.modelix.modelql.untyped.nodeReference
import org.modelix.modelql.untyped.property
import org.modelix.modelql.untyped.remove
import org.modelix.modelql.untyped.setProperty
import org.modelix.modelql.untyped.setReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ModelQLClientTest {
    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        withTimeout(3.seconds) {
            application {
                val tree = CLTree(ObjectStoreCache(MapBaseStore()))
                val branch = PBranch(tree, IdGenerator.getInstance(1))
                val rootNode = branch.getRootNode()
                branch.runWrite {
                    val module1 = rootNode.addNewChild("modules", -1, null as IConceptReference?)
                    module1.setPropertyValue("name", "abc")
                    module1.setPropertyValue("description", "xyz")
                    val model1a = module1.addNewChild("models", -1, null as IConceptReference?)
                    model1a.setPropertyValue("name", "model1a")
                }
                routing {
                    ModelQLServer.builder(rootNode).build().installHandler(this)
                }
            }
            val httpClient = createClient {
            }
            block(httpClient)
        }
    }

    @Test
    fun test_count() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result: Int = client.query { root ->
            root.allChildren().count()
        }
        assertEquals(1, result)
    }

    @Test
    fun test_properties() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result: List<String?> = client.query { root ->
            root.children("modules").property("name").toList()
        }
        assertEquals(listOf("abc"), result)
    }

    @Test
    fun test_allProperties() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result: Set<Pair<IProperty, String?>> = client.query { root ->
            root.children("modules").allProperties().toSet()
        }
        assertEquals(
            setOf(
                IProperty.fromName("name") to "abc",
                IProperty.fromName("description") to "xyz",
            ),
            result,
        )
    }

    @Test
    fun test_zip() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result = client.query { root ->
            root.children("modules").map {
                it.property("name").zip(it.allChildren().nodeReference().toList())
            }.toList()
        }
    }

    @Test
    fun test_zipN() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val result = client.query { root ->
            root.children("modules").map {
                it.property("name").zip(
                    it.allChildren().nodeReference().toList(),
                    it.property("p1"),
                    it.property("p2"),
                    it.property("p3"),
                    it.property("p4"),
                ).firstOrNull()
            }.toList()
        }
    }

    @Test
    fun writeProperty() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val updatesNodes = client.query { root ->
            root.children("modules")
                .children("models").filter { it.property("name").contains("model1a") }
                .first()
                .map {
                    it.setProperty("name", "changed")
                    // test if non-consumed steps with side effects are executed
                    it
                }
                .property("name")
        }
        assertEquals("changed", updatesNodes)

        val newPropertyNames = client.query { root ->
            root.children("modules")
                .children("models")
                .property("name")
                .toSet()
        }
        assertEquals(setOf("changed"), newPropertyNames)
    }

    @Test
    fun writeReference() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val updatedNodes = client.query { root ->
            root.children("modules")
                .children("models").filter { it.property("name").contains("model1a") }
                .first()
                .setReference("someModule", root.children("modules").first())
        }

        val referenceTargetNames = client.query { root ->
            root.descendants(true)
                .allReferences()
                .property("name")
                .toSet()
        }
        assertEquals(setOf("abc"), referenceTargetNames)
    }

    @Test
    fun addNewChild() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        val createdNodes = client.query { root ->
            root.children("modules")
                .children("models")
                .first()
                .addNewChild("rootNodes")
                .setProperty("name", "MyRootNode")
        }

        val rootNodeNames = client.query { root ->
            root.children("modules")
                .children("models")
                .children("rootNodes")
                .property("name")
                .toSet()
        }
        assertEquals(setOf("MyRootNode"), rootNodeNames)
    }

    @Test
    fun removeNode() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()

        suspend fun countModels(): Int {
            return client.query { root ->
                root.children("modules")
                    .children("models")
                    .count()
            }
        }

        val oldNumberOfModels = countModels()

        val removedNumberOfNodes = client.query { root ->
            root.children("modules")
                .children("models")
                .remove()
        }
        assertEquals(1, removedNumberOfNodes)

        val newNumberOfModels = countModels()

        assertEquals(1, oldNumberOfModels - newNumberOfModels)
    }

    @Test
    fun recursiveQuery() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()

        val descendantsNames: IFluxUnboundQuery<INode, String?> = buildFluxQuery<INode, String?> {
            it.property("name") + it.allChildren().mapRecursive()
        }

        val result: Set<String?> = client.query { root ->
            root.flatMap(descendantsNames).toSet()
        }

        assertEquals(setOf(null, "abc", "model1a"), result)
    }

    @Test
    fun testCaching() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()

        val result: List<Int> = client.query { root ->
            val numberOfNodes = root.descendants()
                .filter { it.property("visited").notEqualTo("xxx") }
                .map { it.setProperty("visited", "xxx") }
                .count()
            numberOfNodes.map { it + 1000 }.plus(numberOfNodes.map { it + 2000 }).toList()
        }
        println(result)
        assertEquals(listOf(1002, 2002), result)
    }

    @Test
    fun `test IMonoStep nodeRefAndConcept`() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)

        val nullNode = client.getRootNode().getReferenceTarget("nonExistentReference")

        assertEquals(null, nullNode)
    }
}

package org.modelix.modelql.client

import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.assertThrows
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import org.modelix.modelql.core.IFluxUnboundQuery
import org.modelix.modelql.core.assertNotEmpty
import org.modelix.modelql.core.buildFluxQuery
import org.modelix.modelql.core.contains
import org.modelix.modelql.core.count
import org.modelix.modelql.core.filter
import org.modelix.modelql.core.first
import org.modelix.modelql.core.firstOrNull
import org.modelix.modelql.core.flatMap
import org.modelix.modelql.core.fold
import org.modelix.modelql.core.map
import org.modelix.modelql.core.memoize
import org.modelix.modelql.core.notEqualTo
import org.modelix.modelql.core.plus
import org.modelix.modelql.core.sum
import org.modelix.modelql.core.toList
import org.modelix.modelql.core.toSet
import org.modelix.modelql.core.zip
import org.modelix.modelql.server.ModelQLServer
import org.modelix.modelql.untyped.addNewChild
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.allReferences
import org.modelix.modelql.untyped.asMono
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.descendants
import org.modelix.modelql.untyped.nodeReference
import org.modelix.modelql.untyped.property
import org.modelix.modelql.untyped.remove
import org.modelix.modelql.untyped.resolve
import org.modelix.modelql.untyped.setProperty
import org.modelix.modelql.untyped.setReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ModelQLClientTest {
    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        withTimeout(30.seconds) {
            application {
                val tree = CLTree(createObjectStoreCache(MapBasedStore()))
                val branch = PBranch(tree, IdGenerator.getInstance(1))
                val rootNode = branch.getRootNode()
                branch.runWrite {
                    val module1 = rootNode.addNewChild("modules", -1, null as IConceptReference?)
                    module1.setPropertyValue("name", "abc")
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

    @Test
    fun `mono can be created from set of concept references`() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val refSet = setOf(ConceptReference("abc"), ConceptReference("def"))
        val result = client.query { refSet.asMono() }
        assertEquals(refSet, result)
    }

    @Test
    fun testRecursiveMemoization() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val result = client.query {
            it.memoize { n ->
                n.count()
                    .sum(
                        n.allChildren().fold(0) { acc, child ->
                            acc.sum(child.mapRecursive())
                        },
                    )
            }
        }
        assertEquals(3, result)
    }

    @Test
    fun `resolving a non-existing node reference returns 422`() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val ex = assertThrows<ModelQueryRequestException> {
            client.query<INode> {
                NodeReference("doesnotexist").asMono().resolve()
            }
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.httpResponse.status)
    }

    @Test
    fun `failed assertions in queries return 422`() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val ex = assertThrows<ModelQueryRequestException> {
            client.query<INode> {
                it.children(IChildLinkReference.fromName("test")).first().assertNotEmpty()
            }
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.httpResponse.status)
    }

    @Test
    fun `empty query results return 422`() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val ex = assertThrows<ModelQueryRequestException> {
            client.query<INode> {
                it.children(IChildLinkReference.fromName("test")).first()
            }
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.httpResponse.status)
    }
}

package org.modelix.modelql.client

import io.ktor.client.HttpClient
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.withTimeout
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.NodeWithModelQLSupport
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import org.modelix.model.server.light.LightModelServer
import org.modelix.modelql.core.UnboundQuery
import org.modelix.modelql.core.buildQuery
import org.modelix.modelql.core.contains
import org.modelix.modelql.core.count
import org.modelix.modelql.core.filter
import org.modelix.modelql.core.first
import org.modelix.modelql.core.firstOrNull
import org.modelix.modelql.core.map
import org.modelix.modelql.core.plus
import org.modelix.modelql.core.toList
import org.modelix.modelql.core.toSet
import org.modelix.modelql.core.zip
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.nodeReference
import org.modelix.modelql.untyped.property
import org.modelix.modelql.untyped.setProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ModelQLClientTest {
    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        withTimeout(3.seconds) {
            application {
                val tree = CLTree(ObjectStoreCache(MapBaseStore()))
                val branch = PBranch(tree, IdGenerator.getInstance(1))
                val rootNode = NodeWithModelQLSupport(branch.getRootNode())
                branch.runWrite {
                    val module1 = rootNode.addNewChild("modules", -1, null as IConceptReference?)
                    module1.setPropertyValue("name", "abc")
                    val model1a = module1.addNewChild("models", -1, null as IConceptReference?)
                    model1a.setPropertyValue("name", "model1a")
                }
                LightModelServer(80, rootNode).apply { installHandlers() }
            }
            val httpClient = createClient {
            }
            block(httpClient)
        }
    }

    @Test
    fun test_count() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val result: Int = client.query { root ->
            root.allChildren().count()
        }
        assertEquals(1, result)
    }

    @Test
    fun test_properties() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val result: List<String?> = client.query { root ->
            root.children("modules").property("name").toList()
        }
        assertEquals(listOf("abc"), result)
    }

    @Test
    fun test_zip() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val result = client.query { root ->
            root.children("modules").map {
                it.property("name").zip(it.allChildren().nodeReference().toList())
            }.toList()
        }
    }

    @Test
    fun test_zipN() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val result = client.query { root ->
            root.children("modules").map {
                it.property("name").zip(
                    it.allChildren().nodeReference().toList(),
                    it.property("p1"),
                    it.property("p2"),
                    it.property("p3"),
                    it.property("p4")
                ).firstOrNull()
            }.toList()
        }
    }

    @Test
    fun writeProperty() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)
        val updatesNodes = client.query { root ->
            root.children("modules")
                .children("models").filter { it.property("name").contains("model1a") }
                .first()
                .setProperty("name", "changed")
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
    fun recursiveQuery() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)

        val descendantsNames: UnboundQuery<INode, String?> = buildQuery<INode, String?> {
            it.property("name") + it.allChildren().mapRecursive()
        }

        val result: Set<String?> = client.query { root ->
            root.map(descendantsNames).toSet()
        }

        assertEquals(setOf(null, "abc", "model1a"), result)
    }
}

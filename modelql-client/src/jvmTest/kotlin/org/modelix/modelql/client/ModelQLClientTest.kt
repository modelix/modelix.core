package org.modelix.modelql.client

import io.ktor.client.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import org.modelix.model.server.light.LightModelServer
import org.modelix.modelql.core.*
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.nodeReference
import org.modelix.modelql.untyped.property
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelQLClientTest {
    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            val tree = CLTree(ObjectStoreCache(MapBaseStore()))
            val branch = PBranch(tree, IdGenerator.getInstance(1))
            val rootNode = branch.getRootNode()
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
}
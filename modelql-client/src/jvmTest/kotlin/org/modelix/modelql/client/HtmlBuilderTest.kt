package org.modelix.modelql.client

import io.ktor.client.HttpClient
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.withTimeout
import kotlinx.html.FlowContent
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.html
import kotlinx.html.stream.createHTML
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
import org.modelix.modelql.core.AsyncBuilder
import org.modelix.modelql.core.IAsyncBuilder
import org.modelix.modelql.core.mapLocal
import org.modelix.modelql.untyped.buildQuery
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.property
import org.modelix.modelql.untyped.query
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class HtmlBuilderTest {
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
                    val model1b = module1.addNewChild("models", -1, null as IConceptReference?)
                    model1b.setPropertyValue("name", "model1b")
                }
                LightModelServer(80, rootNode).apply { installHandlers() }
            }
            val httpClient = createClient {
            }
            block(httpClient)
        }
    }

    @Test
    fun modular() = runTest { httpClient ->
        val client = ModelQLClient("http://localhost/query", httpClient)

        fun HtmlBuilder<INode>.renderModel() {
            val name = input.property("name").getLater()
            onSuccess {
                div {
                    h2 {
                        +"Model: ${name.get()}"
                    }
                }
            }
        }

        fun HtmlBuilder<INode>.renderModule() {
            val name = input.property("name").getLater()
            val models = input.children("models").iterateLater {
                renderModel()
            }
            onSuccess {
                div {
                    h1 {
                        +"Module: ${name.get()}"
                    }
                    iterate(models)
                }
            }
        }

        fun HtmlBuilder<INode>.renderRepository() {
            val modules = input.children("modules").iterateLater {
                renderModule()
            }
            onSuccess {
                iterate(modules)
            }
        }

        val actual = client.getRootNode().queryAndBuildHtml {
            renderRepository()
        }
        val expected = """<html><body><div><h1>Module: abc</h1><div><h2>Model: model1a</h2></div><div><h2>Model: model1b</h2></div></div></body></html>"""
        assertEquals(expected, actual)
    }
}

typealias HtmlBuilder<In> = IAsyncBuilder<In, FlowContent>

suspend fun INode.queryAndBuildHtml(body: HtmlBuilder<INode>.() -> Unit): String {
    val query = buildQuery<String> { repository ->
        val htmlBuilder = AsyncBuilder<INode, FlowContent>(repository)
        htmlBuilder.apply(body)
        htmlBuilder.compileOutputStep().mapLocal { result ->
            createHTML(prettyPrint = false).html {
                body {
                    htmlBuilder.apply {
                        processResult(result)
                    }
                }
            }
        }
    }
    println("query: $query")
    return query.execute()
}

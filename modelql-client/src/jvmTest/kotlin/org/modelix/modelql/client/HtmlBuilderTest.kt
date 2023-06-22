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
import org.modelix.modelql.core.ResultHandler
import org.modelix.modelql.core.mapLocal
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
            val name = node.property("name").getLater()
            buildHtml {
                div {
                    h2 {
                        +"Model: ${name.get()}"
                    }
                }
            }
        }

        fun HtmlBuilder<INode>.renderModule() {
            val name = node.property("name").getLater()
            val models = node.children("models").iterateLater {
                renderModel()
            }
            buildHtml {
                div {
                    h1 {
                        +"Module: ${name.get()}"
                        iterate(models)
                    }
                }
            }
        }

        fun HtmlBuilder<INode>.renderRepository() {
            val modules = node.children("modules").iterateLater {
                renderModule()
            }
            buildHtml {
                iterate(modules)
            }
        }

        val actual = client.getRootNode().queryAndBuildHtml {
            renderRepository()
        }
        val expected = """<html><body><div><h1>Module: abc<div><h2>Model: model1a</h2></div></h1></div></body></html>"""
        assertEquals(expected, actual)
    }
}

typealias HtmlBuilder<In> = IAsyncBuilder<In, FlowContent>
fun <In> HtmlBuilder<In>.buildHtml(body: ResultHandler<FlowContent>) = onSuccess(body)
val HtmlBuilder<INode>.node get() = input

suspend fun INode.queryAndBuildHtml(body: HtmlBuilder<INode>.() -> Unit): String {
    return query<String> { repository ->
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
}

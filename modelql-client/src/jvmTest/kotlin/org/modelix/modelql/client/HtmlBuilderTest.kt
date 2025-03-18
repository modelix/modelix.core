package org.modelix.modelql.client

import io.ktor.client.HttpClient
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.withTimeout
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.UL
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.ul
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.PBranch
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import org.modelix.modelql.core.IFragmentBuilder
import org.modelix.modelql.core.IRecursiveFragmentBuilder
import org.modelix.modelql.core.buildModelQLFragment
import org.modelix.modelql.core.isNotEmpty
import org.modelix.modelql.html.buildHtmlQuery
import org.modelix.modelql.server.ModelQLServer
import org.modelix.modelql.untyped.allChildren
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.createQueryExecutor
import org.modelix.modelql.untyped.property
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class HtmlBuilderTest {
    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        withTimeout(20.seconds) {
            application {
                val tree = CLTree(createObjectStoreCache(MapBasedStore()))
                val branch = PBranch(tree, IdGenerator.getInstance(1))
                val rootNode = branch.getRootNode()
                branch.runWrite {
                    val module1 = rootNode.addNewChild("modules", -1, null as IConceptReference?)
                    module1.setPropertyValue("name", "abc")
                    val model1a = module1.addNewChild("models", -1, null as IConceptReference?)
                    model1a.setPropertyValue("name", "model1a")
                    val model1b = module1.addNewChild("models", -1, null as IConceptReference?)
                    model1b.setPropertyValue("name", "model1b")
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
    fun modular() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()

        val modelTemplate = buildModelQLFragment<INode, FlowContent> {
            val name = input.property("name").getLater()
            onSuccess {
                div {
                    h2 {
                        +"Model: ${name.get()}"
                    }
                }
            }
        }

        fun IFragmentBuilder<INode, FlowContent>.renderModule() {
            val name = input.property("name").getLater()
            val models = input.children("models").requestFragment(modelTemplate)
            onSuccess {
                div {
                    h1 {
                        +"Module: ${name.get()}"
                    }
                    insertFragment(models)
                }
            }
        }

        fun HtmlBuilder<INode>.renderRepository() {
            val modules = input.children("modules").requestFragment {
                renderModule()
            }
            onSuccess {
                body {
                    insertFragment(modules)
                }
            }
        }

        val actual = client.getRootNode().queryAndBuildHtml {
            renderRepository()
        }
        val expected = """<html><body><div><h1>Module: abc</h1><div><h2>Model: model1a</h2></div><div><h2>Model: model1b</h2></div></div></body></html>"""
        assertEquals(expected, actual)
    }

    @Test
    fun recursive() = runTest { httpClient ->
        val client = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()

        val modelTemplate = buildModelQLFragment<INode, FlowContent> {
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
            val models = input.children("models").requestFragment(modelTemplate)
            onSuccess {
                body {
                    div {
                        h1 {
                            +"Module: ${name.get()}"
                        }
                        insertFragment(models)
                    }
                }
            }
        }

        fun IRecursiveFragmentBuilder<INode, UL>.renderNode() {
            val hashChildNodes = input.allChildren().isNotEmpty().getLater()
            val childNodes = input.allChildren().requestFragment(this)
            val name = input.property("name").getLater()
            onSuccess {
                li {
                    +name.get().toString()
                    if (hashChildNodes.get()) {
                        ul {
                            insertFragment(childNodes)
                        }
                    }
                }
            }
        }

        val actual = client.getRootNode().queryAndBuildHtml {
            val renderedNode = input.requestFragment<INode, UL> { renderNode() }
            onSuccess {
                body {
                    ul {
                        insertFragment(renderedNode)
                    }
                }
            }
        }
        val expected = """<html><body><ul><li>null<ul><li>abc<ul><li>model1a</li><li>model1b</li></ul></li></ul></li></ul></body></html>"""
        assertEquals(expected, actual)
    }
}

typealias HtmlBuilder<In> = IFragmentBuilder<In, HTML>

suspend fun INode.queryAndBuildHtml(body: IFragmentBuilder<INode, HTML>.() -> Unit): String {
    return buildHtmlQuery { body() }.bind(createQueryExecutor()).execute(asAsyncNode().getStreamExecutor()).value
}

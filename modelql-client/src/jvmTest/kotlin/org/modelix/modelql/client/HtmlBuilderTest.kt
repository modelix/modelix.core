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
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.mapLocal
import org.modelix.modelql.core.toList
import org.modelix.modelql.untyped.buildQuery
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.property
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

        val modelTemplate = buildModelQLTemplate<INode, FlowContent> {
            val name = input.property("name").prepare()

            templateBody {
                div {
                    h2 {
                        +"Model: ${name.get()}"
                    }
                }
            }
        }

        val moduleTemplate = buildModelQLTemplate<INode, FlowContent> {
            val name = input.property("name").prepare()
            val models = input.children("models").prepareTemplate(modelTemplate)

            templateBody {
                div {
                    h1 {
                        +"Module: ${name.get()}"
                    }
                    applyTemplates(models)
                }
            }
        }

        val repositoryTemplate = buildModelQLTemplate<INode, FlowContent> {
            val modules = input.children("modules").prepareTemplate(moduleTemplate)

            templateBody {
                applyTemplates(modules)
            }
        }

        val actual = client.getRootNode().queryAndBuildHtml(repositoryTemplate)
        val expected = """<html><body><div><h1>Module: abc</h1><div><h2>Model: model1a</h2></div><div><h2>Model: model1b</h2></div></div></body></html>"""
        assertEquals(expected, actual)
    }
}

typealias HtmlBuilder<In> = IAsyncBuilder<In, FlowContent>

suspend fun INode.queryAndBuildHtml(template: IModelQLTemplate<INode, HTML>): String {
    val query = buildQuery<String> { repository ->
        val htmlBuilder = AsyncBuilder<INode, HTML>(repository)
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

fun <In, Context> buildModelQLTemplate(body: IModelQLTemplateBuilder<In, Context>.() -> Unit): IModelQLTemplate<In, Context> {
    return object : IModelQLTemplate<In, Context> {
        override fun IModelQLTemplateBuilder<In, Context>.applyLater(): IModelQLTemplateInstance<Context> {
            return body()
        }
    }
}

class RequestedValue<E>(val step: IMonoStep<E>) {
    private var initialized: Boolean = false
    private var result: E? = null
    fun set(value: E) {
        result = value
        initialized = true
    }
    fun get(): E {
        require(initialized) { "Value not received for $this" }
        return result as E
    }
}

interface IModelQLTemplateBuilder<In, Context> {
    val input: IMonoStep<In>
    fun templateBody(body: Context.() -> Unit)
    fun <T> IMonoStep<T>.prepare(): RequestedValue<T>
    fun <TIn, TContext> IMonoStep<TIn>.prepareTemplate(template: IModelQLTemplate<TIn, TContext>): IModelQLTemplateInstance<TContext>
    fun <TIn, TContext> IFluxStep<TIn>.prepareTemplate(template: IModelQLTemplate<TIn, TContext>): IModelQLTemplateInstanceList<TContext>
    fun <ChildContext> ChildContext.applyTemplate(templateInstance: IModelQLTemplateInstance<ChildContext>)
    fun <ChildContext> ChildContext.applyTemplates(templateInstance: IModelQLTemplateInstanceList<ChildContext>)
}

class ModelQLTemplateBuilder<In, Context>(val input: IMonoStep<In>) : IModelQLTemplateBuilder<In, Context>, IModelQLTemplateInstance<Context> {
    private var templateBody: (Context.() -> Unit)? = null
    private val valueRequests = ArrayList<RequestedValue<Any?>>()

    override fun templateBody(body: Context.() -> Unit) {
        this.templateBody = body
    }
    override fun applyTemplateInstance(context: Context) {
        with(context) {
            templateBody()
        }
    }
    override fun <ChildContext> ChildContext.applyTemplate(templateInstance: IModelQLTemplateInstance<ChildContext>) {
        templateInstance.applyTemplate(this)
    }
    override fun <ChildContext> ChildContext.applyTemplates(templateInstance: IModelQLTemplateInstanceList<ChildContext>) {
        templateInstance.applyTemplates(this)
    }
    override fun <T> IMonoStep<T>.prepare(): RequestedValue<T> {
        return RequestedValue<T>(this).also { valueRequests.add(it as RequestedValue<Any?>) }
    }
    override fun <TIn, TContext> IFluxStep<TIn>.prepareTemplate(template: IModelQLTemplate<TIn, TContext>): IModelQLTemplateInstance<TContext> {
        toList()
    }
    override fun <TIn, TContext> IMonoStep<TIn>.prepareTemplate(template: IModelQLTemplate<TIn, TContext>): IModelQLTemplateInstance<TContext> {
        val childBuilder = ModelQLTemplateBuilder<TIn, TContext>(this)
        with(childBuilder) {
            with(template) {
                applyLater()
            }
        }
        return object : IModelQLTemplateInstance<TContext> {
            override fun applyTemplate(context: TContext) {
                with(context) {
                    return childBuilder.templateBody!!()
                }
            }
        }
    }
}

/**
 * To be implemented by the user.
 */
interface IModelQLTemplate<In, Context> {
    fun IModelQLTemplateBuilder<In, Context>.applyLater(): IModelQLTemplateInstance<Context>
}

interface IModelQLTemplateInstance<Context> {
    fun applyTemplateInstance(context: Context)
}
interface IModelQLTemplateInstanceList<Context> {
    fun applyTemplates(context: Context)
}

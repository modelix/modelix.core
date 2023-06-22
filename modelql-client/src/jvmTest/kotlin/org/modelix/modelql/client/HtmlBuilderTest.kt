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
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IZipOutput
import org.modelix.modelql.core.asFlux
import org.modelix.modelql.core.map
import org.modelix.modelql.core.mapLocal
import org.modelix.modelql.core.toList
import org.modelix.modelql.core.zipList
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.property
import kotlin.test.Test
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

        val htmlResult: String = client.query<String> { repository ->
            val htmlBuilder = HtmlBuilder(repository)
            htmlBuilder.renderRepository()
            htmlBuilder.compileOutputStep().mapLocal { result ->
                createHTML().html {
                    body {
                        htmlBuilder.apply {
                            processResult(result)
                        }
                    }
                }
            }
        }

        println(htmlResult)
    }
}

typealias ResultHandler = FlowContent.() -> Unit

class HtmlBuilder<E>(val node: IMonoStep<E>) {
    private val valueRequests = ArrayList<ValueRequest<Any?>>()
    private val iterationRequests = ArrayList<IterationRequest<Any?>>()
    private var resultHandler: ResultHandler? = null

    fun compileOutputStep(): IMonoStep<IZipOutput<*>> {
        val allRequestSteps: List<IMonoStep<Any?>> = valueRequests.map { it.step } + iterationRequests.map { it.outputStep }
        return zipList(*allRequestSteps.toTypedArray())
    }

    /**
     * Can be called multiple times for a list of results.
     */
    fun FlowContent.processResult(result: IZipOutput<*>) {
        val allRequests: List<Request<Any?>> = valueRequests + (iterationRequests as List<Request<Any?>>)
        allRequests.zip(result.values).forEach { (request, value) ->
            request.set(value)
        }

        resultHandler?.invoke(this)

        result.values
    }

    fun buildHtml(body: ResultHandler) {
        resultHandler = body
    }
    fun <T> IMonoStep<T>.getLater(): ValueRequest<T> {
        return ValueRequest(this).also { valueRequests.add(it as ValueRequest<Any?>) }
    }

    fun <T> IMonoStep<T>.iterateLater(body: HtmlBuilder<T>.() -> Unit): IIterationRequest {
        return this.asFlux().iterateLater(body)
    }
    fun <T> IFluxStep<T>.iterateLater(body: HtmlBuilder<T>.() -> Unit): IIterationRequest {
        lateinit var childBuilder: HtmlBuilder<T>
        val outputStep: IMonoStep<List<IZipOutput<*>>> = this.map {
            childBuilder = HtmlBuilder(it).apply(body)
            childBuilder.compileOutputStep()
        }.toList()
        return IterationRequest(childBuilder, outputStep).also { iterationRequests.add(it as IterationRequest<Any?>) }
    }

    fun FlowContent.iterate(request: IIterationRequest) {
        val casted = request as IterationRequest<*>
        require(casted.getOwner() != this) { "Iteration request belongs to a different builder" }
        casted.iterate(this)
    }

    abstract class Request<E> {
        var initialized: Boolean = false
        var result: E? = null
        fun set(value: E) {
            result = value
            initialized = true
        }
        fun get(): E {
            require(initialized) { "Value not received for $this" }
            return result as E
        }
    }

    class ValueRequest<E>(val step: IMonoStep<E>) : Request<E>()

    inner class IterationRequest<In>(val htmlBuilder: HtmlBuilder<In>, val outputStep: IMonoStep<List<IZipOutput<*>>>) : Request<List<IZipOutput<*>>>(), IIterationRequest {
        fun getOwner(): HtmlBuilder<*> = this@HtmlBuilder
        fun iterate(context: FlowContent) {
            context.apply {
                get().forEach { elementResult ->
                    htmlBuilder.apply { processResult(elementResult) }
                }
            }
        }
    }
}

interface IIterationRequest

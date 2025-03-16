package org.modelix.modelql.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.KSerializer
import org.modelix.model.api.INode
import org.modelix.model.api.UnresolvableNodeReferenceException
import org.modelix.model.api.async.asAsyncNode
import org.modelix.model.area.IArea
import org.modelix.modelql.core.EmptyQueryResultException
import org.modelix.modelql.core.IMonoUnboundQuery
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.MODELIX_VERSION
import org.modelix.modelql.core.QueryGraphDescriptor
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.VersionAndData
import org.modelix.modelql.core.upcast
import org.modelix.modelql.untyped.UntypedModelQL
import org.modelix.modelql.untyped.createQueryExecutor
import org.modelix.streams.StreamAssertionError
import kotlin.system.measureTimeMillis

class ModelQLServer private constructor(val rootNodeProvider: () -> INode?, val area: IArea? = null) {
    fun installHandler(route: Route) {
        route.post("query") {
            val rootNode = rootNodeProvider()!!
            handleCall(call, rootNode, area ?: rootNode.getArea())
        }
    }

    class Builder {
        private var rootNodeProvider: () -> INode? = { null }
        private var area: IArea? = null

        fun rootNode(rootNode: INode): Builder {
            this.rootNodeProvider = { rootNode }
            return this
        }

        fun rootNode(rootNodeProvider: () -> INode?): Builder {
            this.rootNodeProvider = rootNodeProvider
            return this
        }

        fun area(area: IArea): Builder {
            this.area = area
            return this
        }

        fun build(): ModelQLServer {
            return ModelQLServer(
                rootNodeProvider,
                area,
            )
        }
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger { }

        fun builder(rootNode: INode): Builder = Builder().also { it.rootNode(rootNode) }
        fun builder(rootNodeProvider: () -> INode?): Builder = Builder().also { it.rootNode(rootNodeProvider) }

        suspend fun handleCall(call: ApplicationCall, rootNode: INode, area: IArea) {
            handleCall(call, { rootNode to area }, {})
        }

        suspend fun handleCall(call: ApplicationCall, input: suspend (write: Boolean) -> Pair<INode, IArea>, afterQueryExecution: suspend () -> Unit = {}) {
            try {
                val serializedQuery = call.receiveText()
                val json = UntypedModelQL.json
                val queryDescriptor = VersionAndData.deserialize(serializedQuery, QueryGraphDescriptor.serializer(), json).data
                val query = queryDescriptor.createRootQuery() as IMonoUnboundQuery<INode, Any?>
                LOG.debug { "query: ${query.toString().lineSequence().map { it.trim() }.joinToString("")}" }
                val (rootNode, area) = input(query.requiresWriteAccess())
                val transactionBody: () -> IStepOutput<Any?> = {
                    area.runWithAdditionalScope {
                        val result: IStepOutput<Any?>
                        val time = measureTimeMillis {
                            result = rootNode.asAsyncNode().getStreamExecutor().query {
                                query.bind(rootNode.createQueryExecutor()).asAggregationStream()
                            }
                        }
                        if (time > 100) LOG.info { "Query execution took $time ms: $query" }
                        result
                    }
                }
                val result: IStepOutput<Any?> = if (query.requiresWriteAccess()) {
                    area.executeWrite(transactionBody)
                } else {
                    area.executeRead(transactionBody)
                }
                val serializer: KSerializer<IStepOutput<Any?>> =
                    query.getAggregationOutputSerializer(SerializationContext(json.serializersModule)).upcast()

                val versionAndResult = VersionAndData(result)
                val serializedResult = json.encodeToString(VersionAndData.serializer(serializer), versionAndResult)
                afterQueryExecution()
                call.respondText(text = serializedResult, contentType = ContentType.Application.Json)
            } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
                afterQueryExecution()
                val statusCode = when (ex) {
                    is UnresolvableNodeReferenceException,
                    is StreamAssertionError,
                    is EmptyQueryResultException,
                    -> HttpStatusCode.UnprocessableEntity

                    else -> HttpStatusCode.InternalServerError
                }
                call.respondText(
                    text = "server version: $MODELIX_VERSION\n" + ex.stackTraceToString(),
                    status = statusCode,
                )
            }
        }
    }
}

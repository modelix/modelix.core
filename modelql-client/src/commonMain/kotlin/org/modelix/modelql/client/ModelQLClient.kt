package org.modelix.modelql.client

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.area.IArea
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IUnboundQuery
import org.modelix.modelql.core.MODELIX_VERSION
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.UnboundQuery
import org.modelix.modelql.core.VersionAndData
import org.modelix.modelql.core.castToInstance
import org.modelix.modelql.untyped.UntypedModelQL
import org.modelix.modelql.untyped.query

class ModelQLClient(val url: String, val client: HttpClient, includedSerializersModule: SerializersModule = UntypedModelQL.serializersModule) {
    val serializersModule = SerializersModule {
        include(includedSerializersModule)
        polymorphicDefaultDeserializer(INode::class) {
            ModelQLNodeSerializer(this@ModelQLClient)
        }
    }
    val json = Json {
        serializersModule = this@ModelQLClient.serializersModule
    }
    private val rootNode = ModelQLRootNode(this)

    fun getRootNode(): INode = rootNode

    fun getNode(ref: INodeReference) = ModelQLNodeWithConceptQuery(this, ref.toSerializedRef())

    fun getArea(): IArea = ModelQLArea(this)

    suspend fun <R> query(body: (IMonoStep<INode>) -> IMonoStep<R>): R {
        return rootNode.query(body)
    }

    suspend fun <T> runQuery(query: IUnboundQuery<INode, T, *>): T {
        return deserialize(queryAsJson(query.castToInstance()), query)
    }

    protected fun <T> deserialize(serializedJson: String, query: IUnboundQuery<*, T, *>): T {
        return ModelQLArea(this).runWithAdditionalScope {
            VersionAndData.deserialize(
                serializedJson,
                query.getAggregationOutputSerializer(SerializationContext(json.serializersModule)),
                json,
            ).data.value
        }
    }

    protected suspend fun queryAsJson(query: UnboundQuery<INode, *, *>): String {
        val response = client.post(url) {
            LOG.debug { "query: " + query }
            val queryDescriptor = query.createDescriptor()
            val versionAndQuery = VersionAndData(queryDescriptor)
            val queryAsJson = json.encodeToString(versionAndQuery)
            setBody(queryAsJson)
        }
        when (response.status) {
            HttpStatusCode.OK -> {
                val text = response.bodyAsText()
                LOG.debug { "result: $text" }
                return text
            }
            else -> throw ModelQueryRequestException(query, response, response.bodyAsText())
        }
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger { }
        fun builder(): ModelQLClientBuilder = PlatformSpecificModelQLClientBuilder()
    }
}

expect fun <ResultT> ModelQLClient.blockingQuery(body: (IMonoStep<INode>) -> IMonoStep<ResultT>): ResultT

class ModelQueryRequestException(
    val query: UnboundQuery<INode, *, *>,
    val httpResponse: HttpResponse,
    val responseBody: String,
) : RuntimeException("Query failed : $query \nclient version: $MODELIX_VERSION\n${httpResponse.status}\n$responseBody")

package org.modelix.modelql.client

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.modelix.model.api.INode
import org.modelix.model.area.ContextArea
import org.modelix.model.area.IArea
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.Query
import org.modelix.modelql.untyped.UntypedModelQL

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

    fun getArea(): IArea = ModelQLArea(this)

    suspend fun <T> runQuery(query: Query<INode, T>): T {
        return deserialize(queryAsJson(query), query)
    }

    protected fun <T> deserialize(serializedJson: String, query: Query<*, T>): T {
        return ContextArea.withAdditionalContext(ModelQLArea(this)) {
            json.decodeFromString(
                query.getOutputSerializer(json.serializersModule),
                serializedJson
            )
        }
    }

    suspend fun <ResultT> query(body: (IMonoStep<INode>) -> IMonoStep<ResultT>): ResultT {
        return runQuery(Query.build(body))
    }

    protected suspend fun queryAsJson(query: Query<INode, *>): String {
        val response = client.post(url) {
            LOG.debug { "query: " + query }
            val queryDescriptor = query.createDescriptor()
            val queryAsJson = json.encodeToString(queryDescriptor)
            setBody(queryAsJson)
        }
        return response.bodyAsText()
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger {  }
        fun builder(): ModelQLClientBuilder = PlatformSpecificModelQLClientBuilder()
    }
}

expect fun <ResultT> ModelQLClient.blockingQuery(body: (IMonoStep<INode>) -> IMonoStep<ResultT>): ResultT

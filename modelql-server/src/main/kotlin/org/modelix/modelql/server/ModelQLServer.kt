/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.modelql.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import org.modelix.model.api.INode
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.area.IArea
import org.modelix.modelql.core.IMonoUnboundQuery
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryGraphDescriptor
import org.modelix.modelql.core.VersionAndData
import org.modelix.modelql.core.modelqlVersion
import org.modelix.modelql.core.upcast
import org.modelix.modelql.untyped.UntypedModelQL
import org.modelix.modelql.untyped.createQueryExecutor

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
                area
            )
        }
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger { }

        fun builder(rootNode: INode): Builder = Builder().also { it.rootNode(rootNode) }
        fun builder(rootNodeProvider: () -> INode?): Builder = Builder().also { it.rootNode(rootNodeProvider) }

        suspend fun handleCall(call: ApplicationCall, rootNode: INode, area: IArea) {
            try {
                val serializedQuery = call.receiveText()
                val json = UntypedModelQL.json
                val queryDescriptor = VersionAndData.deserialize(serializedQuery, QueryGraphDescriptor.serializer(), json).data
                val query = queryDescriptor.createRootQuery() as IMonoUnboundQuery<INode, Any?>
                LOG.debug { "query: $query" }
                val nodeResolutionScope: INodeResolutionScope = area
                val transactionBody: () -> IStepOutput<Any?> = {
                    runBlocking {
                        withContext(nodeResolutionScope) {
                            query.bind(rootNode.createQueryExecutor()).execute()
                        }
                    }
                }
                val result: IStepOutput<Any?> = if (query.requiresWriteAccess()) {
                    area.executeWrite(transactionBody)
                } else {
                    area.executeRead(transactionBody)
                }
                val serializer: KSerializer<IStepOutput<Any?>> =
                    query.getAggregationOutputSerializer(json.serializersModule).upcast()

                val versionAndResult = VersionAndData(result)
                val serializedResult = json.encodeToString(VersionAndData.serializer(serializer), versionAndResult)
                call.respondText(text = serializedResult, contentType = ContentType.Application.Json)
            } catch (ex: Throwable) {
                call.respondText(text = "server version: $modelqlVersion\n" + ex.stackTraceToString(), status = HttpStatusCode.InternalServerError)
            }
        }
    }
}

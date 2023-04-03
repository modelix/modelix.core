/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.encodeToStream
import org.modelix.authorization.getUserName
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.api.ModelQuery
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.slf4j.LoggerFactory
import java.util.*

private fun toLong(value: String?): Long {
    return if (value == null || value.isEmpty()) 0 else value.toLong()
}

/**
 * Implements the endpoints used by the 'model-client', but compared to KeyValueLikeModelServer also understands what
 * client sends. This allows more validations and more responsibilities on the server side.
 */
@OptIn(ExperimentalSerializationApi::class)
class ModelReplicationServer(val repositoriesManager: RepositoriesManager) {
    constructor(modelClient: LocalModelClient) : this(RepositoriesManager(modelClient))
    constructor(storeClient: IStoreClient) : this(LocalModelClient(storeClient))

    companion object {
        private val LOG = LoggerFactory.getLogger(ModelReplicationServer::class.java)

        private fun randomUUID(): String {
            return UUID.randomUUID().toString().replace("[^a-zA-Z0-9]".toRegex(), "")
        }
    }

    private val modelClient: LocalModelClient get() = repositoriesManager.client
    private val storeClient: IStoreClient get() = modelClient.store

    fun init(application: Application) {
        KeyValueLikeModelServer.initServerId(storeClient)
        application.apply {
            routing {
                route("v2") {
                    installHandlers()
                }
            }
        }
    }

    private fun Route.installHandlers() {
        post("generate-client-id") {
            call.respondText(storeClient.generateId("clientId").toString())
        }
        get("server-id") {
            call.respondText(KeyValueLikeModelServer.getServerId(storeClient))
        }
        get("user-id") {
            call.respondText(call.getUserName() ?: call.request.origin.remoteHost)
        }
        route("repositories") {
            get {
                call.respondText(repositoriesManager.getRepositories().joinToString("\n") { it.id })
            }
            route("{repository}") {
                fun ApplicationCall.repositoryId() = RepositoryId(parameters["repository"]!!)
                fun PipelineContext<Unit, ApplicationCall>.repositoryId() = call.repositoryId()
                post("init") {
                    val initialVersion = repositoriesManager.createRepository(repositoryId(), call.getUserName())
                    call.respondDelta(initialVersion.hash, null)
                }
                route("branches") {
                    get {
                        call.respondText(repositoriesManager.getBranchNames(repositoryId()).joinToString("\n"))
                    }
                    route("{branch}") {
                        fun ApplicationCall.branchRef() = repositoryId().getBranchReference(parameters["branch"]!!)
                        fun PipelineContext<Unit, ApplicationCall>.branchRef() = call.branchRef()
                        get {
                            val baseVersionHash = call.request.queryParameters["lastKnown"]
                            val branch = branchRef()
                            val versionHash = repositoriesManager.getVersionHash(branch)
                            if (versionHash == null) {
                                call.respondText(
                                    "Branch '${branch.branchName}' doesn't exist in repository '${branch.repositoryId.id}'",
                                    status = HttpStatusCode.NotFound
                                )
                                return@get
                            }
                            call.respondDelta(versionHash, baseVersionHash)
                        }
                        post {
                            val deltaFromClient = call.receive<VersionDelta>()
                            storeClient.putAll(deltaFromClient.objects.associateBy { HashUtil.sha256(it) })
                            val mergedHash = repositoriesManager.mergeChanges(branchRef(), deltaFromClient.versionHash)
                            call.respondDelta(mergedHash, deltaFromClient.versionHash)
                        }
                        get("poll") {
                            val lastKnownVersionHash = call.request.queryParameters["lastKnown"]
                            val newVersionHash = repositoriesManager.pollVersionHash(branchRef(), lastKnownVersionHash)
                            call.respondDelta(newVersionHash, lastKnownVersionHash)
                        }
                        webSocket("listen") {
                            var lastVersionHash = call.request.queryParameters["lastKnown"]
                            while (coroutineContext[Job]?.isCancelled == false) {
                                val newVersionHash =
                                    repositoriesManager.pollVersionHash(call.branchRef(), lastVersionHash)
                                val delta = VersionDelta(
                                    newVersionHash,
                                    lastVersionHash,
                                    repositoriesManager.computeDelta(newVersionHash, lastVersionHash).values.filterNotNull().toSet()
                                )
                                send(Json.encodeToString(delta))
                                lastVersionHash = newVersionHash
                            }
                        }
                    }
                }
            }
        }
        route("versions") {
            get("{versionHash}") {
                val baseVersionHash = call.request.queryParameters["lastKnown"]
                val versionHash = call.parameters["versionHash"]!!
                if (storeClient[versionHash] == null) {
                    call.respondText(
                        "Version '$versionHash' doesn't exist",
                        status = HttpStatusCode.NotFound
                    )
                    return@get
                }
                call.respondDelta(versionHash, baseVersionHash)
            }
            get("{versionHash}/history/{oldestVersionHash}") {
                TODO()
            }
        }
        route("objects") {
            post {
                withContext(Dispatchers.IO) {
                    Json.decodeToSequence<String>(call.receiveStream(), DecodeSequenceMode.ARRAY_WRAPPED)
                        .chunked(5000)
                        .forEach { values ->
                            storeClient.putAll(values.associateBy { HashUtil.sha256(it) }, true)
                        }
                }
                call.respondText("OK")
            }
            get("{hash}") {
                val key = call.parameters["hash"]!!
                val value = storeClient[key]
                if (value == null) {
                    call.respondText("object '$key' not found", status = HttpStatusCode.NotFound)
                } else {
                    call.respondText(value)
                }
            }
        }
        route("modelql") {
            put {
                val params = call.receiveParameters()
                val queryFromClient = params["query"]
                if (queryFromClient == null) {
                    call.respondText(text = "'query' is missing", status = HttpStatusCode.BadRequest)
                    return@put
                }
                val query = ModelQuery.fromJson(queryFromClient)
                val json = query.toJson()
                val hash = HashUtil.sha256(json)
                storeClient.put(hash, json,)
                call.respondText(text = hash)
            }
            get("{hash}") {
                val hash = call.parameters["hash"]!!
                val json = storeClient[hash]
                if (json == null) {
                    call.respondText(status = HttpStatusCode.NotFound, text = "ModelQL with hash '$hash' doesn't exist")
                    return@get
                }
                ModelQuery.fromJson(json) // ensure it's a valid ModelQuery
                call.respondText(json, ContentType.Application.Json)
            }
        }
    }

    suspend fun ApplicationCall.respondDelta(versionHash: String, baseVersionHash: String?) {
        val delta = VersionDelta(
            versionHash,
            baseVersionHash,
            repositoriesManager.computeDelta(versionHash, baseVersionHash).values.filterNotNull().toSet()
        )
        respondOutputStream(contentType = ContentType.Application.Json) {
            Json.encodeToStream(delta, this)
        }
    }
}

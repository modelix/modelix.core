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

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.util.pipeline.PipelineContext
import io.ktor.websocket.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.authorization.getUserName
import org.modelix.model.api.PBranch
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.area.getArea
import org.modelix.model.client2.checkObjectHashes
import org.modelix.model.client2.getAllObjects
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.api.v2.VersionDelta
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.modelql.server.ModelQLServer
import org.slf4j.LoggerFactory

/**
 * Implements the endpoints used by the 'model-client', but compared to KeyValueLikeModelServer also understands what
 * client sends. This allows more validations and more responsibilities on the server side.
 */
class ModelReplicationServer(val repositoriesManager: RepositoriesManager) {
    constructor(modelClient: LocalModelClient) : this(RepositoriesManager(modelClient))
    constructor(storeClient: IStoreClient) : this(LocalModelClient(storeClient))

    companion object {
        private val LOG = LoggerFactory.getLogger(ModelReplicationServer::class.java)
    }

    private val modelClient: LocalModelClient get() = repositoriesManager.client
    private val storeClient: IStoreClient get() = modelClient.store

    fun init(application: Application) {
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
            call.respondText(repositoriesManager.getServerId())
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
                    val useRoleIds = call.request.queryParameters["useRoleIds"] != "false"
                    val initialVersion = repositoriesManager.createRepository(repositoryId(), call.getUserName(), useRoleIds)
                    call.respondDelta(initialVersion.getContentHash(), null)
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
                                    status = HttpStatusCode.NotFound,
                                )
                                return@get
                            }
                            call.respondDelta(versionHash, baseVersionHash)
                        }
                        get("hash") {
                            val branch = branchRef()
                            val versionHash = repositoriesManager.getVersionHash(branch)
                            if (versionHash == null) {
                                call.respondText(
                                    "Branch '${branch.branchName}' doesn't exist in repository '${branch.repositoryId.id}'",
                                    status = HttpStatusCode.NotFound,
                                )
                                return@get
                            }
                            call.respondText(versionHash)
                        }
                        post {
                            val deltaFromClient = call.receive<VersionDelta>()
                            deltaFromClient.checkObjectHashes()
                            storeClient.putAll(deltaFromClient.getAllObjects())
                            val mergedHash = repositoriesManager.mergeChanges(branchRef(), deltaFromClient.versionHash)
                            call.respondDelta(mergedHash, deltaFromClient.versionHash)
                        }
                        get("poll") {
                            val lastKnownVersionHash = call.request.queryParameters["lastKnown"]
                            val newVersionHash = repositoriesManager.pollVersionHash(branchRef(), lastKnownVersionHash)
                            call.respondDelta(newVersionHash, lastKnownVersionHash)
                        }
                        get("pollHash") {
                            val lastKnownVersionHash = call.request.queryParameters["lastKnown"]
                            val newVersionHash = repositoriesManager.pollVersionHash(branchRef(), lastKnownVersionHash)
                            call.respondText(newVersionHash)
                        }
                        webSocket("listen") {
                            var lastVersionHash = call.request.queryParameters["lastKnown"]
                            while (coroutineContext[Job]?.isCancelled == false) {
                                val newVersionHash =
                                    repositoriesManager.pollVersionHash(call.branchRef(), lastVersionHash)
                                val delta = VersionDelta(
                                    newVersionHash,
                                    lastVersionHash,
                                    objectsMap = repositoriesManager.computeDelta(newVersionHash, lastVersionHash),
                                )
                                delta.checkObjectHashes()
                                send(Json.encodeToString(delta))
                                lastVersionHash = newVersionHash
                            }
                        }
                        post("query") {
                            val branchRef = branchRef()
                            val version = repositoriesManager.getVersion(branchRef)
                            val initialTree = version!!.getTree()
                            val branch = OTBranch(PBranch(initialTree, repositoriesManager.client.idGenerator), repositoriesManager.client.idGenerator, repositoriesManager.client.storeCache)
                            ModelQLServer.handleCall(call, branch.getRootNode(), branch.getArea())

                            val (ops, newTree) = branch.operationsAndTree
                            if (newTree != initialTree) {
                                val newVersion = CLVersion.createRegularVersion(
                                    id = repositoriesManager.client.idGenerator.generate(),
                                    author = getUserName(),
                                    tree = newTree as CLTree,
                                    baseVersion = version,
                                    operations = ops.map { it.getOriginalOp() }.toTypedArray(),
                                )
                                repositoriesManager.mergeChanges(branchRef, newVersion.getContentHash())
                            }
                        }
                    }
                }
                route("versions") {
                    route("{versionHash}") {
                        get {
                            // TODO permission check on the repository ID is not sufficient, because the client could
                            //      provide any repository ID to access a version inside a different repository.
                            //      A check if the version belongs to the repository is required.
                            val baseVersionHash = call.request.queryParameters["lastKnown"]
                            val versionHash = call.parameters["versionHash"]!!
                            if (storeClient[versionHash] == null) {
                                call.respondText(
                                    "Version '$versionHash' doesn't exist",
                                    status = HttpStatusCode.NotFound,
                                )
                                return@get
                            }
                            call.respondDelta(versionHash, baseVersionHash)
                        }
                        get("history/{oldestVersionHash}") {
                            TODO()
                        }
                        post("query") {
                            val versionHash = call.parameters["versionHash"]!!
                            val version = CLVersion.loadFromHash(versionHash, repositoriesManager.client.storeCache)
                            val initialTree = version.getTree()
                            val branch = TreePointer(initialTree)
                            ModelQLServer.handleCall(call, branch.getRootNode(), branch.getArea())
                        }
                    }
                }
                route("objects") {
                    put {
                        var writtenEntries = 0
                        withContext(Dispatchers.IO) {
                            var isKey = true
                            var key = ""
                            call.receiveStream().bufferedReader().lineSequence().forEach { line ->
                                if (isKey) {
                                    key = line
                                } else {
                                    val value = line
                                    require(HashUtil.isSha256(key)) {
                                        "This API cannot be used to store other entries than serialized objects." +
                                            " The key is expected to be a SHA256 hash over the value: $key -> $value"
                                    }
                                    val expectedKey = HashUtil.sha256(value)
                                    require(expectedKey == key) { "Hash mismatch. Expected $expectedKey, but $key was provided. Value: $value" }
                                    storeClient.put(key, value, true)
                                    writtenEntries++
                                }
                                isKey = !isKey
                            }
                        }
                        call.respondText("$writtenEntries objects received")
                    }
                }
            }
        }
        route("versions") {
            get("{versionHash}") {
                // TODO versions should be stored inside a repository with permission checks.
                //      Knowing a version hash should not give you access to the content.
                //      This handler was already moved to the 'repositories' route. Removing it here would be a breaking
                //      change, but should be done in some future version.
                val baseVersionHash = call.request.queryParameters["lastKnown"]
                val versionHash = call.parameters["versionHash"]!!
                if (storeClient[versionHash] == null) {
                    call.respondText(
                        "Version '$versionHash' doesn't exist",
                        status = HttpStatusCode.NotFound,
                    )
                    return@get
                }
                call.respondDelta(versionHash, baseVersionHash)
            }
            get("{versionHash}/history/{oldestVersionHash}") {
                TODO()
            }
        }
    }

    private suspend fun ApplicationCall.respondDelta(versionHash: String, baseVersionHash: String?) {
        val delta = VersionDelta(
            versionHash,
            baseVersionHash,
            objectsMap = repositoriesManager.computeDelta(versionHash, baseVersionHash),
        )
        delta.checkObjectHashes()
        respond(delta)
    }
}

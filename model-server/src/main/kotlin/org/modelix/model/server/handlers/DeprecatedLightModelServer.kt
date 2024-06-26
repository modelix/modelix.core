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
package org.modelix.model.server.handlers

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.request.receiveText
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.html.h1
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import org.json.JSONArray
import org.json.JSONObject
import org.modelix.api.deprecated.Paths
import org.modelix.authorization.KeycloakScope
import org.modelix.authorization.asResource
import org.modelix.authorization.getUserName
import org.modelix.authorization.requiresPermission
import org.modelix.model.IKeyListener
import org.modelix.model.VersionMerger
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.TreePointer
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.templates.PageWithMenuBar
import java.util.Date

class DeprecatedLightModelServer(val client: LocalModelClient, val repositoriesManager: RepositoriesManager) {

    fun init(application: Application) {
        application.apply {
            routing {
                requiresPermission("model-json-api".asResource(), KeycloakScope.READ) {
                    route("/") {
                        initRouting()
                    }
                }
            }
        }
    }

    private suspend fun getCurrentVersion(repositoryId: RepositoryId): CLVersion {
        return repositoriesManager.runWithRepository(repositoryId) {
            val branch = repositoryId.getBranchReference()
            checkNotNull(repositoriesManager.getVersion(branch)) { "Branch not found: $branch" }
        }
    }

    private fun Route.initRouting() {
        get<Paths.jsonGet> {
            call.respondHtmlTemplate(PageWithMenuBar("json/", ".."), status = HttpStatusCode.OK) {
                headContent {
                    title("JSON API")
                }
                bodyContent {
                    h1 { +"JSON API" }
                    table {
                        thead {
                            tr {
                                th { +"Route" }
                                th { +"Description" }
                            }
                        }
                        tbody {
                            tr {
                                td { +"GET /{repositoryId}/" }
                                td { +"Returns the model content of the latest version on the master branch." }
                            }
                            tr {
                                td { +"GET /{repositoryId}/{versionHash}/" }
                                td { +"Returns the model content of the specified version on the master branch." }
                            }
                            tr {
                                td { +"GET /{repositoryId}/{versionHash}/poll" }
                                td { +"" }
                            }
                            tr {
                                td { +"POST /{repositoryId}/init" }
                                td { +"Initializes a new repository." }
                            }
                            tr {
                                td { +"POST /{repositoryId}/{versionHash}/update" }
                                td {
                                    +"Applies the delta to the specified version of the model and merges"
                                    +" it into the master branch. Return the model content after the merge."
                                }
                            }
                            tr {
                                td { +"WEBSOCKET /{repositoryId}/ws" }
                                td {
                                    +"WebSocket for exchanging model deltas."
                                }
                            }
                        }
                    }
                }
            }
        }
        get<Paths.jsonRepositoryIdGet> {
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            val version = repositoriesManager.getVersion(repositoryId.getBranchReference())!!
            // TODO 404 if it doesn't exist
            respondVersion(version)
        }
        get<Paths.jsonRepositoryIdVersionHashGet> {
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            repositoriesManager.runWithRepository(repositoryId) {
                val versionHash = call.parameters["versionHash"]!!
                // TODO 404 if it doesn't exist
                val version = repositoriesManager.getVersion(repositoryId, versionHash)
                respondVersion(version!!)
            }
        }
        get<Paths.jsonRepositoryIdVersionHashPollGet> {
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            repositoriesManager.runWithRepository(repositoryId) {
                val oldHash = call.parameters["versionHash"]!!
                val newHash = repositoriesManager.pollVersionHash(repositoryId.getBranchReference(), oldHash)
                val newVersion = repositoriesManager.getVersion(repositoryId, newHash)!!
                val oldVersion = repositoriesManager.getVersion(repositoryId, oldHash)
                respondVersion(newVersion, oldVersion)
            }
        }
        webSocket("/json/{repositoryId}/ws") {
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            repositoriesManager.runWithRepository(repositoryId) {
                val userId = call.getUserName()

                var lastVersion: CLVersion? = null
                val deltaMutex = Mutex()
                val sendDelta: suspend (CLVersion) -> Unit = { newVersion ->
                    deltaMutex.withLock {
                        if (newVersion.getContentHash() != lastVersion?.getContentHash()) {
                            send(versionAsJson(newVersion, lastVersion).toString())
                            lastVersion = newVersion
                        }
                    }
                }

                val listener = object : IKeyListener {
                    override fun changed(key: String, value: String?) {
                        if (value == null) return
                        launch {
                            repositoriesManager.runWithRepository(repositoryId) {
                                val newVersion = CLVersion.loadFromHash(value, client.storeCache)
                                sendDelta(newVersion)
                            }
                        }
                    }
                }

                client.listen(repositoryId.getBranchKey(), listener)
                try {
                    sendDelta(getCurrentVersion(repositoryId))
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val updateData = JSONArray(frame.readText())
                                val mergedVersion = applyUpdate(lastVersion!!, updateData, repositoryId, userId)
                                sendDelta(mergedVersion)
                            }
                            else -> {}
                        }
                    }
                } finally {
                    client.removeListener(repositoryId.getBranchKey(), listener)
                }
            }
        }
        post<Paths.jsonRepositoryIdInitPost> {
            // TODO error if it already exists
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            repositoriesManager.runWithRepository(repositoryId) {
                val userId = call.getUserName()
                val newVersion = repositoriesManager.createRepository(repositoryId, userId)
                respondVersion(newVersion)
            }
        }
        post<Paths.jsonRepositoryIdVersionHashUpdatePost> {
            val updateData = JSONArray(call.receiveText())
            val repositoryId = RepositoryId(call.parameters["repositoryId"]!!)
            repositoriesManager.runWithRepository(repositoryId) {
                val baseVersionHash = call.parameters["versionHash"]!!
                val baseVersion = repositoriesManager.getVersion(repositoryId, baseVersionHash)
                if (baseVersion == null) {
                    call.respond(HttpStatusCode.NotFound, "version not found: $baseVersionHash")
                    return@runWithRepository
                }
                val mergedVersion = applyUpdate(baseVersion, updateData, repositoryId, getUserName())
                respondVersion(mergedVersion, baseVersion)
            }
        }
        post<Paths.jsonGenerateIdsPost> {
            val quantity = call.request.queryParameters["quantity"]?.toInt() ?: 1000
            val ids = (client.idGenerator as IdGenerator).generate(quantity)
            respondJson(
                buildJSONObject {
                    put("first", ids.first)
                    put("last", ids.last)
                },
            )
        }
    }

    private suspend fun applyUpdate(
        baseVersion: CLVersion,
        updateData: JSONArray,
        repositoryId: RepositoryId,
        userId: String?,
    ): CLVersion {
        val branch = OTBranch(PBranch(baseVersion.tree, client.idGenerator), client.idGenerator, client.storeCache)
        branch.computeWriteT { t ->
            for (nodeData in (0 until updateData.length()).map { updateData.getJSONObject(it) }) {
                updateNode(nodeData, containmentData = null, t)
            }
        }

        val operationsAndTree = branch.getPendingChanges()
        val newVersion = CLVersion.createRegularVersion(
            client.idGenerator.generate(),
            Date().toString(),
            userId,
            operationsAndTree.second as CLTree,
            baseVersion,
            operationsAndTree.first.map { it.getOriginalOp() }.toTypedArray(),
        )
        repositoryId.getBranchKey()
        val mergedVersion = VersionMerger(client.storeCache, client.idGenerator)
            .mergeChange(getCurrentVersion(repositoryId), newVersion)
        repositoriesManager.mergeChanges(repositoryId.getBranchReference(), mergedVersion.getContentHash())
        return mergedVersion
    }

    private fun updateNode(nodeData: JSONObject, containmentData: ContainmentData?, t: IWriteTransaction): Long {
        var actualContainmentData = containmentData
        val nodeId = nodeData.getString("nodeId").toLong()
        if (!t.containsNode(nodeId)) {
            if (actualContainmentData == null) {
                actualContainmentData = ContainmentData(nodeData.optLong("parent", ITree.ROOT_ID), nodeData.optString("role", null), nodeData.optInt("index", -1))
            }
            t.addNewChild(
                actualContainmentData.parent,
                actualContainmentData.role,
                actualContainmentData.index,
                nodeId,
                null as IConcept?,
            )
        }
        nodeData.optJSONObject("properties")?.stringEntries()?.forEach { (role, newValue) ->
            if (t.getProperty(nodeId, role) != newValue) {
                t.setProperty(nodeId, role, newValue)
            }
        }
        nodeData.optJSONObject("references")?.longEntries()?.forEach { (role, newTargetId) ->
            val currentTarget = t.getReferenceTarget(nodeId, role)
            val currentTargetId: Long? = when (currentTarget) {
                is LocalPNodeReference -> currentTarget.id
                is PNodeReference -> if (currentTarget.branchId == t.tree.getId()) currentTarget.id else null
                else -> null
            }
            if (newTargetId != currentTargetId) {
                val newTarget = if (newTargetId == null) null else LocalPNodeReference(newTargetId)
                t.setReferenceTarget(nodeId, role, newTarget)
            }
        }
        nodeData.optJSONObject("children")?.arrayEntries()?.forEach { (role, childDataArray) ->
            val expectedChildren = childDataArray.mapIndexed { index, child ->
                when (child) {
                    is Number -> child.toLong()
                    is JSONObject -> updateNode(child, ContainmentData(nodeId, role, index), t)
                    else -> throw RuntimeException("Unsupported child data: $child")
                }
            }
            val unexpected = (t.getChildren(nodeId, role).toSet() - expectedChildren.toSet())
            if (unexpected.isNotEmpty()) {
                unexpected.forEach { child ->
                    t.moveChild(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE, -1, child)
                }
            }
            val actualChildren = t.getChildren(nodeId, role)
            if (actualChildren != expectedChildren) {
                expectedChildren.forEachIndexed { index, child ->
                    t.moveChild(nodeId, role, index, child)
                }
            }
        }
        return nodeId
    }

    private suspend fun CallContext.respondVersion(version: CLVersion, oldVersion: CLVersion? = null) {
        val json = versionAsJson(version, oldVersion)
        respondJson(json)
    }

    private fun versionAsJson(
        version: CLVersion,
        oldVersion: CLVersion?,
    ): JSONObject {
        val branch = TreePointer(version.getTree())
        val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
        val json = JSONObject()
        json.put("repositoryId", version.getTree().getId())
        json.put("versionHash", version.getContentHash())
        if (oldVersion == null) {
            json.put("root", node2json(rootNode, true))
        } else {
            val nodesToInclude = HashSet<Long>()
            version.getTree().visitChanges(
                oldVersion.getTree(),
                object : ITreeChangeVisitorEx {
                    override fun childrenChanged(nodeId: Long, role: String?) {
                        nodesToInclude += nodeId
                    }

                    override fun containmentChanged(nodeId: Long) {}

                    override fun conceptChanged(nodeId: Long) {
                        nodesToInclude += nodeId
                    }

                    override fun propertyChanged(nodeId: Long, role: String) {
                        nodesToInclude += nodeId
                    }

                    override fun referenceChanged(nodeId: Long, role: String) {
                        nodesToInclude += nodeId
                    }

                    override fun nodeAdded(nodeId: Long) {
                        nodesToInclude += nodeId
                    }

                    override fun nodeRemoved(nodeId: Long) {}
                },
            )
            val changedNodes = nodesToInclude.map { node2json(PNodeAdapter(it, branch), false) }.toJsonArray()
            json.put("nodes", changedNodes)
            version.getTree()
        }
        return json
    }

    private suspend fun CallContext.respondJson(json: JSONObject) {
        call.respondText(json.toString(2), ContentType.Application.Json)
    }
    private suspend fun CallContext.respondJson(json: JSONArray) {
        call.respondText(json.toString(2), ContentType.Application.Json)
    }

    private fun node2json(node: INode, includeDescendants: Boolean): JSONObject {
        val json = JSONObject()
        if (node is PNodeAdapter) {
            json.put("nodeId", node.nodeId.toString())
        }
        val jsonProperties = JSONObject()
        val jsonReferences = JSONObject()
        val jsonChildren = JSONObject()
        json.put("properties", jsonProperties)
        json.put("references", jsonReferences)
        json.put("children", jsonChildren)

        for (role in node.getPropertyRoles()) {
            jsonProperties.put(role, node.getPropertyValue(role))
        }
        for (role in node.getReferenceRoles()) {
            val target = node.getReferenceTarget(role)
            if (target is PNodeAdapter) {
                jsonReferences.put(role, target.nodeId.toString())
            }
        }
        for (children in node.allChildren.groupBy { it.roleInParent }) {
            if (includeDescendants) {
                jsonChildren.put(children.key ?: "null", children.value.map { node2json(it, includeDescendants) })
            } else {
                jsonChildren.put(children.key ?: "null", children.value.map { (it as PNodeAdapter).nodeId.toString() })
            }
        }
        return json
    }
}

class ContainmentData(val parent: Long, val role: String?, val index: Int)

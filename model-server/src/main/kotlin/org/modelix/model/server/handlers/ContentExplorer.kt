package org.modelix.model.server.handlers

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import kotlinx.html.BODY
import kotlinx.html.FlowContent
import kotlinx.html.UL
import kotlinx.html.b
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.small
import kotlinx.html.stream.appendHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import org.modelix.api.html.Paths
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.TreePointer
import org.modelix.model.client.IModelClient
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.templates.PageWithMenuBar
import kotlin.collections.set

class ContentExplorer(private val client: IModelClient, private val repoManager: IRepositoriesManager) {

    fun init(application: Application) {
        application.routing {
            get<Paths.getContent> {
                call.respondRedirect("../repos/")
            }
            get<Paths.getContentRepositoryBranchLatest> {
                val repository = call.parameters["repository"]
                val branch = call.parameters["branch"]
                if (repository.isNullOrEmpty()) {
                    call.respondText("repository not found", status = HttpStatusCode.BadRequest)
                    return@get
                }
                if (branch.isNullOrEmpty()) {
                    call.respondText("branch not found", status = HttpStatusCode.BadRequest)
                    return@get
                }

                val latestVersion = repoManager.getVersion(BranchReference(RepositoryId(repository), branch))
                if (latestVersion == null) {
                    call.respondText("unable to find latest version", status = HttpStatusCode.InternalServerError)
                    return@get
                } else {
                    call.respondRedirect("../../../versions/${latestVersion.getContentHash()}/")
                }
            }
            get<Paths.getVersionHash> {
                val repositoryId = call.parameters["repository"]?.let { RepositoryId(it) }
                if (repositoryId == null) {
                    call.respondText("repository parameter missing", status = HttpStatusCode.BadRequest)
                    return@get
                }
                val versionHash = call.parameters["versionHash"]
                if (versionHash.isNullOrEmpty()) {
                    call.respondText("version parameter missing", status = HttpStatusCode.BadRequest)
                    return@get
                }

                repoManager.runWithRepository(repositoryId) {
                    val tree = CLVersion.loadFromHash(versionHash, client.storeCache).getTree()
                    val rootNode = PNodeAdapter(ITree.ROOT_ID, TreePointer(tree))

                    call.respondHtmlTemplate(PageWithMenuBar("repos/", "../../../../..")) {
                        headContent {
                            title("Content Explorer")
                            link("../../../../../public/content-explorer.css", rel = "stylesheet")
                            script("text/javascript", src = "../../../../../public/content-explorer.js") {}
                        }
                        bodyContent { contentPageBody(rootNode, versionHash, emptySet()) }
                    }
                }
            }
            post<Paths.postVersionHash> {
                val repositoryId = call.parameters["repository"]?.let { RepositoryId(it) }
                if (repositoryId == null) {
                    call.respondText("repository parameter missing", status = HttpStatusCode.BadRequest)
                    return@post
                }
                val versionHash = call.parameters["versionHash"]
                if (versionHash.isNullOrEmpty()) {
                    call.respondText("version parameter missing", status = HttpStatusCode.BadRequest)
                    return@post
                }

                repoManager.runWithRepository(repositoryId) {
                    val expandedNodes = call.receive<ContentExplorerExpandedNodes>()

                    val tree = CLVersion.loadFromHash(versionHash, client.storeCache).getTree()
                    val rootNode = PNodeAdapter(ITree.ROOT_ID, TreePointer(tree))

                    var expandedNodeIds = expandedNodes.expandedNodeIds
                    if (expandedNodes.expandAll) {
                        expandedNodeIds = expandedNodeIds + collectExpandableChildNodes(rootNode, expandedNodes.expandedNodeIds)
                    }

                    call.respondText(
                        buildString {
                            appendHTML().ul("treeRoot") {
                                nodeItem(rootNode, expandedNodeIds)
                            }
                        },
                    )
                }
            }
            get<Paths.getNodeIdForVersionHash> {
                val id = call.parameters["nodeId"]?.toLongOrNull()
                    ?: return@get call.respondText("node id not found", status = HttpStatusCode.NotFound)

                val versionHash = call.parameters["versionHash"]
                    ?: return@get call.respondText("version hash not found", status = HttpStatusCode.NotFound)

                val repositoryId = call.parameters["repository"]
                    ?: return@get call.respondText("repository parameter missing", status = HttpStatusCode.BadRequest)

                repoManager.runWithRepository(RepositoryId(repositoryId)) {
                    val version = try {
                        CLVersion.loadFromHash(versionHash, client.storeCache)
                    } catch (ex: RuntimeException) {
                        return@runWithRepository call.respondText("version not found", status = HttpStatusCode.NotFound)
                    }

                    val node = PNodeAdapter(id, TreePointer(version.getTree())).takeIf { it.isValid }

                    if (node != null) {
                        call.respondHtml { body { nodeInspector(node) } }
                    } else {
                        call.respondText("node id not found", status = HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }

    // The method traverses the expanded tree based on the alreadyExpandedNodeIds and
    // collects the expandable (not empty) nodes which are not expanded yet
    private fun collectExpandableChildNodes(under: PNodeAdapter, alreadyExpandedNodeIds: Set<String>): Set<String> {
        if (alreadyExpandedNodeIds.contains(under.nodeId.toString())) {
            val expandableIds = mutableSetOf<String>()
            for (child in under.allChildren) {
                expandableIds.addAll(collectExpandableChildNodes(child as PNodeAdapter, alreadyExpandedNodeIds))
            }
            return expandableIds
        }

        if (under.allChildren.toList().isNotEmpty()) {
            // Node is collected if it is expandable
            return setOf(under.nodeId.toString())
        }
        return emptySet()
    }

    private fun FlowContent.contentPageBody(rootNode: PNodeAdapter, versionHash: String, expandedNodeIds: Set<String>) {
        h1 { +"Model Server Content" }
        small {
            style = "color: #888; text-align: center; margin-bottom: 15px;"
            +versionHash
        }
        div {
            style = "display: flex;"
            button(classes = "btn") {
                id = "expandAllBtn"
                +"Expand all"
            }
            button(classes = "btn") {
                id = "collapseAllBtn"
                +"Collapse all"
            }
        }
        div {
            id = "treeWrapper"
            ul("treeRoot") {
                nodeItem(rootNode, expandedNodeIds)
            }
        }
        div {
            id = "nodeInspector"
        }
    }

    private fun UL.nodeItem(node: PNodeAdapter, expandedNodeIds: Set<String>) {
        li("nodeItem") {
            val expanded = expandedNodeIds.contains(node.nodeId.toString())
            if (node.allChildren.toList().isNotEmpty()) {
                div(if (expanded) "expander expander-expanded" else "expander") { unsafe { +"&#x25B6;" } }
            }
            div("nameField") {
                attributes["data-nodeid"] = node.nodeId.toString()
                b {
                    val namePropertyUID = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getUID()
                    val namedConceptName = node.getPropertyValue(namePropertyUID)
                    if (namedConceptName != null) {
                        +namedConceptName
                    } else if (node.getPropertyRoles().contains("name")) {
                        +"${node.getPropertyValue("name")}"
                    } else {
                        +"Unnamed Node"
                    }
                }
                small { +" | ${node.nodeId} | $node" }
                br { }
                val conceptRef = node.getConceptReference()
                small {
                    if (conceptRef != null) {
                        +conceptRef.getUID()
                    } else {
                        +"No concept reference"
                    }
                }
            }
            div(if (expanded) "nested active" else "nested") {
                if (expanded) {
                    ul("nodeTree") {
                        for (child in node.allChildren) {
                            nodeItem(child as PNodeAdapter, expandedNodeIds)
                        }
                    }
                }
            }
        }
    }

    private fun BODY.nodeInspector(node: PNodeAdapter) {
        div {
            h3 { +"Node Details" }
        }
        val nodeEmpty = node.getReferenceRoles().isEmpty() && node.getPropertyRoles().isEmpty()
        if (nodeEmpty) {
            div { +"No roles." }
            return
        }
        if (node.getPropertyRoles().isEmpty()) {
            div { +"No properties." }
        } else {
            table {
                thead {
                    tr {
                        th { +"PropertyRole" }
                        th { +"Value" }
                    }
                }
                tbody {
                    for (propertyRole in node.getPropertyRoles()) {
                        tr {
                            td { +propertyRole }
                            td { +"${node.getPropertyValue(propertyRole)}" }
                        }
                    }
                }
            }
        }
        if (node.getReferenceRoles().isEmpty()) {
            div { +"No references." }
        } else {
            table {
                thead {
                    tr {
                        th { +"ReferenceRole" }
                        th { +"Target NodeId" }
                        th { +"Target Reference" }
                    }
                }
                tbody {
                    INodeResolutionScope.runWithAdditionalScope(node.getArea()) {
                        for (referenceRole in node.getReferenceRoles()) {
                            tr {
                                td { +referenceRole }
                                td {
                                    +"${(node.getReferenceTarget(referenceRole) as? PNodeAdapter)?.nodeId}"
                                }
                                td {
                                    +"${node.getReferenceTargetRef(referenceRole)?.serialize()}"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

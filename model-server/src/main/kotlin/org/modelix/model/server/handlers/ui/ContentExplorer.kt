package org.modelix.model.server.handlers.ui

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.html.respondHtml
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.request.receive
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.html.BODY
import kotlinx.html.FlowContent
import kotlinx.html.UL
import kotlinx.html.a
import kotlinx.html.b
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.getForm
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.i
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.small
import kotlinx.html.stream.appendHTML
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import org.modelix.authorization.checkPermission
import org.modelix.authorization.requiresLogin
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.ancestors
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.server.ModelServerPermissionSchema
import org.modelix.model.server.handlers.IRepositoriesManager
import org.modelix.model.server.handlers.NodeNotFoundException
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.StoreManager
import org.modelix.model.server.store.runReadIO
import org.modelix.model.server.templates.PageWithMenuBar

class ContentExplorer(private val repoManager: IRepositoriesManager) {

    private val stores: StoreManager get() = repoManager.getStoreManager()

    fun init(application: Application) {
        application.routing {
            get("/content") {
                call.respondRedirect("../repos/")
            }
            requiresLogin {
                get("/content/repositories/{repository}/branches/{branch}/latest") {
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
                    call.checkPermission(ModelServerPermissionSchema.repository(repository).branch(branch).pull)

                    @OptIn(RequiresTransaction::class)
                    val latestVersion = repoManager.getTransactionManager().runReadIO {
                        repoManager.getVersionHash(BranchReference(RepositoryId(repository), branch))
                    }
                    if (latestVersion == null) {
                        call.respondText("unable to find latest version", status = HttpStatusCode.InternalServerError)
                        return@get
                    } else {
                        call.respondRedirect("../../../versions/$latestVersion/")
                    }
                }
                get("/content/repositories/{repository}/versions/{versionHash}") {
                    val repositoryId = call.parameters["repository"]?.let { RepositoryId(it) }
                    if (repositoryId == null) {
                        call.respondText("repository parameter missing", status = HttpStatusCode.BadRequest)
                        return@get
                    }
                    call.checkPermission(ModelServerPermissionSchema.repository(repositoryId).objects.read)
                    val versionHash = call.parameters["versionHash"]
                    if (versionHash.isNullOrEmpty()) {
                        call.respondText("version parameter missing", status = HttpStatusCode.BadRequest)
                        return@get
                    }

                    // IMPORTANT Do not let `expandTo` be an arbitrary string to avoid code injection.
                    // The value of `expandTo` is expanded into JavaScript.
                    val expandTo = call.request.queryParameters["expandTo"]?.let {
                        NodeReference(it)
                    }

                    val version = repoManager.getTransactionManager().runReadIO {
                        repoManager.getVersion(repositoryId, versionHash)
                    }
                    if (version == null) {
                        call.respondText("version $versionHash not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val tree = version.getModelTree()
                    val model = tree.asModelSingleThreaded()
                    val rootNode = model.getRootNode()

                    val expandedNodes = expandTo?.let {
                        try {
                            model.tryResolveNode(it)
                        } catch (ex: IllegalArgumentException) {
                            return@get call.respondText("Invalid expandTo value. Provide a node id.", status = HttpStatusCode.BadRequest)
                        } ?: throw NodeNotFoundException("Node not found: $it")
                    }
                        ?.ancestors(true)
                        .orEmpty()
                        .map { it.getNodeReference() }
                        .toSet()

                    call.respondHtmlTemplate(PageWithMenuBar("repos/", "../../../../..")) {
                        headContent {
                            title("Content Explorer")
                            link("../../../../../public/content-explorer.css", rel = "stylesheet")
                            script("text/javascript", src = "../../../../../public/content-explorer.js") {}
                            if (expandTo != null) {
                                script("text/javascript") {
                                    unsafe {
                                        +"""
                                        document.addEventListener("DOMContentLoaded", function(event) {
                                            scrollToElement('$expandTo');
                                        });
                                        """.trimIndent()
                                    }
                                }
                            }
                        }
                        bodyContent { contentPageBody(rootNode, versionHash, expandedNodes, expandTo) }
                    }
                }
                post("/content/repositories/{repository}/versions/{versionHash}") {
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

                    call.checkPermission(ModelServerPermissionSchema.repository(repositoryId).objects.read)

                    val expandedNodes = call.receive<ContentExplorerExpandedNodes>()

                    val version = repoManager.getTransactionManager().runReadIO {
                        repoManager.getVersion(repositoryId, versionHash)
                    }
                    if (version == null) {
                        call.respondText("version $versionHash not found", status = HttpStatusCode.NotFound)
                        return@post
                    }
                    val tree = version.getModelTree()
                    val rootNode = tree.asModelSingleThreaded().getRootNode()

                    var expandedNodeIds: List<INodeReference> = expandedNodes.expandedNodeIds.mapNotNull { it.urlDecode() }.map { NodeReference(it) }
                    if (expandedNodes.expandAll) {
                        expandedNodeIds = expandedNodeIds + collectExpandableChildNodes(rootNode, expandedNodes.expandedNodeIds.map { NodeReference(it) }.toSet())
                    }

                    call.respondText(
                        buildString {
                            appendHTML().ul("treeRoot") {
                                nodeItem(rootNode, expandedNodeIds.toSet())
                            }
                        },
                    )
                }
                get("/content/repositories/{repository}/versions/{versionHash}/{nodeId}") {
                    val id = call.parameters["nodeId"]?.urlDecode()?.let { NodeReference(it) }
                        ?: return@get call.respondText("node id not found", status = HttpStatusCode.NotFound)

                    val versionHash = call.parameters["versionHash"]
                        ?: return@get call.respondText("version hash not found", status = HttpStatusCode.NotFound)

                    val repositoryId = call.parameters["repository"]
                        ?: return@get call.respondText("repository parameter missing", status = HttpStatusCode.BadRequest)

                    call.checkPermission(ModelServerPermissionSchema.repository(repositoryId).objects.read)

                    val version = repoManager.getTransactionManager().runReadIO {
                        repoManager.getVersion(RepositoryId(repositoryId), versionHash)
                    }
                    if (version == null) {
                        call.respondText("version $versionHash not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val node = version.getModelTree().asModelSingleThreaded().tryResolveNode(id)

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
    private fun collectExpandableChildNodes(under: IReadableNode, alreadyExpandedNodeIds: Set<INodeReference>): Set<INodeReference> {
        if (alreadyExpandedNodeIds.contains(under.getNodeReference())) {
            val expandableIds = mutableSetOf<INodeReference>()
            for (child in under.getAllChildren()) {
                expandableIds.addAll(collectExpandableChildNodes(child, alreadyExpandedNodeIds))
            }
            return expandableIds
        }

        if (under.getAllChildren().isNotEmpty()) {
            // Node is collected if it is expandable
            return setOf(under.getNodeReference())
        }
        return emptySet()
    }

    private fun FlowContent.contentPageBody(
        rootNode: IReadableNode,
        versionHash: String,
        expandedNodeIds: Set<INodeReference>,
        expandTo: INodeReference?,
    ) {
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
        getForm(action = ".") {
            label {
                htmlFor = "expandTo"
                +"Expand to Node: "
            }
            textInput {
                name = "expandTo"
                placeholder = "nodeId"
                required = true
            }
            submitInput(classes = "btn") {
                value = "Go"
            }
        }
        div {
            id = "treeWrapper"
            ul("treeRoot") {
                nodeItem(rootNode, expandedNodeIds, expandTo)
            }
        }
        div {
            id = "nodeInspector"
        }
    }

    private fun UL.nodeItem(node: IReadableNode, expandedNodeIds: Set<INodeReference>, expandTo: INodeReference? = null) {
        li("nodeItem") {
            id = node.getNodeReference().serialize()
            val expanded = expandedNodeIds.contains(node.getNodeReference())
            if (node.getAllChildren().toList().isNotEmpty()) {
                div(if (expanded) "expander expander-expanded" else "expander") { unsafe { +"&#x25B6;" } }
            }
            div("nameField") {
                if (expandTo == node.getNodeReference()) {
                    classes += "expandedToNameField"
                }
                attributes["data-nodeid"] = node.getNodeReference().serialize().urlEncode()
                val namePropertyUID = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()
                val nodeName = node.getPropertyValue(namePropertyUID)
                if (nodeName != null) {
                    b { +nodeName }
                } else {
                    i { +"<no name>" }
                }
                small { +" | ${node.getNodeReference().serialize()}" }
                br { }
                small {
                    +"Concept: "
                    +node.getConceptReference().getUID()
                }
            }
            div(if (expanded) "nested active" else "nested") {
                if (expanded) {
                    ul("nodeTree") {
                        for (child in node.getAllChildren()) {
                            nodeItem(child, expandedNodeIds, expandTo)
                        }
                    }
                }
            }
        }
    }

    private fun BODY.nodeInspector(node: IReadableNode) {
        div {
            h3 { +"Node Details" }
        }
        val nodeEmpty = node.getAllReferenceTargetRefs().isEmpty() && node.getAllProperties().isEmpty()
        if (nodeEmpty) {
            div { +"No roles." }
            return
        }
        if (node.getAllProperties().isEmpty()) {
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
                    for (property in node.getAllProperties()) {
                        tr {
                            td { +property.first.getNameOrId() }
                            td { +property.second }
                        }
                    }
                }
            }
        }
        if (node.getAllReferenceTargetRefs().isEmpty()) {
            div { +"No references." }
        } else {
            table {
                thead {
                    tr {
                        th { +"ReferenceRole" }
                        th { +"Target Reference" }
                    }
                }
                tbody {
                    for ((referenceRole, targetId) in node.getAllReferenceTargetRefs()) {
                        tr {
                            td { +referenceRole.getNameOrId() }
                            td {
                                if (node.getModel().tryResolveNode(targetId) == null) {
                                    +targetId.serialize()
                                } else {
                                    a("?expandTo=${targetId.serialize().urlEncode()}") {
                                        +targetId.serialize()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

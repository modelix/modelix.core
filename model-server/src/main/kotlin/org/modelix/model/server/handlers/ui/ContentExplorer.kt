package org.modelix.model.server.handlers.ui

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
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
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.TreePointer
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
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
                        it.toLongOrNull() ?: return@get call.respondText("Invalid expandTo value. Provide a node id.", status = HttpStatusCode.BadRequest)
                    }

                    val version = repoManager.getTransactionManager().runReadIO {
                        repoManager.getVersion(repositoryId, versionHash)
                    }
                    if (version == null) {
                        call.respondText("version $versionHash not found", status = HttpStatusCode.NotFound)
                        return@get
                    }
                    val tree = version.getTree()
                    val rootNode = PNodeAdapter(ITree.ROOT_ID, TreePointer(tree))

                    val expandedNodes = expandTo?.let { nodeId -> getAncestorsAndSelf(nodeId, tree) }.orEmpty()

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
                    val tree = version.getTree()
                    val rootNode = PNodeAdapter(ITree.ROOT_ID, TreePointer(tree))

                    var expandedNodeIds = expandedNodes.expandedNodeIds
                    if (expandedNodes.expandAll) {
                        expandedNodeIds = expandedNodeIds + collectExpandableChildNodes(rootNode, expandedNodes.expandedNodeIds.toSet())
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
                    val id = call.parameters["nodeId"]?.toLongOrNull()
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

    private fun getAncestorsAndSelf(expandTo: Long, tree: ITree): Set<String> {
        val seq = generateSequence(expandTo) { id ->
            try {
                tree.getParent(id).takeIf { it != 0L } // getParent returns 0L for root node
            } catch (e: org.modelix.datastructures.model.NodeNotFoundException) {
                throw NodeNotFoundException(id, e)
            }
        }
        return seq.map { it.toString() }.toSet()
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

    private fun FlowContent.contentPageBody(
        rootNode: PNodeAdapter,
        versionHash: String,
        expandedNodeIds: Set<String>,
        expandTo: Long?,
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

    private fun UL.nodeItem(node: PNodeAdapter, expandedNodeIds: Set<String>, expandTo: Long? = null) {
        li("nodeItem") {
            id = node.nodeId.toString()
            val expanded = expandedNodeIds.contains(node.nodeId.toString())
            if (node.allChildren.toList().isNotEmpty()) {
                div(if (expanded) "expander expander-expanded" else "expander") { unsafe { +"&#x25B6;" } }
            }
            div("nameField") {
                if (expandTo == node.nodeId) {
                    classes += "expandedToNameField"
                }
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
                            nodeItem(child as PNodeAdapter, expandedNodeIds, expandTo)
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
                                    val nodeId = (node.getReferenceTarget(referenceRole) as? PNodeAdapter)?.nodeId
                                    if (nodeId != null) {
                                        a("?expandTo=$nodeId") {
                                            +"$nodeId"
                                        }
                                    } else {
                                        +"null"
                                    }
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

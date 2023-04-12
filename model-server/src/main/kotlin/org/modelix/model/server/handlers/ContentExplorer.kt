package org.modelix.model.server.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.modelix.model.ModelFacade
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.TreePointer
import org.modelix.model.client.IModelClient
import org.modelix.model.lazy.CLVersion
import org.modelix.model.server.templates.PageWithMenuBar

class ContentExplorer(private val client: IModelClient, private val repoManager: RepositoriesManager) {

    private val rootNodes: List<PNodeAdapter>
        get() {
            val nodeList = mutableListOf<PNodeAdapter>()

            for (repoId in repoManager.getRepositories()) {
                val branchRef = repoId.getBranchReference()
                val version = ModelFacade.loadCurrentVersion(client, branchRef) ?: continue
                val rootNode = PNodeAdapter(ITree.ROOT_ID, TreePointer(version.getTree()))
                nodeList.add(rootNode)
            }
            return nodeList
        }

    private val allVersions: Set<CLVersion>
        get() {
            val versions = linkedSetOf<CLVersion>()
            for (repoId in repoManager.getRepositories()) {
                val hash = repoManager.getVersionHash(repoId.getBranchReference()) ?: continue
                val version = CLVersion(hash, client.storeCache)
                var current: CLVersion? = version
                while (current != null) {
                    versions.add(current)
                    current = current.baseVersion
                }
            }
            return versions
        }

    fun init(application: Application) {
        application.routing {
            get("/content/") {
                call.respondHtmlTemplate(PageWithMenuBar("content/", "..")) {
                    headContent {contentOverviewHead()}
                    content {contentOverviewBody()}
                }
            }
            get("/content/{versionHash}/") {
                val versionHash = call.parameters["versionHash"]
                if (versionHash.isNullOrEmpty()) {
                    call.respondText("version not found", status = HttpStatusCode.InternalServerError)
                    return@get
                }
                val tree = CLVersion.loadFromHash(versionHash, client.storeCache).getTree()
                val rootNode = PNodeAdapter(ITree.ROOT_ID, TreePointer(tree))
                call.respondHtmlTemplate(PageWithMenuBar("content/", "../..")) {
                    headContent {contentPageHead()}
                    content {contentPageBody(rootNode)}
                }
            }
            get("/content/{versionHash}/{nodeId}/") {
                val id = call.parameters["nodeId"]!!.toLong()
                var found: PNodeAdapter? = null
                for (node in rootNodes) {
                    val candidate = PNodeAdapter(id, node.branch).takeIf { it.isValid }
                    if (candidate != null) {
                        found = candidate
                        break
                    }
                }
                if (found == null) {
                    call.respondText("node id not found", status = HttpStatusCode.NotFound)
                } else {
                    call.respondHtml { body { nodeInspector(found) } }
                }
            }
        }
    }

    private fun HEAD.contentOverviewHead() {
        title("Content Explorer")
        style {
            unsafe {
                +"""
                body {
                    font-family: sans-serif;
                }
                """.trimIndent()
            }
        }
    }

    private fun FlowContent.contentOverviewBody() {
        h1 { +"Model Server Content" }
        h2 { +"Select a Version" }

        if (allVersions.isEmpty()) {
            span { +"No versions available." }
        } else {
            ul {
                for (version in allVersions) {
                    li {
                        a(href = "$version/"){+"$version"}
                    }
                }
            }
        }
    }

    private fun HEAD.contentPageHead() {
        title("Content Explorer")
        style {
            +"""
                body {
                    font-family: sans-serif;
                }
                table {
                  border-collapse: collapse;
                  font-family: sans-serif;
                  font-size: 0.9em;
                  border-radius:6px;
                }
                thead tr {
                  background-color: #009879;
                  color: #ffffff;
                  text-align: left;
                }
                th {
                  padding: 8px 10px;
                }
                td {
                  padding: 5px 10px;
                }
                tbody tr {
                  border-bottom: 1px solid #dddddd;
                  border-left: 1px solid #dddddd;
                  border-right: 1px solid #dddddd;
                }
                tbody tr:nth-of-type(even) {
                  background-color: #f3f3f3;
                }
                tbody tr:last-of-type
                  border-bottom: 2px solid #009879;
                }
                tbody tr.active-row {
                  font-weight: bold;
                  color: #009879;
                }
                .expander {
                    cursor: pointer;
                    margin-top: 12px;
                    font-size: 20px;
                    float: left;
                }
                .expander-expanded {
                    transition: .1s linear;
                    transform: rotate(90deg);
                    transform-origin: center center;
                }
                .nested {
                    display: none;
                    margin-left: 9px;
                    border-left: 1px solid #999999;
                }
                .active {
                    display: block;
                }
                .nodeTree {
                    list-style-type: none;
                }
                .treeRoot {
                    list-style-type: none;
                    margin-top: 5px;
                    float: left;
                }
                .referenceRoles {
                    margin-top: 5px;
                }
                .nodeItem {
                    padding-top: 10px;
                }
                .nameField {
                    margin-left: 30px;
                    padding: 5px;
                    border: 1px solid;
                    border-radius: 10px;
                    width: fit-content;
                    cursor: pointer;
                }
                .nameField:hover {
                    background-color: #e6e6e6;
                }
                .selectedNameField {
                    background-color: #c9c9c9;
                }
                #treeWrapper {
                    position: absolute;
                }
                #nodeInspector {
                    position: fixed;
                    top: 10%;
                    left: 65%;
                    width: 35%;
                    overflow-scroll;
                    z-index: 1;
                    display: none;
                    background-color: #ffffff;
                }
            """.trimIndent()
        }
        script("text/javascript") {
            unsafe {
                +"""
                async function createInspectorDetails(nodeId) {
                    let response = await window.fetch(window.location.pathname + nodeId + '/');
                    let nodeInspector = document.getElementById('nodeInspector');
                    nodeInspector.innerHTML = await response.text();
                    nodeInspector.style.display = 'block';
                }
                document.addEventListener('DOMContentLoaded', () => {
                    var expander = document.getElementsByClassName('expander');
                    var nameField = document.getElementsByClassName('nameField');
                    var expandAllBtn = document.getElementById('expandAllBtn');
                    var collapseAllBtn = document.getElementById('collapseAllBtn');
            
                    for (let i = 0; i < nameField.length; i++) {
                        nameField[i].addEventListener('click', function() {
                             let isSelected = this.classList.contains('selectedNameField');
                             if (isSelected) {
                                document.getElementById('nodeInspector').style.display = 'none';
                             } else {
                                createInspectorDetails(this.dataset.nodeid);
                             }
                             let selected = document.getElementsByClassName('selectedNameField');
                             for (let j = 0; j < selected.length; j++) {
                                selected[j].classList.remove('selectedNameField');
                             }
                             if (!isSelected) {
                                this.classList.add('selectedNameField');
                             }
                        });
                    }
            
                    for (let i = 0; i < expander.length; i++) {
                        expander[i].addEventListener('click', function() {
                            this.parentElement.querySelector(".nested").classList.toggle('active');
                            this.classList.toggle('expander-expanded');
                        });
                    }
            
                    expandAllBtn.addEventListener('click', function () {
                        var nested = document.getElementsByClassName("nested")
                        for (let i=0; i < nested.length; i++) {
                            nested[i].classList.add('active');
                        }
                        for (let i = 0; i < expander.length; i++) {
                            expander[i].classList.add('expander-expanded')
                        }
                    });
            
                    collapseAllBtn.addEventListener('click', function () {
                        var nested = document.getElementsByClassName('nested')
                        for (let i=0; i < nested.length; i++) {
                            nested[i].classList.remove('active');
                        }
                        for (let i = 0; i < expander.length; i++) {
                            expander[i].classList.remove('expander-expanded')
                        }
                    });
                });
            """.trimIndent()
            }
        }
    }

    private fun FlowContent.contentPageBody(rootNode: PNodeAdapter) {
        h1 {+"Model Server Content"}
        button {
            id = "expandAllBtn"
            +"Expand all"
        }
        button {
            id = "collapseAllBtn"
            +"Collapse all"
        }
        div {
            id = "treeWrapper"
            ul("treeRoot") {
                nodeItem(rootNode)
            }
        }
        div {
            id = "nodeInspector"
        }
    }

    private fun UL.nodeItem(node: PNodeAdapter) {
        li("nodeItem") {
            if (node.allChildren.toList().isNotEmpty()) {
                div("expander") { unsafe { +"&#x25B6;" } }
            }
            div("nameField") {
                attributes["data-nodeid"] = node.nodeId.toString()
                b {
                    if (node.getPropertyRoles().contains("name")) {
                        +"${node.getPropertyValue("name")}"
                    } else {
                        +"Unnamed Node"
                    }
                }
                small { +"(${node})" }
                br {  }
                val conceptRef = node.getConceptReference()
                small {
                    if (conceptRef != null) {
                        +conceptRef.getUID()
                    } else {
                        +"No concept reference"
                    }
                }

            }
            div("nested") {
                ul("nodeTree") {
                    for (child in node.allChildren) {
                        nodeItem(child as PNodeAdapter)
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
        table {
            thead {
                tr {
                    th { +"PropertyRole" }
                    th { +"Value" }
                }
            }
            for (propertyRole in node.getPropertyRoles()) {
                tr {
                    td { +propertyRole }
                    td { +(node.getPropertyValue(propertyRole) ?: "null") }
                }
            }
        }
        table {
            thead {
                tr {
                    th { +"ReferenceRole" }
                    th { +"Value" }
                }
            }
            for (referenceRole in node.getReferenceRoles()) {
                tr {
                    td { +"$referenceRole" }
                    td { +"Not yet implemented" }
                }
            }
        }
    }
}

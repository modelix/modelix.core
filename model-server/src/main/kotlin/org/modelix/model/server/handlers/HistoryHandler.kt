package org.modelix.model.server.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.TBODY
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.i
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.ul
import org.apache.commons.lang3.StringEscapeUtils
import org.modelix.authorization.KeycloakScope
import org.modelix.authorization.asResource
import org.modelix.authorization.getUserName
import org.modelix.authorization.requiresPermission
import org.modelix.model.LinearHistory
import org.modelix.model.api.*
import org.modelix.model.client.IModelClient
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CLVersion.Companion.createRegularVersion
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.metameta.MetaModelBranch
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.RevertToOp
import org.modelix.model.operations.applyOperation
import org.modelix.model.persistent.CPVersion.Companion.DESERIALIZER
import java.io.PrintWriter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoryHandler(val client: IModelClient, val repositoriesManager: RepositoriesManager) {


    fun init(application: Application) {
        application.routing {
            get("/history/") {
                call.respondHtml {
                    buildMainPage()
                }
            }
            get("/history/{repoId}/{branch}/") {
                val repositoryId = RepositoryId(call.parameters["repoId"]!!)
                val branch = repositoryId.getBranchReference(call.parameters["branch"]!!)
                val params = call.request.queryParameters
                val limit = toInt(params["limit"], 500)
                val skip = toInt(params["skip"], 0)
                call.respondHtml {
                    buildRepositoryPage(branch, params["head"], skip, limit)
                }
            }
            requiresPermission("history".asResource(), KeycloakScope.WRITE) {
                post("/history/{repoId}/{branch}/revert") {
                    val repositoryId = RepositoryId(call.parameters["repoId"]!!)
                    val branch = repositoryId.getBranchReference(call.parameters["branch"]!!)
                    val params = call.receiveParameters()
                    val fromVersion = params["from"]!!
                    val toVersion = params["to"]!!
                    val user = getUserName()
                    revert(branch, fromVersion, toVersion, user)
                    call.respondRedirect(".")
                }
//                post("/history/{repoId}/{branch}/delete") {
//                    val repositoryId = call.parameters["repoId"]!!
//                    val branch = call.parameters["branch"]!!
//                    client.put(RepositoryId(repositoryId).getBranchKey(branch), null)
//                    call.respondRedirect(".")
//                }
            }
        }
    }

    fun revert(repositoryAndBranch: BranchReference, from: String?, to: String?, author: String?) {
        val version = repositoriesManager.getVersion(repositoryAndBranch) ?: throw RuntimeException("Branch doesn't exist: $repositoryAndBranch")
        val branch = OTBranch(PBranch(version.tree, client.idGenerator), client.idGenerator, client.storeCache!!)
        branch.runWriteT { t ->
            t.applyOperation(RevertToOp(KVEntryReference(from!!, DESERIALIZER), KVEntryReference(to!!, DESERIALIZER)))
        }
        val (ops, tree) = branch.operationsAndTree
        val newVersion = createRegularVersion(
            client.idGenerator.generate(),
            LocalDateTime.now().toString(),
            author ?: "<server>",
            (tree as CLTree),
            version,
            ops.map { it.getOriginalOp() }.toTypedArray()
        )
        repositoriesManager.mergeChanges(repositoryAndBranch, newVersion.getContentHash())
    }

    private fun HTML.buildMainPage() {
        body {
            h1 { +"Choose Repository" }
            val repositories = repositoriesManager.getRepositories()
            if (repositories.isEmpty()) {
                p { i { +"No repositories available, add one" } }
            } else {
                table {
                    tr {
                        th { +"Repository" }
                        th { +"Branch" }
                    }
                    for (repository in repositories) {
                        val branches = repositoriesManager.getBranches(repository)
                        tr {
                            td {
                                rowSpan = branches.size.coerceAtLeast(1).toString()
                                +repository.id
                            }
                            if (branches.isEmpty()) {
                                td { }
                            } else {
                                for (branch in branches) {
                                    td {
                                        a {
                                            href = "${branch.repositoryId.id}/${branch.branchName}/"
                                            +branch.branchName
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

    private fun HTML.buildRepositoryPage(repositoryAndBranch: BranchReference, headHash: String?, skip: Int, limit: Int) {
        head {
            style {
                +"""
                    table {
                      border-collapse: collapse;
                      font-family: sans-serif;
                      margin: 25px 0;
                      font-size: 0.9em;
                      border-radius:6px;
                    }
                    thead tr {
                      background-color: #009879;
                      color: #ffffff;
                      text-align: left;
                    }
                    th {
                      padding: 12px 15px;
                    }
                    td {
                      padding: 3px 15px;
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
                    ul {
                      padding-left: 15px;
                    }
                    .hash {
                      color: #888;
                      white-space: nowrap;
                    }
                    .BtnGroup {
                      display: inline-block;
                      vertical-align: middle;
                    }
                    .BtnGroup-item {
                      background-color: #f6f8fa;
                      border: 1px solid rgba(27,31,36,0.15);
                      padding: 5px 16px;
                      position: relative;
                      float: left;
                      border-right-width: 0;
                      border-radius: 0;
                    }
                    .BtnGroup-item:first-child {
                      border-top-left-radius: 6px;
                      border-bottom-left-radius: 6px;
                    }
                    .BtnGroup-item:last-child {
                      border-right-width: 1px;
                      border-top-right-radius: 6px;
                      border-bottom-right-radius: 6px;
                    }
                """.trimIndent()
            }
        }
        body {
            val latestVersion = repositoriesManager.getVersion(repositoryAndBranch) ?: throw RuntimeException("Branch not found: $repositoryAndBranch")
            val headVersion = if (headHash == null || headHash.length == 0) latestVersion else CLVersion(headHash, client.storeCache!!)
            var rowIndex = 0
            h1 {
                +"History for Repository "
                +repositoryAndBranch.repositoryId.id
                +"/"
                +repositoryAndBranch.branchName
            }

//        out.append("""
//            <div>
//            <form action='delete' method='POST'>
//            <input type='submit' value='Delete'/>
//            </form>
//            </div>
//        """)

            fun buttons() {
                div("BtnGroup") {
                    if (skip == 0) {
                        a(classes = "BtnGroup-item") {
                            href = "?head=${latestVersion.getContentHash()}&skip=0&limit=$limit"
                            +"Newer"
                        }
                    } else {
                        a(classes = "BtnGroup-item") {
                            href = "?head=${headVersion.getContentHash()}&skip=${Math.max(0, skip - limit)}&limit=$limit"
                            +"Newer"
                        }
                    }
                    a(classes = "BtnGroup-item") {
                        href = "?head=${headVersion.getContentHash()}&skip=${skip + limit}&limit=$limit"
                        +"Older"
                    }
                }
            }
            buttons()
            table {
                thead {
                    tr {
                        th {
                            +"ID"
                            br {  }
                            +"Hash"
                        }
                        th { +"Author" }
                        th { +"Time" }
                        th { +"Operations" }
                        th { }
                    }
                }
                tbody {
                    var version: CLVersion? = headVersion
                    while (version != null) {
                        if (rowIndex >= skip) {
                            createTableRow(version, latestVersion)
                            if (version.isMerge()) {
                                for (v in LinearHistory(version.baseVersion!!.getContentHash()).load(version.getMergedVersion1()!!, version.getMergedVersion2()!!)) {
                                    createTableRow(v, latestVersion)
                                    rowIndex++
                                    if (rowIndex >= skip + limit) {
                                        break
                                    }
                                }
                            }
                        }
                        rowIndex++
                        if (rowIndex >= skip + limit) {
                            break
                        }
                        version = version.baseVersion
                    }
                }
            }
            buttons()
        }
    }

    private fun TBODY.createTableRow(version: CLVersion, latestVersion: CLVersion) {
        tr {
            td {
                +version.id.toString(16)
                br {  }
                span(classes = "hash") { +version.getContentHash() }
            }
            td {
                style = "white-space: nowrap;"
                text(version.author ?: "")
            }
            td {
                style = "white-space: nowrap;"
                +(version.getTimestamp()?.let { reformatTime(it) } ?: version.time ?: "")
            }
            td {
                if (version.isMerge()) {
                    +"merge ${version.getMergedVersion1()!!.id} + ${version.getMergedVersion2()!!.id} (base ${version.baseVersion})"
                } else {
                    if (version.operationsInlined()) {
                        ul {
                            for (operation in version.operations) {
                                li {
                                    +operation.toString()
                                }
                            }
                        }
                    } else {
                        +"(${version.numberOfOperations}) "
                    }
                }
            }
            td {
                form(action = "revert", method = FormMethod.post) {
                    hiddenInput(name = "from") { value = latestVersion.getContentHash() }
                    hiddenInput(name = "to") { value = version.getContentHash() }
                    submitInput { value = "Revert To" }
                }
            }
        }
    }

    private fun reformatTime(dateTime: Instant): String {
        return dateTime.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    private fun toInt(text: String?, defaultValue: Int): Int {
        try {
            if (!text.isNullOrEmpty()) {
                return text.toInt()
            }
        } catch (ex: NumberFormatException) {
        }
        return defaultValue
    }
}
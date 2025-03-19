package org.modelix.model.server.handlers.ui

import io.ktor.server.application.Application
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.HEAD
import kotlinx.html.TBODY
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.hiddenInput
import kotlinx.html.li
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
import kotlinx.html.unsafe
import org.modelix.authorization.checkPermission
import org.modelix.authorization.getUserName
import org.modelix.authorization.requiresLogin
import org.modelix.model.LinearHistory
import org.modelix.model.api.PBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CLVersion.Companion.createRegularVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.RevertToOp
import org.modelix.model.operations.applyOperation
import org.modelix.model.persistent.CPVersion
import org.modelix.model.server.ModelServerPermissionSchema
import org.modelix.model.server.handlers.BranchNotFoundException
import org.modelix.model.server.handlers.IRepositoriesManager
import org.modelix.model.server.handlers.getAsyncStore
import org.modelix.model.server.handlers.getLegacyObjectStore
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.StoreManager
import org.modelix.model.server.store.runReadIO
import org.modelix.model.server.store.runWriteIO
import org.modelix.model.server.templates.PageWithMenuBar
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoryHandler(private val repositoriesManager: IRepositoriesManager) {

    private val stores: StoreManager get() = repositoriesManager.getStoreManager()

    fun init(application: Application) {
        application.routing {
            get("/history") {
                call.respondRedirect("../repos/")
            }
            requiresLogin {
                get("/history/{repoId}/{branch}") {
                    val repositoryId = RepositoryId(call.parameters["repoId"]!!)
                    val branch = repositoryId.getBranchReference(call.parameters["branch"]!!)
                    call.checkPermission(ModelServerPermissionSchema.branch(branch).pull)
                    val params = call.request.queryParameters
                    val limit = toInt(params["limit"], 500)
                    val skip = toInt(params["skip"], 0)
                    val latestVersion =
                        @OptIn(RequiresTransaction::class)
                        repositoriesManager.getTransactionManager().runReadIO {
                            repositoriesManager.getVersion(branch)
                        }
                    checkNotNull(latestVersion) { "Branch not found: $branch" }
                    call.respondHtmlTemplate(PageWithMenuBar("repos/", "../../..")) {
                        headContent {
                            style {
                                unsafe {
                                    raw(
                                        """
                                    body {
                                        font-family: sans-serif;
                                    }
                                        """.trimIndent(),
                                    )
                                }
                            }
                            repositoryPageStyle()
                        }
                        bodyContent {
                            buildRepositoryPage(branch, latestVersion, params["head"], skip, limit)
                        }
                    }
                }
                post("/history/{repoId}/{branch}/revert") {
                    val repositoryId = RepositoryId(call.parameters["repoId"]!!)
                    val branch = repositoryId.getBranchReference(call.parameters["branch"]!!)
                    call.checkPermission(ModelServerPermissionSchema.branch(branch).write)
                    val params = call.receiveParameters()
                    val fromVersion = params["from"]!!
                    val toVersion = params["to"]!!
                    val user = getUserName()
                    @OptIn(RequiresTransaction::class)
                    repositoriesManager.getTransactionManager().runWriteIO {
                        revert(branch, fromVersion, toVersion, user)
                    }
                    call.respondRedirect(".")
                }
            }
//                post("/history/{repoId}/{branch}/delete") {
//                    val repositoryId = call.parameters["repoId"]!!
//                    val branch = call.parameters["branch"]!!
//                    client.put(RepositoryId(repositoryId).getBranchKey(branch), null)
//                    call.respondRedirect(".")
//                }
        }
    }

    @RequiresTransaction
    private fun revert(repositoryAndBranch: BranchReference, from: String?, to: String?, author: String?) {
        val version = repositoriesManager.getVersion(repositoryAndBranch) ?: throw BranchNotFoundException(repositoryAndBranch)
        val branch = OTBranch(PBranch(version.getTree(), stores.idGenerator), stores.idGenerator)
        branch.runWriteT { t ->
            val graph = repositoriesManager.getAsyncStore(repositoryAndBranch.repositoryId).asObjectGraph()
            t.applyOperation(RevertToOp(graph(from!!, CPVersion), graph(to!!, CPVersion)))
        }
        val (ops, tree) = branch.getPendingChanges()
        val newVersion = createRegularVersion(
            stores.idGenerator.generate(),
            LocalDateTime.now().toString(),
            author ?: "<server>",
            (tree as CLTree),
            version,
            ops.map { it.getOriginalOp() }.toTypedArray(),
        )
        repositoriesManager.mergeChanges(repositoryAndBranch, newVersion.getContentHash())
    }

    private fun HEAD.repositoryPageStyle() {
        style {
            unsafe {
                raw(
                    """
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
                      margin: 10px;
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
                    """.trimIndent(),
                )
            }
        }
    }

    private fun FlowContent.buildRepositoryPage(
        repositoryAndBranch: BranchReference,
        latestVersion: CLVersion,
        headHash: String?,
        skip: Int,
        limit: Int,
    ) {
        val headVersion = if (headHash.isNullOrEmpty()) {
            latestVersion
        } else {
            CLVersion.loadFromHash(
                headHash,
                repositoriesManager.getLegacyObjectStore(repositoryAndBranch.repositoryId),
            )
        }
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
                        br { }
                        +"Hash"
                    }
                    th { +"Author" }
                    th { +"Time" }
                    th { +"Operations" }
                    th {
                        colSpan = "3"
                        +"Actions"
                    }
                }
            }
            tbody {
                val versions = sequence<CLVersion> {
                    var version: CLVersion? = headVersion
                    while (version != null) {
                        yield(version)
                        if (version.isMerge()) {
                            for (v in LinearHistory(version.baseVersion!!.getContentHash()).load(version.getMergedVersion1()!!, version.getMergedVersion2()!!)) {
                                yield(v)
                                v.baseVersion?.let { yield(it) } // to include merge commits
                            }
                        }
                        version = version.baseVersion
                    }
                }.distinct().drop(skip).take(limit)

                var previous: CLVersion? = null
                for (version in versions) {
                    if (previous != null) {
                        createTableRow(repositoryAndBranch.repositoryId, previous, version, latestVersion)
                    }
                    previous = version
                }
                if (previous != null) {
                    createTableRow(repositoryAndBranch.repositoryId, previous, null, latestVersion)
                }
            }
        }
        buttons()
    }

    private fun TBODY.createTableRow(repositoryId: RepositoryId, version: CLVersion, nextVersion: CLVersion?, latestVersion: CLVersion) {
        tr {
            td {
                +version.id.toString(16)
                br { }
                span(classes = "hash") { +version.getContentHash() }

                if (nextVersion != null && version.baseVersion?.getContentHash() != nextVersion.getContentHash()) {
                    br { }
                    span(classes = "hash") {
                        +"parent: "
                        +version.baseVersion?.getContentHash().orEmpty()
                    }
                }
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
                    +"merge ${version.getMergedVersion1()} + ${version.getMergedVersion2()} (base ${version.baseVersion})"
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
                style = "white-space: nowrap;"
                val previousVersion = version.baseVersion
                if (previousVersion != null) {
                    a("/../../../diff/view?repository=$repositoryId&oldVersionHash=${previousVersion.getContentHash()}&newVersionHash=${version.getContentHash()}") {
                        +"Compare with Previous"
                    }
                }
            }

            td {
                style = "white-space: nowrap;"
                a("/../../../content/repositories/$repositoryId/versions/${version.getContentHash()}/") {
                    +"Explore Content"
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

package org.modelix.model.server.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.commons.lang3.StringEscapeUtils
import org.modelix.authorization.KeycloakScope
import org.modelix.authorization.asResource
import org.modelix.authorization.getUserName
import org.modelix.authorization.requiresPermission
import org.modelix.model.LinearHistory
import org.modelix.model.api.*
import org.modelix.model.client.IModelClient
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

class HistoryHandler(private val client: IModelClient) {

    fun init(application: Application) {
        application.routing {
            get("/history/") {
                call.respondTextWriter(contentType = ContentType.Text.Html) {
                    buildMainPage(PrintWriter(this))
                }
            }
            get("/history/{repoId}/{branch}/") {
                val repositoryId = call.parameters["repoId"]!!
                val branch = call.parameters["branch"]!!
                val params = call.request.queryParameters
                val limit = toInt(params["limit"], 500)
                val skip = toInt(params["skip"], 0)
                call.respondTextWriter(contentType = ContentType.Text.Html) {
                    buildRepositoryPage(PrintWriter(this), RepositoryAndBranch(repositoryId, branch), params["head"], skip, limit)
                }
            }
            requiresPermission("history".asResource(), KeycloakScope.WRITE) {
                post("/history/{repoId}/{branch}/revert") {
                    val repositoryId = call.parameters["repoId"]!!
                    val branch = call.parameters["branch"]!!
                    val params = call.receiveParameters()
                    val fromVersion = params["from"]!!
                    val toVersion = params["to"]!!
                    val user = getUserName()
                    revert(RepositoryAndBranch(repositoryId, branch), fromVersion, toVersion, user)
                    call.respondRedirect(".")
                }
                post("/history/{repoId}/{branch}/delete") {
                    val repositoryId = call.parameters["repoId"]!!
                    val branch = call.parameters["branch"]!!
                    client.put(RepositoryId(repositoryId).getBranchKey(branch), null)
                    call.respondRedirect(".")
                }
            }
        }
    }

    fun revert(repositoryAndBranch: RepositoryAndBranch, from: String?, to: String?, author: String?) {
        val versionHash = client[repositoryAndBranch.branchKey]
        val version = CLVersion(versionHash!!, client.storeCache!!)
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
        client.put(repositoryAndBranch.branchKey, newVersion.write())
    }

    val knownRepositoryIds: Set<RepositoryAndBranch>
        get() {
            val result: MutableSet<RepositoryAndBranch> = HashSet()
            val infoVersionHash = client[RepositoryId("info").getBranchKey()] ?: return emptySet()
            val infoVersion = CLVersion(infoVersionHash, client.storeCache!!)
            val infoBranch: IBranch = MetaModelBranch(PBranch(infoVersion.tree, IdGeneratorDummy()))
            infoBranch.runReadT { t: IReadTransaction ->
                for (infoNodeId in t.getChildren(ITree.ROOT_ID, "info")) {
                    for (repositoryNodeId in t.getChildren(infoNodeId, "repositories")) {
                        val repositoryId = t.getProperty(repositoryNodeId, "id") ?: continue
                        result.add(RepositoryAndBranch(repositoryId))
                        for (branchNodeId in t.getChildren(repositoryNodeId, "branches")) {
                            val branchName = t.getProperty(branchNodeId, "name") ?: continue
                            result.add(RepositoryAndBranch(repositoryId, branchName))
                        }
                    }
                }
            }
            return result
        }

    fun buildMainPage(out: PrintWriter) {
        val content = if (knownRepositoryIds.isEmpty()) {
            "<p><i>No repositories available, add one</i></p>"
        } else {
            """<ul>
                | ${knownRepositoryIds.map { repositoryAndBranch -> """
                <li>
                    <a href='${escapeURL(repositoryAndBranch.repository)}/${escapeURL(repositoryAndBranch.branch)}/'>${escape(repositoryAndBranch.toString())}</a>
                </li>
                """ }.joinToString("\n")}
                |</ul>
            """.trimMargin()
        }

        out.append("""
            <html>
                <head>
                    <style>
                    </style>
                </head>
                <body>
                    <h1>Choose Repository</h1>
                    $content
                </body>
            </html>
            """)
    }

    protected fun buildRepositoryPage(out: PrintWriter, repositoryAndBranch: RepositoryAndBranch, headHash: String?, skip: Int, limit: Int) {
        out.append("""
            <html>
            <head>
            <style>
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
            </style>
            </head>
            <body>
            """)
        val latestVersion = CLVersion(client[repositoryAndBranch.branchKey]!!, client.storeCache!!)
        val headVersion = if (headHash == null || headHash.length == 0) latestVersion else CLVersion(headHash, client.storeCache!!)
        var version: CLVersion? = headVersion
        var rowIndex = 0
        out.append("<h1>History for Repository ")
        out.append(escape(repositoryAndBranch.toString()))
        out.append("</h1>")

        out.append("""
            <div>
            <form action='delete' method='POST'>
            <input type='submit' value='Delete'/>
            </form>
            </div>
        """)

        val buttons = Runnable {
            out.append("<div class='BtnGroup'>")
            if (skip == 0) {
                out.append("<a class='BtnGroup-item' href='?head=" + escapeURL(latestVersion.hash) + "&skip=0&limit=" + limit + "'>Newer</a>")
            } else {
                out.append("<a class='BtnGroup-item' href='?head=" + escapeURL(headVersion.hash) + "&skip=" + Math.max(0, skip - limit) + "&limit=" + limit + "'>Newer</a>")
            }
            out.append("<a class='BtnGroup-item' href='?head=" + escapeURL(headVersion.hash) + "&skip=" + (skip + limit) + "&limit=" + limit + "'>Older</a>")
            out.append("</div>")
        }
        buttons.run()
        out.append("<table>")
        out.append("<thead><tr><th>ID<br/>Hash</th><th>Author</th><th>Time</th><th>Operations</th><th></th></tr></thead><tbody>")
        try {
            while (version != null) {
                if (rowIndex >= skip) {
                    createTableRow(out, version, latestVersion)
                    if (version.isMerge()) {
                        for (v in LinearHistory(version.baseVersion!!.hash).load(version.getMergedVersion1()!!, version.getMergedVersion2()!!)) {
                            createTableRow(out, v, latestVersion)
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
        } catch (ex: Exception) {
            out.append("""<tr><td colspan="5"><pre>${escape(ex.toString())}\n${ex.stackTraceToString()}</pre></td></tr>""")
        }
        out.append("</tbody></table>")
        buttons.run()
        out.append("</body>")
        out.append("</html>")
    }

    fun createTableRow(out: PrintWriter, version: CLVersion, latestVersion: CLVersion) {
        out.append("""
            <tr>
        <td>
            ${java.lang.Long.toHexString(version.id)}
            <br/>
            <span class='hash'>${version.hash}"</span>
        </td>
        <td>${nbsp(escape(version.author))}</td>
        <td style='white-space: nowrap;'>${escape(reformatTime(version.time))}</td>
        <td>""")
        val opsDescription = if (version.isMerge()) {
            "merge " + version.getMergedVersion1()!!.id + " + " + version.getMergedVersion2()!!.id + " (base " + version.baseVersion + ")"
        } else {
            if (version.operationsInlined()) {
                "<ul><li>" + version.operations.joinToString("</li><li>") { it.toString() } + "</li></ul>"
            } else {
                "(" + version.numberOfOperations + ") "
            }
        }
        out.append(opsDescription)
        out.append("""</td>
            <td>
            <form action='revert' method='POST'>
            <input type='hidden' name='from' value='${escapeURL(latestVersion.hash)}'/>
            <input type='hidden' name='to' value='${escapeURL(version.hash)}'/>
            <input type='submit' value='Revert To'/>
            </form>
            </td>
            </tr>
        """)
    }

    private fun reformatTime(dateTimeStr: String?): String {
        if (dateTimeStr == null) return ""
        val dateTime = LocalDateTime.parse(dateTimeStr)
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    private fun escape(text: String?): String? {
        return StringEscapeUtils.escapeHtml4(text)
    }

    private fun escapeURL(text: String?): String? {
        return URLEncoder.encode(text, StandardCharsets.UTF_8)
    }

    private fun nbsp(text: String?): String? {
        return text?.replace(" ", "&nbsp;")
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

    data class RepositoryAndBranch(val repository: String, val branch: String = RepositoryId.DEFAULT_BRANCH) {
        val branchKey: String
            get() = RepositoryId(repository).getBranchKey(branch)

        override fun toString() = "$repository/$branch"
    }
}
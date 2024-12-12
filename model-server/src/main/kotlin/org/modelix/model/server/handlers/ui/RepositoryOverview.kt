package org.modelix.model.server.handlers.ui

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLPathPart
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.html.Template
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.charsets.Charsets
import kotlinx.html.FlowContent
import kotlinx.html.FlowOrInteractiveOrPhrasingContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.h1
import kotlinx.html.html
import kotlinx.html.i
import kotlinx.html.onClick
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.unsafe
import org.modelix.authorization.hasPermission
import org.modelix.authorization.requiresLogin
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.ModelServerPermissionSchema
import org.modelix.model.server.handlers.IRepositoriesManager
import org.modelix.model.server.store.ITransactionManager
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.runReadIO
import org.modelix.model.server.templates.PageWithMenuBar

suspend fun <TTemplate : Template<HTML>> ApplicationCall.respondHtmlTemplateInTransaction(
    transactionManager: ITransactionManager,
    template: TTemplate,
    status: HttpStatusCode = HttpStatusCode.OK,
    body: TTemplate.() -> Unit,
) {
    val html = transactionManager.runReadIO {
        template.body()
        createHTML().html {
            with(template) { apply() }
        }
    }
    respond(TextContent(html, ContentType.Text.Html.withCharset(Charsets.UTF_8), status))
}

class RepositoryOverview(private val repoManager: IRepositoriesManager) {

    fun init(application: Application) {
        application.routing {
            requiresLogin {
                get("/repos") {
                    @OptIn(RequiresTransaction::class)
                    call.respondHtmlTemplateInTransaction(repoManager.getTransactionManager(), PageWithMenuBar("repos/", "..")) {
                        headContent {
                            title("Repositories")
                            script(type = "text/javascript") {
                                unsafe {
                                    +"""
                                    function removeBranch(repository, branch) {
                                        if (confirm('Are you sure you want to delete the branch ' + branch + ' of repository ' +repository + '?')) {
                                            fetch('../v2/repositories/' + repository + '/branches/' + branch, { method: 'DELETE'})
                                            .then( _ => location.reload())
                                        }
                                    }

                                    function removeRepository(repository) {
                                        if (confirm('Are you sure you want to delete the repository ' + repository + '?')) {
                                            fetch('../v2/repositories/' + repository + '/delete', { method: 'POST'})
                                            .then( _ => location.reload())
                                        }
                                    }
                                    """.trimIndent()
                                }
                            }
                        }
                        bodyContent { buildMainPage(call) }
                    }
                }
            }
        }
    }

    @RequiresTransaction
    private fun FlowContent.buildMainPage(call: ApplicationCall) {
        h1 { +"Choose Repository" }
        val repositories = repoManager.getRepositories().filter { call.hasPermission(ModelServerPermissionSchema.repository(it).list) }
        if (repositories.isEmpty()) {
            p { i { +"No repositories available, add one" } }
        } else {
            table {
                thead {
                    tr {
                        th {
                            colSpan = "2"
                            +"Repository"
                        }
                        th { +"Branch" }
                        th {
                            colSpan = "3"
                            +"Actions"
                        }
                    }
                }
                tbody {
                    for (repository in repositories) {
                        val branches = repoManager.getBranches(repository).filter { call.hasPermission(ModelServerPermissionSchema.branch(it).list) }
                        val repoRowSpan = branches.size.coerceAtLeast(1).plus(1).toString()
                        tr {
                            td {
                                rowSpan = repoRowSpan
                                +repository.id
                            }
                            td {
                                rowSpan = repoRowSpan
                                buildDeleteRepositoryForm(repository.id)
                            }
                        }
                        if (branches.isEmpty()) {
                            tr {
                                td { }
                                td { }
                                td { }
                                td { }
                            }
                        } else {
                            for (branch in branches) {
                                tr {
                                    td {
                                        span {
                                            +branch.branchName
                                        }
                                    }
                                    td {
                                        buildHistoryLink(repository.id, branch.branchName)
                                    }
                                    td {
                                        buildExploreLatestLink(repository.id, branch.branchName)
                                    }
                                    td {
                                        buildDeleteBranchButton(repository.id, branch.branchName)
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

internal fun FlowOrInteractiveOrPhrasingContent.buildHistoryLink(repositoryId: String, branchName: String) {
    a("../history/${repositoryId.encodeURLPathPart()}/${branchName.encodeURLPathPart()}/") {
        +"Show History"
    }
}

internal fun FlowOrInteractiveOrPhrasingContent.buildExploreLatestLink(repositoryId: String, branchName: String) {
    a("../content/repositories/${repositoryId.encodeURLPathPart()}/branches/${branchName.encodeURLPathPart()}/latest/") {
        +"Explore Latest Version"
    }
}

internal fun FlowContent.buildDeleteRepositoryForm(repositoryId: String) {
    button {
        name = "delete"
        formAction = "../v2/repositories/${repositoryId.encodeURLPathPart()}/delete"
        onClick = "return removeRepository('${repositoryId.encodeURLPathPart()}')"
        +"Delete Repository"
    }
}

internal fun FlowContent.buildDeleteBranchButton(repositoryId: String, branchName: String) {
    if (branchName == RepositoryId.DEFAULT_BRANCH) return
    button {
        onClick = "return removeBranch('${repositoryId.encodeURLPathPart()}', '${branchName.encodeURLPathPart()}')"
        +"Delete Branch"
    }
}

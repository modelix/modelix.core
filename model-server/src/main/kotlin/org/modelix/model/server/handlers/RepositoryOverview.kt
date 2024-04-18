package org.modelix.model.server.handlers

import io.ktor.http.encodeURLPathPart
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.resources.get
import io.ktor.server.routing.routing
import kotlinx.html.FlowContent
import kotlinx.html.FlowOrInteractiveOrPhrasingContent
import kotlinx.html.a
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.i
import kotlinx.html.p
import kotlinx.html.postButton
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import org.modelix.api.html.Paths
import org.modelix.model.server.templates.PageWithMenuBar

class RepositoryOverview(private val repoManager: IRepositoriesManager) {

    class RepositoryIdWithBranchNames(val repositoryId: String, val branchNames: Set<String>)

    fun init(application: Application) {
        application.routing {
            get<Paths.getRepos> {
                val repositoryIds = repoManager.getRepositories()
                val repositoryIdsWithBranchNames = repositoryIds.map { repositoryId ->
                    val branchNames = repoManager.getBranchNames(repositoryId)
                    RepositoryIdWithBranchNames(repositoryId.id, branchNames)
                }
                call.respondHtmlTemplate(PageWithMenuBar("repos/", "..")) {
                    headContent { title("Repositories") }
                    bodyContent { buildMainPage(repositoryIdsWithBranchNames) }
                }
            }
        }
    }

    private fun FlowContent.buildMainPage(repositoryIdsWithBranchNames: List<RepositoryIdWithBranchNames>) {
        h1 { +"Choose Repository" }
        if (repositoryIdsWithBranchNames.isEmpty()) {
            p { i { +"No repositories available, add one" } }
        } else {
            table {
                thead {
                    tr {
                        th { +"Repository" }
                        th { +"Branch" }
                        th {
                            colSpan = "3"
                            +"Actions"
                        }
                    }
                }
                tbody {
                    for (repositoryIdWithBranchNames in repositoryIdsWithBranchNames) {
                        val repositoryId = repositoryIdWithBranchNames.repositoryId
                        val branchNames = repositoryIdWithBranchNames.branchNames
                        tr {
                            td {
                                rowSpan = branchNames.size.coerceAtLeast(1).plus(1).toString()
                                +repositoryIdWithBranchNames.repositoryId
                            }
                        }
                        if (branchNames.isEmpty()) {
                            tr {
                                td { }
                                td { }
                                td { }
                                td { }
                            }
                        } else {
                            for (branchName in branchNames) {
                                tr {
                                    td {
                                        span {
                                            +branchName
                                        }
                                    }
                                    td {
                                        buildHistoryLink(repositoryId, branchName)
                                    }
                                    td {
                                        buildExploreLatestLink(repositoryId, branchName)
                                    }
                                    td {
                                        buildDeleteForm(repositoryId)
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

fun FlowOrInteractiveOrPhrasingContent.buildHistoryLink(repositoryId: String, branchName: String) {
    a("../history/${repositoryId.encodeURLPathPart()}/${branchName.encodeURLPathPart()}/") {
        +"Show History"
    }
}

fun FlowOrInteractiveOrPhrasingContent.buildExploreLatestLink(repositoryId: String, branchName: String) {
    a("../content/${repositoryId.encodeURLPathPart()}/${branchName.encodeURLPathPart()}/latest/") {
        +"Explore Latest Version"
    }
}

fun FlowContent.buildDeleteForm(repositoryId: String) {
    form {
        postButton {
            name = "delete"
            formAction = "../v2/repositories/${repositoryId.encodeURLPathPart()}/delete"
            +"Delete Repository"
        }
    }
}

package org.modelix.model.server.handlers

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.modelix.model.server.templates.PageWithMenuBar

class RepositoryOverview(private val repoManager: RepositoriesManager) {

    fun init(application: Application) {
        application.routing {
            get("/repos/") {
                call.respondHtmlTemplate(PageWithMenuBar("repos/", "..")) {
                    headContent { title("Repositories") }
                    bodyContent { buildMainPage() }
                }
            }
        }
    }

    private fun FlowContent.buildMainPage() {
        h1 { +"Choose Repository" }
        val repositories = repoManager.getRepositories()
        if (repositories.isEmpty()) {
            p { i { +"No repositories available, add one" } }
        } else {
            table {
                thead {
                    tr {
                        th { +"Repository" }
                        th { +"Branch" }
                        th {
                            colSpan = "2"
                            +"Actions"
                        }
                    }
                }
                tbody {
                    for (repository in repositories) {
                        val branches = repoManager.getBranches(repository)
                        tr {
                            td {
                                rowSpan = branches.size.coerceAtLeast(1).plus(1).toString()
                                +repository.id
                            }
                        }
                        if (branches.isEmpty()) {
                            tr {
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
                                        a("../history/${branch.repositoryId.id}/${branch.branchName}/") {
                                            +"Show History"
                                        }
                                    }
                                    td {
                                        val latestVersion = repoManager.getVersion(branch)
                                            ?: throw RuntimeException("Branch not found: $branch")
                                        a("../content/${latestVersion.getContentHash()}/") {
                                            +"Explore Latest Version"
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
}
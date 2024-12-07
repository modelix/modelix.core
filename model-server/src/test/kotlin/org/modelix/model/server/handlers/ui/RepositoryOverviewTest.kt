package org.modelix.model.server.handlers.ui

import kotlinx.html.span
import kotlinx.html.stream.createHTML
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryOverviewTest {

    @Test
    fun `history link is encoded properly`() {
        val html = createHTML(prettyPrint = false).span {
            buildHistoryLink("repository/v1", "branch/v2")
        }
        val document = Jsoup.parse(html)

        val href = document.getElementsByTag("a").first()?.attribute("href")?.value
        assertEquals("../history/repository%2Fv1/branch%2Fv2/", href)
    }

    @Test
    fun `explore latest link is encoded properly`() {
        val html = createHTML(prettyPrint = false).span {
            buildExploreLatestLink("repository/v1", "branch/v2")
        }
        val document = Jsoup.parse(html)

        val href = document.getElementsByTag("a").first()?.attribute("href")?.value
        assertEquals("../content/repositories/repository%2Fv1/branches/branch%2Fv2/latest/", href)
    }

    @Test
    fun `delete repository button parameters encoded properly`() {
        val html = createHTML(prettyPrint = false).span {
            buildDeleteRepositoryForm("repository/v1")
        }

        val document = Jsoup.parse(html)
        val onClick = document.getElementsByTag("button").first()?.attribute("onclick")?.value
        assertEquals("return removeRepository('repository%2Fv1')", onClick)
    }

    @Test
    fun `delete branch button parameters are encoded properly`() {
        val html = createHTML(prettyPrint = false).span {
            buildDeleteBranchButton("repository/v1", "branch/v2")
        }

        val document = Jsoup.parse(html)
        val onClick = document.getElementsByTag("button").first()?.attribute("onclick")?.value
        assertEquals("return removeBranch('repository%2Fv1', 'branch%2Fv2')", onClick)
    }
}

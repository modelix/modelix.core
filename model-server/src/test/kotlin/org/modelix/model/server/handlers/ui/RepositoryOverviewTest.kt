/*
 * Copyright (c) 2023-2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    fun `delete repository form action is encoded properly`() {
        val html = createHTML(prettyPrint = false).span {
            buildDeleteRepositoryForm("repository/v1")
        }

        val document = Jsoup.parse(html)
        val formAction = document.getElementsByTag("button").first()?.attribute("formaction")?.value
        assertEquals("../v2/repositories/repository%2Fv1/delete", formAction)
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

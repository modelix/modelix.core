/*
 * Copyright (c) 2023.
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

package org.modelix.model.server.handlers

import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlin.test.Test
import kotlin.test.assertEquals

class RepositoryOverviewTest {

    @Test
    fun testSlashesInPathSegmentsFromRepositoryIdAndBranchId() {
        val html = createHTML(prettyPrint = false).span {
            buildHistoryLink("repository/v1", "branch/v2")
            buildExploreLatestLink("repository/v1", "branch/v2")
            buildDeleteForm("repository/v1")
        }
        assertEquals(
            """
                <span>
                    <a href="../history/repository%2Fv1/branch%2Fv2/">Show History</a>
                    <a href="../content/repositories/repository%2Fv1/branches/branch%2Fv2/latest/">Explore Latest Version</a>
                    <form>
                        <button formmethod="post" name="delete" formaction="../v2/repositories/repository%2Fv1/delete">Delete Repository</button>
                    </form>
                </span>
            """.lines().joinToString("") { it.trim() },
            html,
        )
    }
}

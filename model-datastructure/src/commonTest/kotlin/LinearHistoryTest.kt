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

import org.modelix.model.LinearHistory
import org.modelix.model.VersionMerger
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.operations.IOperation
import org.modelix.model.persistent.MapBasedStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinearHistoryTest {
    private val initialTree = CLTree.builder(ObjectStoreCache(MapBasedStore())).repositoryId("LinearHistoryTest").build()

    @Test
    fun noCommonHistory() {
        val v20 = version(20, null)
        val v21 = version(21, null)

        assertHistory(v20, v21, listOf(v20, v21))
    }

    @Test
    fun divergedByTwoCommits() {
        val v10 = version(10, null)
        val v20 = version(20, v10)
        val v21 = version(21, v10)

        assertHistory(v20, v21, listOf(v20, v21))
    }

    @Test
    fun divergedWithTwoCommitsInCommonBase() {
        val v1 = version(1, null)
        val v10 = version(10, v1)
        val v20 = version(20, v10)
        val v21 = version(21, v10)

        val actual = LinearHistory(null).load(v20, v21).map { it.id }
        val expected = listOf(1L, 10L, 20, 21)
        assertEquals(expected, actual)
    }

    @Test
    fun correctHistoryIfIdsAreNotAscending() {
        val v1 = version(1, null)
        val v2 = version(2, v1)
        val v3 = version(3, v1)
        val v9 = version(9, v2)
        val v4 = merge(4, v2, v3)
        val v8 = version(8, v9)

        val expected = listOf(v2, v9, v8, v3)
        assertHistory(v4, v8, expected)
    }

    private fun assertHistory(v1: CLVersion, v2: CLVersion, expected: List<CLVersion>) {
        val historyMergingFirstIntoSecond = history(v1, v2)
        val historyMeringSecondIntoFirst = history(v2, v1)
        assertEquals(
            historyMergingFirstIntoSecond.map { it.id.toString(16) },
            historyMeringSecondIntoFirst.map { it.id.toString(16) },
        )
        assertEquals(expected.map { it.id.toString(16) }, historyMergingFirstIntoSecond.map { it.id.toString(16) })
    }

    private fun history(v1: CLVersion, v2: CLVersion): List<CLVersion> {
        val base = VersionMerger.commonBaseVersion(v1, v2)
        val history = LinearHistory(base?.getContentHash()).load(v1, v2)
        assertHistoryIsCorrect(history)
        return history
    }

    private fun assertHistoryIsCorrect(history: List<CLVersion>) {
        history.forEach { version ->
            val versionIndex = history.indexOf(version)
            getDescendants(version).forEach { descendent ->
                val descendantIndex = history.indexOf(descendent)
                // A descendant might not be in history
                // (1) if it is a merge or
                // (2) if it is a descendant of a common version.
                // The descendantIndex is then -1.
                assertTrue(
                    versionIndex > descendantIndex,
                    "${version.id.toString(16)} must come after its descendant ${descendent.id.toString(16)} in ${history.map { it.id.toString() }} .",
                )
            }
        }
    }

    private fun getChildren(version: CLVersion): List<CLVersion> {
        return if (version.isMerge()) {
            listOf(version.getMergedVersion1()!!, version.getMergedVersion2()!!)
        } else {
            listOfNotNull(version.baseVersion)
        }
    }

    private fun getDescendants(version: CLVersion): MutableSet<CLVersion> {
        val descendants = mutableSetOf<CLVersion>()
        getChildren(version).forEach { descendant ->
            descendants.add(descendant)
            descendants.addAll(getDescendants(descendant))
        }
        return descendants
    }

    private fun version(id: Long, base: CLVersion?): CLVersion {
        return CLVersion.createRegularVersion(
            id,
            null,
            null,
            initialTree,
            base,
            emptyArray(),
        )
    }

    private fun merge(id: Long, v1: CLVersion, v2: CLVersion): CLVersion {
        return merge(id, VersionMerger.Companion.commonBaseVersion(v1, v2)!!, v1, v2)
    }

    private fun merge(id: Long, base: CLVersion, v1: CLVersion, v2: CLVersion): CLVersion {
        return CLVersion.createAutoMerge(
            id,
            initialTree,
            base,
            v1,
            v2,
            emptyArray<IOperation>(),
            initialTree.store,
        )
    }
}

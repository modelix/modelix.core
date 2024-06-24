/*
 * Copyright (c) 2024.
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

package org.modelix.model.server.store

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.modelix.model.lazy.RepositoryId
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryIsolatingStoreTest {

    private val store = InMemoryStoreClient()

    @Nested
    inner class RemoveRepositoryObjectsTest {
        @Test
        fun `repository data is removed successfully`() {
            val repoId = RepositoryId("abc")
            val entries = mapOf(
                ObjectInRepository(repoId.id, "key0") to "value0",
                ObjectInRepository(repoId.id, "key1") to "value1",
            )
            store.putAll(entries)

            store.removeRepositoryObjects(repoId)

            assertTrue { store.getAll().isEmpty() }
        }

        @Test
        fun `data of other repositories stays intact`() {
            val existingId = RepositoryId("existing")
            val existingEntries = mapOf(
                ObjectInRepository(existingId.id, "key0") to "value0",
                ObjectInRepository(existingId.id, "key1") to "value1",
            )
            store.putAll(existingEntries)

            val toDeleteId = RepositoryId("toDelete")
            val toDeleteEntries = mapOf(
                ObjectInRepository(toDeleteId.id, "key0") to "value0",
                ObjectInRepository(toDeleteId.id, "key1") to "value1",
            )
            store.putAll(toDeleteEntries)
            store.removeRepositoryObjects(toDeleteId)

            assertEquals(existingEntries, store.getAll())
        }

        @Test
        fun `removing a non-existing repository does not throw`() {
            assertDoesNotThrow {
                store.removeRepositoryObjects(RepositoryId("invalid"))
            }
        }
    }

    @AfterTest
    fun cleanup() {
        store.close()
    }
}

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
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.store.IgniteStoreClient
import org.modelix.model.server.store.ObjectInRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IgnitePostgresTest {
    lateinit var store: IgniteStoreClient

    @BeforeTest
    fun beforeTest() {
        store = IgniteStoreClient()
    }

    @AfterTest
    fun afterTest() {
        store.dispose()
    }

    @Test
    fun `store immutable object in repository`() {
        val value = "immutable value " + System.nanoTime()
        val hash = HashUtil.sha256(value)
        val repositoryId = RepositoryId("repo1")
        val key = ObjectInRepository(repositoryId.id, hash)

        assertEquals(null, store.get(key))
        store.put(key, value)
        assertEquals(value, store.get(key))

        assertEquals(null, store.get(ObjectInRepository.global(hash)))
    }

    @Test
    fun `store immutable object in global storage`() {
        val value = "immutable value " + System.nanoTime()
        val hash = HashUtil.sha256(value)
        val key = ObjectInRepository.global(hash)

        assertEquals(null, store.get(key))
        store.put(key, value)
        assertEquals(value, store.get(key))
    }

    @Test
    fun `store mutable object in repository`() {
        val value = "mutable value " + System.nanoTime()
        val hash = "mutable key " + System.nanoTime()
        val repositoryId = RepositoryId("repo1")
        val key = ObjectInRepository(repositoryId.id, hash)

        assertEquals(null, store.get(key))
        store.put(key, value)
        assertEquals(value, store.get(key))

        assertEquals(null, store.get(ObjectInRepository.global(hash)))
    }

    @Test
    fun `store mutable object in global storage`() {
        val value = "mutable value " + System.nanoTime()
        val hash = "mutable key " + System.nanoTime()
        val key = ObjectInRepository.global(hash)

        assertEquals(null, store.get(key))
        store.put(key, value)
        assertEquals(value, store.get(key))
    }

    @Test
    fun `read mutable legacy entry`() {
        val key = ObjectInRepository.global(":v2:repositories")
        assertEquals("courses", store.get(key))
    }

    @Test
    fun `overwrite mutable legacy entry`() {
        val key = ObjectInRepository.global(":v2:repositories:courses:branches")
        assertEquals("master", store.get(key))
        store.put(key, "new value")
        assertEquals("new value", store.get(key))
    }

    @Test
    fun `delete overwritten mutable legacy entry`() {
        val key = ObjectInRepository.global(":v2:repositories:courses:branches:master")
        assertEquals("7fQeo*xrdfZuHZtaKhbp0OosarV5tVR8N3pW8JPkl7ZE", store.get(key))
        store.put(key, "new value")
        assertEquals("new value", store.get(key))
        store.put(key, null)
        assertEquals(null, store.get(key))
    }

    @Test
    fun `read immutable legacy entry`() {
        val key = ObjectInRepository.global("2YAqw*tJWjb2t2RMO8ypIvHn8QpHjwmIUdhdFygV9lUE")
        assertEquals("S/5/0/W7HBQ*K6ZbUt76ti7r2pTscLVsWd8oiqIOIsKTpQB8Aw", store.get(key))
    }
}

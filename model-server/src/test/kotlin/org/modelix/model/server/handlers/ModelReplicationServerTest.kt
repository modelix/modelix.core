/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.server.handlers

import io.ktor.client.request.get
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.IConceptReference
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.readVersionDelta
import org.modelix.model.client2.runWrite
import org.modelix.model.client2.useVersionStreamFormat
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.installDefaultServerPlugins
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import kotlin.test.Test
import kotlin.test.fail

class ModelReplicationServerTest {

    private fun runTest(block: suspend ApplicationTestBuilder.(scope: CoroutineScope) -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            installDefaultServerPlugins()
            val repositoriesManager = RepositoriesManager(LocalModelClient(InMemoryStoreClient()))
            ModelReplicationServer(repositoriesManager).init(this)
            KeyValueLikeModelServer(repositoriesManager).init(this)
        }

        coroutineScope {
            block(this)
        }
    }

    @Test
    fun `pulling delta does not return objects twice`() = runTest {
        // Arrange
        val url = "http://localhost/v2"
        val modelClient = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        val branchId = repositoryId.getBranchReference("my-branch")
        // By calling modelClient.runWrite twice, we create to versions.
        // Those two versions will share objects.
        modelClient.runWrite(branchId) { root ->
            root.addNewChild("aChild", -1, null as IConceptReference?)
        }
        modelClient.runWrite(branchId) { root ->
            root.addNewChild("aChild", -1, null as IConceptReference?)
        }

        // Act
        val response = client.get {
            url {
                takeFrom(url)
                appendPathSegments("repositories", repositoryId.id, "branches", branchId.branchName)
            }
            useVersionStreamFormat()
        }
        val versionDelta = response.readVersionDelta()

        // Assert
        val seenHashes = mutableSetOf<String>()
        versionDelta.getObjectsAsFlow().collect { (hash, _) ->
            val wasSeenBefore = !seenHashes.add(hash)
            if (wasSeenBefore) {
                // We should not send the same object (with the same hash more than once)
                // even if we got with versions that share data.
                fail("Hash $hash sent more than once.")
            }
        }
    }
}

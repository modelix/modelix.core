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

package org.modelix.model.server

import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.authorization.ModelixAuthorization
import org.modelix.authorization.createModelixAccessToken
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.modelServerSchema
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.forContextRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class PermissionsTest {

    private val jwtSignatureKey = "xyz"

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ModelixAuthorization) {
                permissionSchema = modelServerSchema
                hmac512Key = jwtSignatureKey
            }
            installDefaultServerPlugins()
            ModelReplicationServer(InMemoryStoreClient().forContextRepository()).init(this)
        }
        block()
    }

    suspend fun ApplicationTestBuilder.createModelClientWithPermissions(vararg permissions: PermissionParts): ModelClientV2 {
        val url = "http://localhost/v2"
        return ModelClientV2.builder()
            .url(url)
            .client(client)
            .authToken {
                createModelixAccessToken(
                    hmac512key = jwtSignatureKey,
                    user = "test",
                    grantedPermissions = permissions.map { it.fullId },
                )
            }
            .build().also { it.init() }
    }

    @Test
    fun `cannot create repository`() = runTest {
        val repositoryId = RepositoryId("repo1")
        val client = createModelClientWithPermissions()

        assertFails {
            client.initRepository(repositoryId)
        }
    }

    @Test
    fun `can create repository`() = runTest {
        val repositoryId = RepositoryId("repo1")
        val client = createModelClientWithPermissions(
            PermissionParts("repository", repositoryId.id, "create"),
        )

        client.initRepository(repositoryId)
    }

    @Test
    fun `can list repositories`() = runTest {
        assertEquals(emptyList(), createModelClientWithPermissions().listRepositories())

        createModelClientWithPermissions(
            PermissionParts("repository", "repo1", "create"),
        ).initRepository(RepositoryId("repo1"))

        // not visible without permission to list the repository
        assertEquals(emptyList(), createModelClientWithPermissions().listRepositories())

        // visible with correct permission
        assertEquals(
            listOf("repo1"),
            createModelClientWithPermissions(
                PermissionParts("repository", "repo1", "list"),
            ).listRepositories().map { it.id },
        )

        // not visible with list permission on other repository
        assertEquals(
            emptyList(),
            createModelClientWithPermissions(
                PermissionParts("repository", "repo2", "list"),
            ).listRepositories().map { it.id },
        )

        // visible with indirect permission
        assertEquals(
            listOf("repo1"),
            createModelClientWithPermissions(
                PermissionParts("repository", "repo1", "read"),
            ).listRepositories().map { it.id },
        )
    }

    @Test
    fun `can write to branch`() = runTest {
        val repositoryId = RepositoryId("repo1")
        val masterBranch = repositoryId.getBranchReference()
        val branch = repositoryId.getBranchReference("branch1")
        createModelClientWithPermissions(
            PermissionParts("repository", repositoryId.id, "create"),
        ).initRepository(repositoryId)
        val client = createModelClientWithPermissions(
            PermissionParts("repository", repositoryId.id, "branch", masterBranch.branchName, "read"),
            PermissionParts("repository", repositoryId.id, "branch", branch.branchName, "write"),
        )

        val initialVersion = client.pull(masterBranch, null)
        client.push(branch, initialVersion, initialVersion)

        assertEquals(client.pullHash(masterBranch), client.pullHash(branch))
    }

    @Test
    fun `cannot write to branch`() = runTest {
        val repositoryId = RepositoryId("repo1")
        val masterBranch = repositoryId.getBranchReference()
        val branch = repositoryId.getBranchReference("branch1")
        createModelClientWithPermissions(
            PermissionParts("repository", repositoryId.id, "create"),
        ).initRepository(repositoryId)

        val initialVersion = createModelClientWithPermissions(
            PermissionParts("repository", repositoryId.id, "branch", masterBranch.branchName, "read"),
        ).pull(masterBranch, null)

        // no permission in the branch
        assertFails {
            createModelClientWithPermissions(
                PermissionParts("repository", repositoryId.id, "branch", masterBranch.branchName, "read"),
            ).push(branch, initialVersion, initialVersion)
        }

        // only read permission on the branch
        assertFails {
            createModelClientWithPermissions(
                PermissionParts("repository", repositoryId.id, "branch", masterBranch.branchName, "read"),
                PermissionParts("repository", repositoryId.id, "branch", branch.branchName, "read"),
            ).push(branch, initialVersion, initialVersion)
        }
    }
}

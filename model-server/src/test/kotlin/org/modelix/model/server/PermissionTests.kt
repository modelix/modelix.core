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

package org.modelix.model.server

import com.auth0.jwt.JWTCreator
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.authorization.ModelixAuthorization
import org.modelix.authorization.createModelixAccessToken
import org.modelix.authorization.permissions.modelServerSchema
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.forContextRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

private const val JWT_SIGNATURE_KEY = "FnxLlWowL6"

class PermissionTests {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ModelixAuthorization) {
                hmac512Key = JWT_SIGNATURE_KEY
                permissionSchema = modelServerSchema
            }
            installDefaultServerPlugins(unitTestMode = false)
            ModelReplicationServer(InMemoryStoreClient().forContextRepository()).init(this)
        }
        block()
    }

    suspend fun ApplicationTestBuilder.createModelClient(vararg grantedPermissions: String): ModelClientV2 {
        return createModelClient(grantedPermissions = grantedPermissions.toList())
    }

    suspend fun ApplicationTestBuilder.createModelClient(grantedPermissions: List<String>, hmac512key: String = JWT_SIGNATURE_KEY, tokenModifier: (JWTCreator.Builder) -> Unit = {}): ModelClientV2 {
        val url = "http://localhost/v2"
        return ModelClientV2.builder().url(url).client(
            client.config {
                install(Auth) {
                    bearer {
                        loadTokens {
                            val token = createModelixAccessToken(
                                hmac512key = hmac512key,
                                user = "unit-tests@example.com",
                                grantedPermissions = grantedPermissions,
                                additionalTokenContent = tokenModifier,
                            )
                            BearerTokens(token, "")
                        }
                    }
                }
            },
        ).build().also {
            try {
                it.init()
            } catch (ex: Exception) {
                Exception("Client initialization failed", ex).printStackTrace()
            }
        }
    }

    private suspend fun assertPermissionDenied(body: suspend () -> Unit) {
        val ex = assertFails {
            body()
        }
        assertEquals(ClientRequestException::class, ex::class)
        assertEquals(HttpStatusCode.Forbidden, (ex as ClientRequestException).response.status)
    }

    private suspend fun assertUnauthorized(body: suspend () -> Unit) {
        val ex = assertFails {
            body()
        }
        assertEquals(ClientRequestException::class, ex::class)
        assertEquals(HttpStatusCode.Unauthorized, (ex as ClientRequestException).response.status)
    }

    @Test
    fun `can create repository`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient("repository/${repoId.id}/write")

        assertEquals(emptyList(), client.listRepositories())

        client.initRepository(repoId)

        assertEquals(listOf(repoId), client.listRepositories())
    }

    @Test
    fun `cannot create repository with missing permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val someOtherRepoId = RepositoryId("repo2")
        val client = createModelClient("repository/${someOtherRepoId.id}/write")

        assertEquals(emptyList(), client.listRepositories())

        assertPermissionDenied {
            client.initRepository(repoId)
        }
    }

    @Test
    fun `cannot create repository with read permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient("repository/${repoId.id}/read")

        assertEquals(emptyList(), client.listRepositories())

        assertPermissionDenied {
            client.initRepository(repoId)
        }
    }

    @Test
    fun `can create branch`() = runTest {
        val repoId = RepositoryId("repo1")
        val clientA = createModelClient("repository/${repoId.id}/write")
        val initialVersion = clientA.initRepository(repoId)
        assertEquals(setOf(repoId.getBranchReference()), clientA.listBranches(repoId).toSet())

        val branch = repoId.getBranchReference("branch1")
        val clientB = createModelClient("repository/${repoId.id}/branch/${branch.branchName}/write")

        // writing a branch includes reading the repo
        assertEquals(setOf(repoId), clientB.listRepositories().toSet())

        // master branch is not listed, because of the missing permission
        assertEquals(setOf(), clientB.listBranches(repoId).toSet())

        clientB.push(branch, initialVersion, initialVersion)

        // after creating the new branch it should be visible, but master is still hidden
        assertEquals(setOf(branch), clientB.listBranches(repoId).toSet())

        // clientA has permission on the whole repository and should see all branches
        assertEquals(setOf(repoId.getBranchReference(), branch), clientA.listBranches(repoId).toSet())
    }

    @Test
    fun `cannot create branch without permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val clientA = createModelClient("repository/${repoId.id}/write")
        val initialVersion = clientA.initRepository(repoId)
        assertEquals(setOf(repoId.getBranchReference()), clientA.listBranches(repoId).toSet())

        val masterBranch = repoId.getBranchReference()
        val branch1 = repoId.getBranchReference("branch1")
        val clientB = createModelClient("repository/${repoId.id}/branch/${masterBranch.branchName}/write")

        // clientB has write permission on the master branch ...
        clientB.push(masterBranch, initialVersion, initialVersion)

        // ... but no write permission on other branches
        assertPermissionDenied {
            clientB.push(branch1, initialVersion, initialVersion)
        }
    }

    @Test
    fun `can delete a repository`() = runTest {
        val repoId = RepositoryId("repo1")
        val clientA = createModelClient("repository/${repoId.id}/rewrite")

        val initialVersion = clientA.initRepository(repoId)
        assertEquals(setOf(repoId), clientA.listRepositories().toSet())

        clientA.deleteRepository(repoId)
        assertEquals(setOf(), clientA.listRepositories().toSet())
    }

    @Test
    fun `cannot delete a repository without permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val someOtherRepoId = RepositoryId("repo2")
        val clientA = createModelClient("repository/${repoId.id}/write")
        clientA.initRepository(repoId)
        assertEquals(setOf(repoId.getBranchReference()), clientA.listBranches(repoId).toSet())

        val clientB = createModelClient("repository/${someOtherRepoId.id}/rewrite")

        // TODO inconsistent API. All other methods throw an exception if the permission is missing.
        assertFalse(clientB.deleteRepository(repoId))
    }

    @Test
    fun `cannot delete a repository with only write permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val clientA = createModelClient("repository/${repoId.id}/write")
        clientA.initRepository(repoId)
        assertEquals(setOf(repoId.getBranchReference()), clientA.listBranches(repoId).toSet())

        val clientB = createModelClient("repository/${repoId.id}/write")

        assertFalse(clientB.deleteRepository(repoId))
    }

    @Test
    fun `cannot create repository with expired token`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient(grantedPermissions = listOf("repository/${repoId.id}/write")) { jwt ->
            jwt.withExpiresAt(Instant.now().minusSeconds(300))
        }

        assertUnauthorized {
            client.initRepository(repoId)
        }
    }

    @Test
    fun `cannot create repository with invalid signature`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient(grantedPermissions = listOf("repository/${repoId.id}/write"), hmac512key = "xxx")

        assertUnauthorized {
            client.initRepository(repoId)
        }
    }

    @Test
    fun `can create repository with non-expired token`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient(grantedPermissions = listOf("repository/${repoId.id}/write")) { jwt ->
            jwt.withExpiresAt(Instant.now().plusSeconds(10))
        }

        client.initRepository(repoId)
        assertEquals(listOf(repoId), client.listRepositories())
    }
}

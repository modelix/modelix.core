package org.modelix.model.server

import com.nimbusds.jwt.JWTClaimsSet
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
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class PermissionsTest {

    private val jwtSignatureKey = "xyz"

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ModelixAuthorization) {
                permissionSchema = ModelServerPermissionSchema.SCHEMA
                hmac512Key = jwtSignatureKey
            }
            installDefaultServerPlugins()
            val storeClient = InMemoryStoreClient()
            val repositoriesManager = RepositoriesManager(storeClient)
            ModelReplicationServer(repositoriesManager).init(this)
            IdsApiImpl(repositoriesManager).init(this)
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

    suspend fun ApplicationTestBuilder.createModelClient(vararg grantedPermissions: PermissionParts): ModelClientV2 {
        return createModelClient(grantedPermissions = grantedPermissions.toList())
    }

    suspend fun ApplicationTestBuilder.createModelClient(grantedPermissions: List<PermissionParts>, hmac512key: String = jwtSignatureKey, tokenModifier: (JWTClaimsSet.Builder) -> Unit = {}): ModelClientV2 {
        val url = "http://localhost/v2"
        return ModelClientV2.builder().url(url).client(
            client.config {
                install(Auth) {
                    bearer {
                        loadTokens {
                            val token = createModelixAccessToken(
                                hmac512key = hmac512key,
                                user = "unit-tests@example.com",
                                grantedPermissions = grantedPermissions.map { it.toString() },
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
    fun `cannot create repository`() = runTest {
        val repositoryId = RepositoryId("repo1")
        val client = createModelClientWithPermissions()

        assertFails {
            client.initRepository(repositoryId)
        }
    }

    @Test
    fun `can list repositories`() = runTest {
        assertEquals(emptyList(), createModelClientWithPermissions().listRepositories())

        createModelClientWithPermissions(
            ModelServerPermissionSchema.repository("repo1").create,
        ).initRepository(RepositoryId("repo1"))

        // not visible without permission to list the repository
        assertEquals(emptyList(), createModelClientWithPermissions().listRepositories())

        // visible with correct permission
        assertEquals(
            listOf("repo1"),
            createModelClientWithPermissions(
                ModelServerPermissionSchema.repository("repo1").list,
            ).listRepositories().map { it.id },
        )

        // not visible with list permission on other repository
        assertEquals(
            emptyList(),
            createModelClientWithPermissions(
                ModelServerPermissionSchema.repository("repo2").list,
            ).listRepositories().map { it.id },
        )

        // visible with indirect permission
        assertEquals(
            listOf("repo1"),
            createModelClientWithPermissions(
                ModelServerPermissionSchema.repository("repo1").read,
            ).listRepositories().map { it.id },
        )
    }

    @Test
    fun `can write to branch`() = runTest {
        val repositoryId = RepositoryId("repo1")
        val masterBranch = repositoryId.getBranchReference()
        val branch = repositoryId.getBranchReference("branch1")
        createModelClientWithPermissions(
            ModelServerPermissionSchema.repository(repositoryId).create,
        ).initRepository(repositoryId)
        val client = createModelClientWithPermissions(
            ModelServerPermissionSchema.branch(masterBranch).read,
            ModelServerPermissionSchema.branch(branch).write,
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
            ModelServerPermissionSchema.repository(repositoryId).create,
        ).initRepository(repositoryId)

        val initialVersion = createModelClientWithPermissions(
            ModelServerPermissionSchema.branch(masterBranch).read,
        ).pull(masterBranch, null)

        // no permission in the branch
        assertFails {
            createModelClientWithPermissions(
                ModelServerPermissionSchema.branch(masterBranch).read,
            ).push(branch, initialVersion, initialVersion)
        }

        // only read permission on the branch
        assertFails {
            createModelClientWithPermissions(
                ModelServerPermissionSchema.branch(masterBranch).read,
                ModelServerPermissionSchema.branch(branch).read,
            ).push(branch, initialVersion, initialVersion)
        }
    }

    @Test
    fun `can create repository`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient(ModelServerPermissionSchema.repository(repoId).write)

        assertEquals(emptyList(), client.listRepositories())

        client.initRepository(repoId)

        assertEquals(listOf(repoId), client.listRepositories())
    }

    @Test
    fun `cannot create repository with missing permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val someOtherRepoId = RepositoryId("repo2")
        val client = createModelClient(ModelServerPermissionSchema.repository(someOtherRepoId).write)

        assertEquals(emptyList(), client.listRepositories())

        assertPermissionDenied {
            client.initRepository(repoId)
        }
    }

    @Test
    fun `cannot create repository with read permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient(ModelServerPermissionSchema.repository(repoId).read)

        assertEquals(emptyList(), client.listRepositories())

        assertPermissionDenied {
            client.initRepository(repoId)
        }
    }

    @Test
    fun `can create branch`() = runTest {
        val repoId = RepositoryId("repo1")
        val clientA = createModelClient(ModelServerPermissionSchema.repository(repoId).write)
        val initialVersion = clientA.initRepository(repoId)
        assertEquals(setOf(repoId.getBranchReference()), clientA.listBranches(repoId).toSet())

        val branch = repoId.getBranchReference("branch1")
        val clientB = createModelClient(ModelServerPermissionSchema.branch(branch).write)

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
        val clientA = createModelClient(ModelServerPermissionSchema.repository(repoId).write)
        val initialVersion = clientA.initRepository(repoId)
        assertEquals(setOf(repoId.getBranchReference()), clientA.listBranches(repoId).toSet())

        val masterBranch = repoId.getBranchReference()
        val branch1 = repoId.getBranchReference("branch1")
        val clientB = createModelClient(ModelServerPermissionSchema.branch(masterBranch).write)

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
        val clientA = createModelClient(ModelServerPermissionSchema.repository(repoId).rewrite)

        val initialVersion = clientA.initRepository(repoId)
        assertEquals(setOf(repoId), clientA.listRepositories().toSet())

        clientA.deleteRepository(repoId)
        assertEquals(setOf(), clientA.listRepositories().toSet())
    }

    @Test
    fun `cannot delete a repository without permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val someOtherRepoId = RepositoryId("repo2")
        val clientA = createModelClient(ModelServerPermissionSchema.repository(repoId).write)
        clientA.initRepository(repoId)
        assertEquals(setOf(repoId.getBranchReference()), clientA.listBranches(repoId).toSet())

        val clientB = createModelClient(ModelServerPermissionSchema.repository(someOtherRepoId).rewrite)

        // TODO inconsistent API. All other methods throw an exception if the permission is missing.
        assertFalse(clientB.deleteRepository(repoId))
    }

    @Test
    fun `cannot delete a repository with only write permission`() = runTest {
        val repoId = RepositoryId("repo1")
        val clientA = createModelClient(ModelServerPermissionSchema.repository(repoId).write)
        clientA.initRepository(repoId)
        assertEquals(setOf(repoId.getBranchReference()), clientA.listBranches(repoId).toSet())

        val clientB = createModelClient(ModelServerPermissionSchema.repository(repoId).write)

        assertFalse(clientB.deleteRepository(repoId))
    }

    @Test
    fun `cannot create repository with expired token`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient(grantedPermissions = listOf(ModelServerPermissionSchema.repository(repoId).write)) { jwt ->
            jwt.expirationTime(Date(Instant.now().minusSeconds(300).toEpochMilli()))
        }

        assertUnauthorized {
            client.initRepository(repoId)
        }
    }

    @Test
    fun `cannot create repository with invalid signature`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient(grantedPermissions = listOf(ModelServerPermissionSchema.repository(repoId).write), hmac512key = "xxx")

        assertUnauthorized {
            client.initRepository(repoId)
        }
    }

    @Test
    fun `can create repository with non-expired token`() = runTest {
        val repoId = RepositoryId("repo1")
        val client = createModelClient(grantedPermissions = listOf(ModelServerPermissionSchema.repository(repoId).write)) { jwt ->
            jwt.expirationTime(Date(Instant.now().plusSeconds(10).toEpochMilli()))
        }

        client.initRepository(repoId)
        assertEquals(listOf(repoId), client.listRepositories())
    }

    @Test
    fun `frontend URL doesn't need authorization`() = runTest {
        val repoId = RepositoryId("repo1")
        val client1 = createModelClient(grantedPermissions = listOf(ModelServerPermissionSchema.repository(repoId).write))
        client1.initRepository(repoId)

        val frontendUrl = ModelClientV2.getFrontendUrl("http://localhost/v2", repoId.getBranchReference())
        assertEquals("http://localhost/v2/repositories/repo1/branches/master/frontend", frontendUrl)
    }
}

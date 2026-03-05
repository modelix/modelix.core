package org.modelix.model.server

import com.auth0.jwt.JWT
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.buildUrl
import io.ktor.http.parameters
import io.ktor.http.takeFrom
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.modelix.authorization.IModelixAuthorizationConfig
import org.modelix.authorization.ModelixAuthorization
import org.modelix.authorization.ModelixJWTUtil
import org.modelix.authorization.createModelixAccessToken
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.IAuthRequestHandler
import org.modelix.model.oauth.ITokenParameters
import org.modelix.model.oauth.ITokenProvider
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

private val LOG = KotlinLogging.logger { }

@Serializable
private data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
)

class TokenManagementTest {

    private fun runWithInProcessServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            try {
                val modelixAuthorizationConfig: IModelixAuthorizationConfig.() -> Unit = {
                    permissionSchema = ModelServerPermissionSchema.SCHEMA
                    hmac512Key = "my-hmac-key"
                }
                install(ModelixAuthorization, modelixAuthorizationConfig)
                installDefaultServerPlugins()
                val repoManager = RepositoriesManager(InMemoryStoreClient())
                ModelReplicationServer(repoManager).init(this)
                IdsApiImpl(repoManager).init(this)
            } catch (ex: Throwable) {
                LOG.error("", ex)
            }
        }
        block()
    }

    @Test
    fun `separate token is used for each repository when using token provider`() = runWithInProcessServer {
        val createdTokens = ArrayList<String>()

        fun getPermissions(index: Int) = ModelixJWTUtil().extractPermissions(JWT.decode(createdTokens[index]))

        val client = ModelClientV2.builder()
            .url("http://localhost/v2")
            .client(client)
            .authToken(object : ITokenProvider {
                override suspend fun getToken(parameters: ITokenParameters): String {
                    return createModelixAccessToken(
                        hmac512key = "my-hmac-key",
                        user = "token-test@modelix.org",
                        grantedPermissions = listOfNotNull(
                            parameters.getRepositoryId()?.let { repository ->
                                ModelServerPermissionSchema.repository(repository).write.fullId
                            },
                            parameters.getBranchName()?.let { branchName ->
                                ModelServerPermissionSchema.repository(parameters.getRepositoryId()!!).branch(branchName).write.fullId
                            },
                        ),
                    ).also { createdTokens += it }
                }
            })
            .build()
        assertEquals(0, createdTokens.size)
        client.init()
        assertEquals(1, createdTokens.size)
        assertEquals(listOf(), getPermissions(0))

        val repoId1 = RepositoryId("repo1")
        client.initRepository(repoId1)
        assertEquals(2, createdTokens.size)
        assertEquals(listOf(ModelServerPermissionSchema.repository("repo1").write.fullId), getPermissions(1))

        val repoId2 = RepositoryId("repo2")
        client.initRepository(repoId2)
        assertEquals(3, createdTokens.size)
        assertEquals(listOf(ModelServerPermissionSchema.repository("repo2").write.fullId), getPermissions(2))
    }

    @Test
    fun `separate token is used for each repository when using token endpoint`() = runTest {
        val createdTokens = ArrayList<String>()

        suspend fun runWithServer(body: suspend (port: Int) -> Unit) {
            // real server need instead of ktor.test because the PKCE flow is implemented by a non-ktor client
            val server = embeddedServer(Netty, port = Random.nextInt(20000, 60000)) {
                try {
                    val modelixAuthorizationConfig: IModelixAuthorizationConfig.() -> Unit = {
                        permissionSchema = ModelServerPermissionSchema.SCHEMA
                        hmac512Key = "my-hmac-key"
                    }
                    install(ModelixAuthorization, modelixAuthorizationConfig)
                    installDefaultServerPlugins()
                    val repoManager = RepositoriesManager(InMemoryStoreClient())
                    ModelReplicationServer(repoManager).init(this)
                    IdsApiImpl(repoManager).init(this)
                } catch (ex: Throwable) {
                    LOG.error("", ex)
                }
                routing {
                    post("/token") {
                        val repositoryId = call.queryParameters["repository-id"]?.takeIf { it.isNotEmpty() }
                        val branchName = call.queryParameters["branch-name"]?.takeIf { it.isNotEmpty() }
                        val token = createModelixAccessToken(
                            hmac512key = "my-hmac-key",
                            user = "token-test@modelix.org",
                            grantedPermissions = listOfNotNull(
                                repositoryId?.let { repository ->
                                    ModelServerPermissionSchema.repository(repository).write.fullId
                                },
                                branchName?.let { branchName ->
                                    ModelServerPermissionSchema.repository(repositoryId!!).branch(branchName).write.fullId
                                },
                            ),
                        ).also { createdTokens += it }
                        call.respond(TokenResponse(accessToken = token))
                    }
                }
            }.startSuspend()
            try {
                body(server.engine.resolvedConnectors().single().port)
            } finally {
                server.stop()
            }
        }

        fun getPermissions(index: Int) = ModelixJWTUtil().extractPermissions(JWT.decode(createdTokens[index]))

        runWithServer { port ->
            val client = ModelClientV2.builder()
                .url("http://localhost:$port")
                .authConfig(
                    IAuthConfig.oauth {
                        authRequestHandler(object : IAuthRequestHandler {
                            override fun browse(url: String) {
                                // https://localhost/realms/modelix/protocol/openid-connect/auth?client_id=my-client-id&code_challenge=YzBhqU2-lRzCkoSLVc0BGN3_AlwU5YUpYS1_m_6FMbI&code_challenge_method=S256&redirect_uri=http://127.0.0.1:64186/Callback&response_type=code&scope=email
                                val redirectUri = io.ktor.http.Url(url).parameters["redirect_uri"]!!
                                val callbackWithCode = buildUrl {
                                    takeFrom(redirectUri)
                                    parameters.append("code", "abc")
                                }
                                runBlocking {
                                    HttpClient(CIO).get(callbackWithCode)
                                }
                            }
                        })
                        clientId("my-client-id")
                        tokenUrl("http://localhost:$port/token?repository-id={repositoryId}")
                        authorizationUrl("http://localhost:$port/auth")
                    },
                )
                .build()
            assertEquals(0, createdTokens.size)
            client.init()
            assertEquals(1, createdTokens.size)
            assertEquals(listOf(), getPermissions(0))

            val repoId1 = RepositoryId("repo1")
            client.initRepository(repoId1)
            assertEquals(2, createdTokens.size)
            assertEquals(listOf(ModelServerPermissionSchema.repository("repo1").write.fullId), getPermissions(1))

            val repoId2 = RepositoryId("repo2")
            client.initRepository(repoId2)
            assertEquals(3, createdTokens.size)
            assertEquals(listOf(ModelServerPermissionSchema.repository("repo2").write.fullId), getPermissions(2))
        }
    }
}

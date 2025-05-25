package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.kotlin.utils.filterNotNullValues
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.IAuthRequestHandler
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
private data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
)

class TokenEndpointTest {

    @Test
    fun `repository is passed to token endpoint`() = runTest {
        var tokenEndpointWasCalled = false
        val expectedRepoId = "my-repo"
        val expectedToken = "my-token"
        val expectedBranches = listOf(
            RepositoryId(expectedRepoId).getBranchReference("a"),
            RepositoryId(expectedRepoId).getBranchReference("b"),
        )

        suspend fun runWithServer(body: suspend (port: Int) -> Unit) {
            // real server need instead of ktor.test because the PKCE flow is implemented by a non-ktor client
            val server = embeddedServer(Netty, port = Random.nextInt(20000, 60000)) {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                    json()
                }
                routing {
                    post("/token") {
                        tokenEndpointWasCalled = true
                        val repositoryId = call.queryParameters["repository-id"]
                        assertEquals(expectedRepoId, repositoryId)
                        call.respond(
                            TokenResponse(
                                accessToken = expectedToken,
                            ),
                        )
                    }

                    get("/v2/repositories/{repository-id}/branches") {
                        val authHeader = call.request.parseAuthorizationHeader()
                        if (authHeader is HttpAuthHeader.Single && authHeader.authScheme == "Bearer" && authHeader.blob == expectedToken) {
                            call.respondText(expectedBranches.joinToString("\n") { it.branchName })
                        } else {
                            val port = engine.resolvedConnectors().single().port
                            call.respond(
                                UnauthorizedResponse(
                                    HttpAuthHeader.Parameterized(
                                        "Bearer",
                                        mapOf(
                                            HttpAuthHeader.Parameters.Realm to "modelix",
                                            "error" to "invalid_token",
                                            "authorization_uri" to "http://localhost:$port/auth",
                                            "token_uri" to "http://localhost:$port/token?repository-id={repositoryId}",
                                        ).filterNotNullValues(),
                                    ),
                                ),
                            )
                        }
                    }
                }
            }.startSuspend()
            try {
                body(server.engine.resolvedConnectors().single().port)
            } finally {
                server.stop()
            }
        }

        runWithServer { port ->
            val modelClient = ModelClientV2.builder().url("http://localhost:$port").authConfig(
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
                    repositoryId(RepositoryId(expectedRepoId))
                },
            ).build()

            val actualBranches = modelClient.listBranches(RepositoryId(expectedRepoId))
            assertEquals(expectedBranches, actualBranches)
            assertTrue(tokenEndpointWasCalled, "Token endpoint was not called")
        }
    }
}

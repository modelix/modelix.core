package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.buildUrl
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.kotlin.utils.filterNotNullValues
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.IAuthRequestHandler
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class TokenResponse2(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
)

class RefreshTokenTest {

    @Test
    fun `invalid refresh token is discarded`() = runBlocking {
        val expectedRepoId = "my-repo"
        var nextTokenSuffix = 1
        val validAccessTokens: MutableSet<String> = mutableSetOf()
        val validRefreshTokens: MutableSet<String> = mutableSetOf()
        val expectedBranches = listOf(
            RepositoryId(expectedRepoId).getBranchReference("a"),
            RepositoryId(expectedRepoId).getBranchReference("b"),
        )

        suspend fun ApplicationCall.respondNextToken() {
            val suffix = nextTokenSuffix++
            val accessToken = "my-access-token-$suffix"
            val refreshToken = "my-refresh-token-$suffix"
            validAccessTokens += accessToken
            validRefreshTokens += refreshToken
            val message = TokenResponse2(
                accessToken = accessToken,
                refreshToken = refreshToken,
            )
            respond(message)
        }

        fun invalidateTokens() {
            validAccessTokens.clear()
            validRefreshTokens.clear()
        }

        suspend fun runWithServer(body: suspend (port: Int) -> Unit) {
            // real server need instead of ktor.test because the PKCE flow is implemented by a non-ktor client
            val server = embeddedServer(Netty, port = Random.nextInt(20000, 60000)) {
                install(ContentNegotiation) {
                    json()
                }
                install(CallLogging)
                routing {
                    post("/token") {
                        val receivedParameters = call.receiveParameters()
                        if (receivedParameters["grant_type"] == "authorization_code") {
                            if (receivedParameters["code"] == "abc") {
                                call.respondNextToken()
                            } else {
                                call.respond(HttpStatusCode.BadRequest)
                            }
                        } else if (receivedParameters["grant_type"] == "refresh_token") {
                            if (validRefreshTokens.contains(receivedParameters["refresh_token"])) {
                                call.respondNextToken()
                            } else {
                                call.respond(HttpStatusCode.BadRequest)
                            }
                        } else {
                            call.respond(HttpStatusCode.BadRequest)
                        }
                    }

                    get("/v2/repositories/{repository-id}/branches") {
                        val authHeader = call.request.parseAuthorizationHeader()
                        if (authHeader is HttpAuthHeader.Single && authHeader.authScheme == "Bearer" && validAccessTokens.contains(authHeader.blob)) {
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
                                            "token_uri" to "http://localhost:$port/token",
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
                            val redirectUri = Url(url).parameters["redirect_uri"]!!
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
            invalidateTokens()
            val actualBranches2 = modelClient.listBranches(RepositoryId(expectedRepoId))
            assertEquals(expectedBranches, actualBranches2)
            assertEquals(3, nextTokenSuffix)
        }
    }
}

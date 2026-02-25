package org.modelix.model.client2

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.modelix.model.oauth.IAuthRequestHandler
import org.modelix.model.oauth.ModelixAuthClient
import org.modelix.model.oauth.OAuthConfig
import org.modelix.model.oauth.TokenProviderAuthConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ModelixAuthClientTest {

    @Test
    fun `authorization can be canceled`() = runBlocking {
        val client = ModelixAuthClient()

        var browseCalled = false

        assertFailsWith<CancellationException> {
            withTimeout(500.milliseconds) {
                // The implementation of authorize calls java.util.concurrent.Semaphore.acquireUninterruptibly
                // which blocks the thread forever is the callback URL is never called.
                // The ModelixAuthClient launches an additional coroutine that catches the cancellation and invokes
                // Semaphore.release to unblock the thread.
                client.authorize(
                    OAuthConfig(
                        tokenUrl = "http://localhost/token",
                        authorizationUrl = "http://localhost/auth",
                        authRequestHandler = object : IAuthRequestHandler {
                            override fun browse(url: String) {
                                browseCalled = true
                            }
                        },
                    ),
                )
            }
        }

        assertTrue(browseCalled)
    }

    @Test
    fun `401 response triggers token refresh and retry with new token`() = testApplication {
        var tokenProviderCallCount = 0
        val token1 = "expired-token"
        val token2 = "fresh-token"
        var serverRequestCount = 0

        application {
            routing {
                get("/protected") {
                    serverRequestCount++
                    val authHeader = call.request.headers["Authorization"]
                    when (authHeader) {
                        "Bearer $token1" -> call.respond(HttpStatusCode.Unauthorized, "Token expired")
                        "Bearer $token2" -> call.respond(HttpStatusCode.OK, "Success")
                        else -> call.respond(HttpStatusCode.Unauthorized, "No token")
                    }
                }
            }
        }

        val authClient = createClient {
            ModelixAuthClient().installAuth(
                this,
                TokenProviderAuthConfig {
                    tokenProviderCallCount++
                    if (tokenProviderCallCount == 1) token1 else token2
                },
            )
        }

        val response = authClient.get("/protected")

        assertEquals(HttpStatusCode.OK, response.status, "Should succeed after token refresh")
        assertEquals("Success", response.bodyAsText())
        assertTrue(tokenProviderCallCount >= 2, "Token provider should be called at least twice: initial + refresh. Was called $tokenProviderCallCount times")
        assertTrue(serverRequestCount >= 2, "Server should receive at least 2 requests: initial 401 + retry. Received $serverRequestCount")
    }

    @Test
    fun `403 response does not cause infinite retry loop`() = testApplication {
        var tokenProviderCallCount = 0
        var serverRequestCount = 0

        application {
            routing {
                get("/always-forbidden") {
                    serverRequestCount++
                    // Always return 403, simulating permanently insufficient permissions
                    call.respond(HttpStatusCode.Forbidden, "Permission denied")
                }
            }
        }

        val authClient = createClient {
            ModelixAuthClient().installAuth(
                this,
                TokenProviderAuthConfig {
                    tokenProviderCallCount++
                    "token-$tokenProviderCallCount"
                },
            )
        }

        val response = authClient.get("/always-forbidden")

        assertEquals(HttpStatusCode.Forbidden, response.status, "Should return 403 after retry attempts exhausted")
        // Key assertion: should NOT retry indefinitely
        assertTrue(serverRequestCount <= 3, "Should not retry more than a few times. Server received $serverRequestCount requests")
        assertTrue(tokenProviderCallCount <= 3, "Token provider should not be called excessively. Called $tokenProviderCallCount times")
    }

    @Test
    fun `403 triggers token refresh attempt on first occurrence`() = testApplication {
        var tokenProviderCallCount = 0
        val token1 = "old-branch-token"
        val token2 = "new-branch-token"
        var serverRequestCount = 0

        application {
            routing {
                get("/branch-resource") {
                    serverRequestCount++
                    val authHeader = call.request.headers["Authorization"]
                    when (authHeader) {
                        "Bearer $token1" -> {
                            // First token doesn't have permission for new branch
                            call.respond(HttpStatusCode.Forbidden, "No permission for this branch")
                        }
                        "Bearer $token2" -> {
                            // Refreshed token has correct permissions
                            call.respond(HttpStatusCode.OK, "Branch access granted")
                        }
                        else -> call.respond(HttpStatusCode.Unauthorized, "No token")
                    }
                }
            }
        }

        val authClient = createClient {
            ModelixAuthClient().installAuth(
                this,
                TokenProviderAuthConfig {
                    tokenProviderCallCount++
                    if (tokenProviderCallCount == 1) token1 else token2
                },
            )
        }

        val response = authClient.get("/branch-resource")

        // Verify that 403 triggered a token refresh and successful retry
        assertEquals(HttpStatusCode.OK, response.status, "Should succeed after token refresh on 403")
        assertEquals("Branch access granted", response.bodyAsText())
        assertEquals(2, tokenProviderCallCount, "Token provider should be called twice: initial load + refresh on 403")
        assertEquals(2, serverRequestCount, "Server should receive 2 requests: initial 403 + retry with new token")
    }

    @Test
    fun `successful response after 403 allows future 403 retry`() = testApplication {
        var tokenProviderCallCount = 0
        var serverRequestCount = 0
        var returnForbidden = true

        application {
            routing {
                get("/resource") {
                    serverRequestCount++
                    if (returnForbidden) {
                        call.respond(HttpStatusCode.Forbidden, "Forbidden")
                    } else {
                        call.respond(HttpStatusCode.OK, "Success")
                    }
                }
            }
        }

        val authClient = createClient {
            ModelixAuthClient().installAuth(
                this,
                TokenProviderAuthConfig {
                    tokenProviderCallCount++
                    "token-$tokenProviderCallCount"
                },
            )
        }

        // First request - gets 403
        val response1 = authClient.get("/resource")
        assertEquals(HttpStatusCode.Forbidden, response1.status)
        val requestsAfterFirst403 = serverRequestCount

        // Second request - succeeds (simulating fix to permissions)
        returnForbidden = false
        val response2 = authClient.get("/resource")
        assertEquals(HttpStatusCode.OK, response2.status)

        // Third request - gets 403 again (new branch change)
        returnForbidden = true
        val response3 = authClient.get("/resource")
        assertEquals(HttpStatusCode.Forbidden, response3.status)

        // Verify the flag was reset after success, allowing retry attempt on third request
        // This is the key behavior: after success, a new 403 should trigger a fresh retry attempt
        assertTrue(
            serverRequestCount > requestsAfterFirst403 + 1,
            "After successful response, new 403 should trigger retry attempt. Total requests: $serverRequestCount",
        )
    }

    @Test
    fun `403 retry uses the new token in Authorization header`() = testApplication {
        val tokensReceivedByServer = mutableListOf<String?>()
        var tokenProviderCallCount = 0
        val token1 = "initial-token-abc"
        val token2 = "refreshed-token-xyz"

        application {
            routing {
                get("/verify-token") {
                    val authHeader = call.request.headers["Authorization"]
                    tokensReceivedByServer.add(authHeader)

                    when (authHeader) {
                        "Bearer $token1" -> call.respond(HttpStatusCode.Forbidden, "Old token")
                        "Bearer $token2" -> call.respond(HttpStatusCode.OK, "New token accepted")
                        else -> call.respond(HttpStatusCode.Unauthorized, "Unknown: $authHeader")
                    }
                }
            }
        }

        val authClient = createClient {
            ModelixAuthClient().installAuth(
                this,
                TokenProviderAuthConfig {
                    tokenProviderCallCount++
                    if (tokenProviderCallCount == 1) token1 else token2
                },
            )
        }

        val response = authClient.get("/verify-token")

        // Print debug info
        println("Tokens received by server in order: $tokensReceivedByServer")
        println("Token provider called $tokenProviderCallCount times")
        println("Response status: ${response.status}")

        // Verify server received both tokens in correct order
        assertEquals(2, tokensReceivedByServer.size, "Server should receive exactly 2 requests")
        assertEquals("Bearer $token1", tokensReceivedByServer[0], "First request should use initial token")
        assertEquals("Bearer $token2", tokensReceivedByServer[1], "Second request (retry) should use refreshed token")
        assertEquals(HttpStatusCode.OK, response.status, "Final response should be OK")
    }
}

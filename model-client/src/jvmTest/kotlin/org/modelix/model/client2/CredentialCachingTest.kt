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
import io.ktor.server.application.install
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.kotlin.utils.filterNotNullValues
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.IAuthRequest
import org.modelix.model.oauth.IAuthRequestHandler
import org.modelix.model.oauth.OAuthConfig
import java.net.BindException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

private inline fun <R> retryOnException(exceptionType: Class<out Throwable>, body: () -> R): R {
    var attempt = 0
    while (true) {
        try {
            return body()
        } catch (ex: Throwable) {
            if (++attempt >= 3 || !exceptionType.isAssignableFrom(ex::class.java)) throw ex
        }
    }
}

@Serializable
private data class CachedTestTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
)

class CredentialCachingTest {

    // The credential cache is process-wide (a ModelixAuthClient companion-object map) and these
    // tests run in parallel with each other and with OAuthTest. Isolation is therefore achieved by
    // giving every test a unique cache key — each embedded server binds a random port, so the
    // discovered/observed authorizationUrl (and thus the cache key) differs per test. We must NOT
    // clear the shared cache globally here: that would race with and wipe other tests' credentials.

    // T030: cache key must not include tokenUrl — two configs with the same issuer+clientId
    // but different branch-specific token URLs must share a single credential cache entry.
    @Test
    fun `credential cache key excludes token URL so different branch URLs with same issuer and client ID share one credential`() {
        val config1 = OAuthConfig(
            clientId = "external-mps",
            authorizationUrl = "https://idp.example.com/auth",
            tokenUrl = "https://srs.example.com/v1/oauth2/modelix-token?repositoryId=repo&branchName=versions%2F1.0.0",
        )
        val config2 = OAuthConfig(
            clientId = "external-mps",
            authorizationUrl = "https://idp.example.com/auth",
            tokenUrl = "https://srs.example.com/v1/oauth2/modelix-token?repositoryId=repo&branchName=versions%2F2.0.0",
        )
        assertEquals(config1.getCacheKey(), config2.getCacheKey())
    }

    // T032: two ModelClientV2 instances with the same authorizationUrl+clientId but different
    // branch-specific tokenUrls must require exactly one PKCE login; the second client reuses
    // the stored refresh token to obtain its own branch-scoped access token.
    @Test
    fun `two clients on different branch token URLs share one PKCE login via refresh token`() = runBlocking {
        val expectedRepoId = "my-repo"
        val pkceLoginCount = AtomicInteger(0)
        val tokenSuffix = AtomicInteger(1)
        val validAccessTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val validRefreshTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val expectedBranches = listOf(
            RepositoryId(expectedRepoId).getBranchReference("a"),
            RepositoryId(expectedRepoId).getBranchReference("b"),
        )

        fun issueTokens(): CachedTestTokenResponse {
            val n = tokenSuffix.getAndIncrement()
            val at = "at-$n"
            val rt = "rt-$n"
            validAccessTokens += at
            validRefreshTokens += rt
            return CachedTestTokenResponse(at, rt)
        }

        suspend fun runWithServer(body: suspend (port: Int) -> Unit) {
            val server = retryOnException(BindException::class.java) {
                embeddedServer(Netty, port = Random.nextInt(20000, 60000)) {
                    install(ContentNegotiation) { json() }
                    routing {
                        post("/token/{branch}") {
                            val params = call.receiveParameters()
                            when {
                                params["grant_type"] == "authorization_code" && params["code"] == "abc" -> {
                                    pkceLoginCount.incrementAndGet()
                                    call.respond(issueTokens())
                                }
                                params["grant_type"] == "refresh_token" && validRefreshTokens.contains(params["refresh_token"]) -> {
                                    call.respond(issueTokens())
                                }
                                else -> call.respond(HttpStatusCode.BadRequest)
                            }
                        }
                        get("/v2/repositories/{repository-id}/branches") {
                            val authHeader = call.request.parseAuthorizationHeader()
                            if (authHeader is HttpAuthHeader.Single &&
                                authHeader.authScheme == "Bearer" &&
                                validAccessTokens.contains(authHeader.blob)
                            ) {
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
                                                "token_uri" to "http://localhost:$port/token/default",
                                            ).filterNotNullValues(),
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }.startSuspend()
            }
            try {
                body(server.engine.resolvedConnectors().single().port)
            } finally {
                server.stop()
            }
        }

        runWithServer { port ->
            val authHandler = object : IAuthRequestHandler {
                override fun browse(request: IAuthRequest) {
                    val redirectUri = Url(request.getUrl()).parameters["redirect_uri"]!!
                    val callbackWithCode = buildUrl {
                        takeFrom(redirectUri)
                        parameters.append("code", "abc")
                    }
                    runBlocking { HttpClient(CIO).get(callbackWithCode) }
                }
            }

            fun makeClient(branch: String) = ModelClientV2.builder()
                .url("http://localhost:$port")
                .authConfig(
                    IAuthConfig.oauth {
                        clientId("external-mps")
                        authorizationUrl("http://localhost:$port/auth")
                        tokenUrl("http://localhost:$port/token/$branch")
                        authRequestHandler(authHandler)
                    },
                )
                .build()

            makeClient("branch1").use { it.listBranches(RepositoryId(expectedRepoId)) }
            makeClient("branch2").use { it.listBranches(RepositoryId(expectedRepoId)) }

            assertEquals(1, pkceLoginCount.get(), "Expected one PKCE login but got ${pkceLoginCount.get()}")
        }
    }

    // T033: when the token endpoint returns invalid_grant, the stored refresh token must be
    // discarded and the client must fall back to a new PKCE login rather than looping.
    @Test
    fun `invalid_grant response discards stored refresh token and triggers new PKCE login`() = runBlocking {
        val expectedRepoId = "my-repo"
        val pkceLoginCount = AtomicInteger(0)
        val tokenSuffix = AtomicInteger(1)
        val validAccessTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val validRefreshTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        var rejectNextRefresh = false
        val expectedBranches = listOf(
            RepositoryId(expectedRepoId).getBranchReference("a"),
        )

        fun issueTokens(): CachedTestTokenResponse {
            val n = tokenSuffix.getAndIncrement()
            val at = "at-$n"
            val rt = "rt-$n"
            validAccessTokens += at
            validRefreshTokens += rt
            return CachedTestTokenResponse(at, rt)
        }

        suspend fun runWithServer(body: suspend (port: Int) -> Unit) {
            val server = retryOnException(BindException::class.java) {
                embeddedServer(Netty, port = Random.nextInt(20000, 60000)) {
                    install(ContentNegotiation) { json() }
                    routing {
                        post("/token") {
                            val params = call.receiveParameters()
                            when {
                                params["grant_type"] == "authorization_code" && params["code"] == "abc" -> {
                                    pkceLoginCount.incrementAndGet()
                                    call.respond(issueTokens())
                                }
                                params["grant_type"] == "refresh_token" && !rejectNextRefresh &&
                                    validRefreshTokens.contains(params["refresh_token"]) -> {
                                    call.respond(issueTokens())
                                }
                                params["grant_type"] == "refresh_token" -> {
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "invalid_grant", "error_description" to "Token expired"),
                                    )
                                }
                                else -> call.respond(HttpStatusCode.BadRequest)
                            }
                        }
                        get("/v2/repositories/{repository-id}/branches") {
                            val authHeader = call.request.parseAuthorizationHeader()
                            if (authHeader is HttpAuthHeader.Single &&
                                authHeader.authScheme == "Bearer" &&
                                validAccessTokens.contains(authHeader.blob)
                            ) {
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
            }
            try {
                body(server.engine.resolvedConnectors().single().port)
            } finally {
                server.stop()
            }
        }

        runWithServer { port ->
            val authHandler = object : IAuthRequestHandler {
                override fun browse(request: IAuthRequest) {
                    val redirectUri = Url(request.getUrl()).parameters["redirect_uri"]!!
                    val callbackWithCode = buildUrl {
                        takeFrom(redirectUri)
                        parameters.append("code", "abc")
                    }
                    runBlocking { HttpClient(CIO).get(callbackWithCode) }
                }
            }

            val client = ModelClientV2.builder()
                .url("http://localhost:$port")
                .authConfig(
                    IAuthConfig.oauth {
                        clientId("external-mps")
                        authorizationUrl("http://localhost:$port/auth")
                        tokenUrl("http://localhost:$port/token")
                        authRequestHandler(authHandler)
                    },
                )
                .build()

            client.use {
                // First call: PKCE login, stores refresh token
                it.listBranches(RepositoryId(expectedRepoId))
                assertEquals(1, pkceLoginCount.get())

                // Invalidate the access token and mark the next refresh as invalid_grant
                validAccessTokens.clear()
                rejectNextRefresh = true

                // Second call: access token rejected → refresh grant returns invalid_grant
                // → stored RT discarded → new PKCE login triggered
                it.listBranches(RepositoryId(expectedRepoId))
                assertEquals(2, pkceLoginCount.get(), "Expected a second PKCE login after invalid_grant")
            }
        }
    }

    // FR-032 single-flight: two clients for the same (issuer, clientId) opening *concurrently* must
    // still trigger exactly one interactive PKCE login; the second waits on the shared mutex and
    // reuses the refresh token. Without the mutex both would log in (and likely race on the local
    // receiver port).
    @Test
    fun `concurrent branch opens trigger only one PKCE login`() = runBlocking {
        val expectedRepoId = "my-repo"
        val pkceLoginCount = AtomicInteger(0)
        val tokenSuffix = AtomicInteger(1)
        val validAccessTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val validRefreshTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val expectedBranches = listOf(RepositoryId(expectedRepoId).getBranchReference("a"))

        fun issueTokens(): CachedTestTokenResponse {
            val n = tokenSuffix.getAndIncrement()
            val at = "at-$n"
            val rt = "rt-$n"
            validAccessTokens += at
            validRefreshTokens += rt
            return CachedTestTokenResponse(at, rt)
        }

        suspend fun runWithServer(body: suspend (port: Int) -> Unit) {
            val server = retryOnException(BindException::class.java) {
                embeddedServer(Netty, port = Random.nextInt(20000, 60000)) {
                    install(ContentNegotiation) { json() }
                    routing {
                        post("/token/{branch}") {
                            val params = call.receiveParameters()
                            when {
                                params["grant_type"] == "authorization_code" && params["code"] == "abc" -> {
                                    pkceLoginCount.incrementAndGet()
                                    call.respond(issueTokens())
                                }
                                params["grant_type"] == "refresh_token" && validRefreshTokens.contains(params["refresh_token"]) -> {
                                    call.respond(issueTokens())
                                }
                                else -> call.respond(HttpStatusCode.BadRequest)
                            }
                        }
                        get("/v2/repositories/{repository-id}/branches") {
                            val authHeader = call.request.parseAuthorizationHeader()
                            if (authHeader is HttpAuthHeader.Single &&
                                authHeader.authScheme == "Bearer" &&
                                validAccessTokens.contains(authHeader.blob)
                            ) {
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
                                                "token_uri" to "http://localhost:$port/token/default",
                                            ).filterNotNullValues(),
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }.startSuspend()
            }
            try {
                body(server.engine.resolvedConnectors().single().port)
            } finally {
                server.stop()
            }
        }

        runWithServer { port ->
            val authHandler = object : IAuthRequestHandler {
                override fun browse(request: IAuthRequest) {
                    val redirectUri = Url(request.getUrl()).parameters["redirect_uri"]!!
                    val callbackWithCode = buildUrl {
                        takeFrom(redirectUri)
                        parameters.append("code", "abc")
                    }
                    runBlocking { HttpClient(CIO).get(callbackWithCode) }
                }
            }

            fun makeClient(branch: String) = ModelClientV2.builder()
                .url("http://localhost:$port")
                .authConfig(
                    IAuthConfig.oauth {
                        clientId("external-mps")
                        authorizationUrl("http://localhost:$port/auth")
                        tokenUrl("http://localhost:$port/token/$branch")
                        authRequestHandler(authHandler)
                    },
                )
                .build()

            val clientA = makeClient("branch1")
            val clientB = makeClient("branch2")
            try {
                coroutineScope {
                    // Launch both concurrently. listBranches suspends on network + the shared
                    // authMutex, so the two interleave; performPKCE already offloads its blocking
                    // work to an IO dispatcher, so an absent mutex would still bind two receiver
                    // ports and produce two logins.
                    awaitAll(
                        async { clientA.listBranches(RepositoryId(expectedRepoId)) },
                        async { clientB.listBranches(RepositoryId(expectedRepoId)) },
                    )
                }
                assertEquals(1, pkceLoginCount.get(), "Concurrent opens must share one PKCE login, got ${pkceLoginCount.get()}")
            } finally {
                clientA.close()
                clientB.close()
            }
        }
    }

    // FR-032 rotation chaining: with single-use refresh tokens (each refresh invalidates the
    // previous RT), repeated refreshes must keep succeeding — proving the client always chains to
    // the latest rotated RT. If it ever re-sent a consumed RT, the server would reject it and force
    // a second PKCE login.
    @Test
    fun `rotating single-use refresh tokens keep working without re-login`() = runBlocking {
        val expectedRepoId = "my-repo"
        val pkceLoginCount = AtomicInteger(0)
        val tokenSuffix = AtomicInteger(1)
        val validAccessTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val validRefreshTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val expectedBranches = listOf(RepositoryId(expectedRepoId).getBranchReference("a"))

        fun issueTokens(): CachedTestTokenResponse {
            val n = tokenSuffix.getAndIncrement()
            val at = "at-$n"
            val rt = "rt-$n"
            validAccessTokens += at
            validRefreshTokens += rt
            return CachedTestTokenResponse(at, rt)
        }

        suspend fun runWithServer(body: suspend (port: Int) -> Unit) {
            val server = retryOnException(BindException::class.java) {
                embeddedServer(Netty, port = Random.nextInt(20000, 60000)) {
                    install(ContentNegotiation) { json() }
                    routing {
                        post("/token") {
                            val params = call.receiveParameters()
                            when {
                                params["grant_type"] == "authorization_code" && params["code"] == "abc" -> {
                                    pkceLoginCount.incrementAndGet()
                                    call.respond(issueTokens())
                                }
                                params["grant_type"] == "refresh_token" && validRefreshTokens.contains(params["refresh_token"]) -> {
                                    // single-use: consume the presented refresh token on rotation
                                    validRefreshTokens.remove(params["refresh_token"])
                                    call.respond(issueTokens())
                                }
                                else ->
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "invalid_grant", "error_description" to "reused or unknown refresh token"),
                                    )
                            }
                        }
                        get("/v2/repositories/{repository-id}/branches") {
                            val authHeader = call.request.parseAuthorizationHeader()
                            if (authHeader is HttpAuthHeader.Single &&
                                authHeader.authScheme == "Bearer" &&
                                validAccessTokens.contains(authHeader.blob)
                            ) {
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
            }
            try {
                body(server.engine.resolvedConnectors().single().port)
            } finally {
                server.stop()
            }
        }

        runWithServer { port ->
            val authHandler = object : IAuthRequestHandler {
                override fun browse(request: IAuthRequest) {
                    val redirectUri = Url(request.getUrl()).parameters["redirect_uri"]!!
                    val callbackWithCode = buildUrl {
                        takeFrom(redirectUri)
                        parameters.append("code", "abc")
                    }
                    runBlocking { HttpClient(CIO).get(callbackWithCode) }
                }
            }

            ModelClientV2.builder()
                .url("http://localhost:$port")
                .authConfig(
                    IAuthConfig.oauth {
                        clientId("external-mps")
                        authorizationUrl("http://localhost:$port/auth")
                        tokenUrl("http://localhost:$port/token")
                        authRequestHandler(authHandler)
                    },
                )
                .build()
                .use { client ->
                    client.listBranches(RepositoryId(expectedRepoId))
                    // Force several refreshes; each rotates (and invalidates) the refresh token.
                    repeat(3) {
                        validAccessTokens.clear()
                        assertEquals(expectedBranches, client.listBranches(RepositoryId(expectedRepoId)))
                    }
                    assertEquals(1, pkceLoginCount.get(), "Rotating refresh tokens must not force a re-login")
                }
        }
    }

    // FR-031/033 isolation: a different (issuer, clientId) must NOT reuse another's cached refresh
    // token. Two clients with distinct client IDs against the same server must each log in once.
    @Test
    fun `different client IDs do not share the cached credential`() = runBlocking {
        val expectedRepoId = "my-repo"
        val pkceLoginCount = AtomicInteger(0)
        val tokenSuffix = AtomicInteger(1)
        val validAccessTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val validRefreshTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val expectedBranches = listOf(RepositoryId(expectedRepoId).getBranchReference("a"))

        fun issueTokens(): CachedTestTokenResponse {
            val n = tokenSuffix.getAndIncrement()
            val at = "at-$n"
            val rt = "rt-$n"
            validAccessTokens += at
            validRefreshTokens += rt
            return CachedTestTokenResponse(at, rt)
        }

        suspend fun runWithServer(body: suspend (port: Int) -> Unit) {
            val server = retryOnException(BindException::class.java) {
                embeddedServer(Netty, port = Random.nextInt(20000, 60000)) {
                    install(ContentNegotiation) { json() }
                    routing {
                        post("/token") {
                            val params = call.receiveParameters()
                            when {
                                params["grant_type"] == "authorization_code" && params["code"] == "abc" -> {
                                    pkceLoginCount.incrementAndGet()
                                    call.respond(issueTokens())
                                }
                                params["grant_type"] == "refresh_token" && validRefreshTokens.contains(params["refresh_token"]) -> {
                                    call.respond(issueTokens())
                                }
                                else -> call.respond(HttpStatusCode.BadRequest)
                            }
                        }
                        get("/v2/repositories/{repository-id}/branches") {
                            val authHeader = call.request.parseAuthorizationHeader()
                            if (authHeader is HttpAuthHeader.Single &&
                                authHeader.authScheme == "Bearer" &&
                                validAccessTokens.contains(authHeader.blob)
                            ) {
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
            }
            try {
                body(server.engine.resolvedConnectors().single().port)
            } finally {
                server.stop()
            }
        }

        runWithServer { port ->
            val authHandler = object : IAuthRequestHandler {
                override fun browse(request: IAuthRequest) {
                    val redirectUri = Url(request.getUrl()).parameters["redirect_uri"]!!
                    val callbackWithCode = buildUrl {
                        takeFrom(redirectUri)
                        parameters.append("code", "abc")
                    }
                    runBlocking { HttpClient(CIO).get(callbackWithCode) }
                }
            }

            fun makeClient(clientId: String) = ModelClientV2.builder()
                .url("http://localhost:$port")
                .authConfig(
                    IAuthConfig.oauth {
                        clientId(clientId)
                        authorizationUrl("http://localhost:$port/auth")
                        tokenUrl("http://localhost:$port/token")
                        authRequestHandler(authHandler)
                    },
                )
                .build()

            makeClient("client-a").use { it.listBranches(RepositoryId(expectedRepoId)) }
            makeClient("client-b").use { it.listBranches(RepositoryId(expectedRepoId)) }

            assertEquals(2, pkceLoginCount.get(), "Distinct client IDs must each log in; cache must not be shared")
        }
    }
}

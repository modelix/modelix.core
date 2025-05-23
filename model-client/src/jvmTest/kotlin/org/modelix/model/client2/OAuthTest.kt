package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.parameters
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.modelix.model.api.ITree
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.oauth.IAuthRequestHandler
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

class OAuthTest {

    @Test
    fun test() = runWithModelServer { url ->
        val client = ModelClientV2.builder()
            .url(url)
            .retries(1U)
            .oauth {
                clientId("external-mps")
                authRequestHandler(object : IAuthRequestHandler {
                    override fun browse(url: String) {
                        runBlocking {
                            handleOAuthLogin(url, "user1", "abc")
                        }
                    }
                })
            }
            .build()
        client.init()

        val version = client.initRepository(RepositoryId("oauth-test-repo"))
        assertEquals(0, version.getTree().getAllChildren(ITree.ROOT_ID).count())
    }

    private suspend fun handleOAuthLogin(authUrl: String, user: String, password: String) {
        val acceptAllCookiesStorage = AcceptAllCookiesStorage()
        val cookiesStorage = object : CookiesStorage by acceptAllCookiesStorage {
            override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
                acceptAllCookiesStorage.addCookie(requestUrl, cookie.copy(secure = false))
            }
        }
        val httpClient = HttpClient(CIO) {
            install(HttpCookies) {
                storage = cookiesStorage
            }
        }
        val html = httpClient.get(authUrl).also { println(it.headers.entries()) }.bodyAsText()

        println(html)
        val loginUrl = Regex("""[^"]+/login-actions/authenticate[^"]+""").find(html)!!.value

        val callbackUrl = httpClient.submitForm(
            url = loginUrl,
            formParameters = parameters {
                set("username", user)
                set("password", password)
            },
        ).headers[HttpHeaders.Location]!!

        httpClient.get(callbackUrl).bodyAsText()
    }

    private fun runWithModelServer(body: suspend (url: String) -> Unit) = runBlocking {
        @OptIn(ExperimentalTime::class)
        withTimeout(5.minutes) {
            val network = Network.newNetwork()

            val keycloak: GenericContainer<*> = GenericContainer("quay.io/keycloak/keycloak:${System.getenv("KEYCLOAK_VERSION")}")
                .withExposedPorts(8080)
                .withCommand("start-dev", "--import-realm")
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                .withEnv("KC_HTTP_PORT", "8080")
                .withEnv("KC_HOSTNAME", "localhost")
                .withCopyFileToContainer(MountableFile.forHostPath("../model-server-with-auth/realm.json"), "/opt/keycloak/data/import/realm.json")
                .withNetwork(network)
                .withNetworkAliases("keycloak")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(3.minutes.toJavaDuration()))
                .withLogConsumer { println("[KEYCLOAK] " + it.utf8StringWithoutLineEnding) }
            keycloak.start()
            val keycloakPort = keycloak.getMappedPort(8080)

            val modelServer: GenericContainer<*> = GenericContainer(System.getProperty("modelix.model.server.image"))
                .withExposedPorts(28101)
                .withCommand("--inmemory")
                .withEnv("MODELIX_AUTHORIZATION_URI", "http://localhost:$keycloakPort/realms/modelix/protocol/openid-connect/auth")
                .withEnv("MODELIX_TOKEN_URI", "http://localhost:$keycloakPort/realms/modelix/protocol/openid-connect/token")
                .withEnv("MODELIX_PERMISSION_CHECKS_ENABLED", "true")
                .withEnv("MODELIX_JWK_URI_KEYCLOAK", "http://keycloak:8080/realms/modelix/protocol/openid-connect/certs")
                .withNetwork(network)
                .withNetworkAliases("model-server")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(3.minutes.toJavaDuration()))
                .withLogConsumer { println("[MODEL] " + it.utf8StringWithoutLineEnding) }
            modelServer.start()

            try {
                body("http://localhost:${modelServer.firstMappedPort}/")
            } finally {
                modelServer.stop()
                keycloak.stop()
            }
        }
    }
}

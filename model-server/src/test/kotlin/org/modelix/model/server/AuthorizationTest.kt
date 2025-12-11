package org.modelix.model.server

import com.auth0.jwt.JWT
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.apache.http.impl.client.HttpClients
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.authorization.client.AuthzClient
import org.keycloak.authorization.client.Configuration
import org.modelix.authorization.IModelixAuthorizationConfig
import org.modelix.authorization.ModelixAuthorization
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.server.handlers.IdsApiImpl
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.StoreManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private const val ADMIN_USER = "admin"
private const val ADMIN_PASSWORD = "admin"
private const val REALM = "authorization-test-realm"
private const val CLIENT_ID = "authorization-test-client"
private const val USER = "authorization-test-user"
private const val USER_PASSWORD = "authorization-test-user-password"

class AuthorizationTest {

    companion object {
        // Configure `clients[0].publicClient` because we want to log in without specifying a client secret.
        // Configure `clients[0].directAccessGrantsEnabled` because we want to directly log in with a password and username.
        // Configure `users[0].email` because it is required by default.
        // Configure `clientScopes` so that we can put Modelix `permissions` into the token.
        // See https://stackoverflow.com/questions/78528623/keycloak-move-from-23-0-to-24-0-account-is-not-fully-set-up-invalid-grant
        // It could be configured to be optional but doing so is more complicated than just adding it for the test user.
        // Configure `users[0].firstName` for the same reason as for `users[0].email`
        // Configure `users[0].lastName` for the same reason as for `users[0].email`.
        // Configure `components."org.keycloak.keys.KeyProvider"` so that we can test using a token with a wrong key.
        // language=json
        private const val REALM_CONFIGURATION = """
            {
              "realm": "$REALM",
              "enabled": true,
              "sslRequired": "none",
              "clients": [
                {
                  "clientId": "$CLIENT_ID",
                  "enabled": true,
                  "directAccessGrantsEnabled": true,
                  "publicClient": true,
                  "defaultClientScopes": ["authorization-test-permissions-claim"]
                }
              ],
              "clientScopes": [
                {
                  "name": "authorization-test-permissions-claim",
                  "description": "",
                  "protocol": "openid-connect",
                  "attributes": {
                    "include.in.token.scope": "false",
                    "display.on.consent.screen": "true",
                    "gui.order": "",
                    "consent.screen.text": ""
                  },
                  "protocolMappers": [
                    {
                      "name": "authorization-test-permissions-mapper",
                      "protocol": "openid-connect",
                      "protocolMapper": "oidc-hardcoded-claim-mapper",
                      "consentRequired": false,
                      "config": {
                        "introspection.token.claim": "true",
                        "claim.value": "[\"model-server/admin\"]",
                        "userinfo.token.claim": "true",
                        "id.token.claim": "true",
                        "lightweight.claim": "false",
                        "access.token.claim": "true",
                        "claim.name": "permissions",
                        "jsonType.label": "JSON",
                        "access.tokenResponse.claim": "false"
                      }
                    }
                  ]
                }
              ],
              "users": [
                {
                  "username": "$USER",
                  "email": "authorization-test-user@authorization-test-user.com",
                  "firstName": "authorization-test-user",
                  "lastName": "authorization-test-user",
                  "enabled": true,
                  "credentials": [
                    {
                      "type": "password",
                      "value": "$USER_PASSWORD"
                    }
                  ]
                }
              ],
              "components": {
                "org.keycloak.keys.KeyProvider": [
                   {
                    "name": "rsa-256-generated",
                    "providerId": "rsa-generated",
                    "subComponents": {},
                    "config": {
                      "keySize": [
                        "2048"
                      ],
                      "active": [
                        "true"
                      ],
                      "priority": [
                        "100"
                      ],
                      "enabled": [
                        "true"
                      ],
                      "algorithm": [
                        "RS256"
                      ]
                    }
                  },
                  {
                    "name": "rsa-512-generated",
                    "providerId": "rsa-generated",
                    "subComponents": {},
                    "config": {
                      "keySize": [
                        "2048"
                      ],
                      "active": [
                        "true"
                      ],
                      "priority": [
                        "0"
                      ],
                      "enabled": [
                        "true"
                      ],
                      "algorithm": [
                        "RS512"
                      ]
                    }
                  }
                ]
              }
            }
        """

        // Reuse on container across all tests. The configuration and state does not change in between.
        private val keycloak: GenericContainer<*> = GenericContainer("quay.io/keycloak/keycloak:${System.getenv("KEYCLOAK_VERSION")}")
            .withEnv("KEYCLOAK_ADMIN", ADMIN_USER)
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withExposedPorts(8080)
            .withCopyToContainer(Transferable.of(REALM_CONFIGURATION), "/opt/keycloak/data/import/realm.json")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(3.minutes.toJavaDuration()))
            .withCommand("start-dev", "--import-realm", "--verbose")

        private var keycloakBaseUrl: String
        private var keycloakAdminClient: Keycloak
        private var keycloakAuthorizationClient: AuthzClient

        init {
            keycloak.start()
            keycloakBaseUrl = "http://${keycloak.host}:${keycloak.firstMappedPort}"

            keycloakAdminClient = KeycloakBuilder.builder()
                .serverUrl(keycloakBaseUrl)
                .realm("master")
                .clientId("admin-cli")
                .grantType(OAuth2Constants.PASSWORD)
                .username(ADMIN_USER)
                .password(ADMIN_PASSWORD)
                .build()

            val configuration = Configuration(
                keycloakBaseUrl,
                REALM,
                CLIENT_ID,
                mapOf("secret" to ""),
                HttpClients.createDefault(),
            )
            keycloakAuthorizationClient = AuthzClient.create(configuration)
        }
    }

    private fun obtainTokenForTestUser(): String {
        val token = keycloakAuthorizationClient.obtainAccessToken(USER, USER_PASSWORD).token
        return token
    }

    private fun runAuthorizationTest(
        modelixAuthorizationConfig: IModelixAuthorizationConfig.() -> Unit,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            install(ModelixAuthorization, modelixAuthorizationConfig)
            installDefaultServerPlugins()
            val storeClient = InMemoryStoreClient()
            val storeManager = StoreManager(storeClient)
            val repositoriesManager = RepositoriesManager(storeManager)
            ModelReplicationServer(repositoriesManager).init(this)
            IdsApiImpl(repositoriesManager).init(this)
        }
        block()
    }

    @Test
    fun `authorization uses key ID from JWT if no key ID is specified`() {
        val modelixAuthorizationConfig: IModelixAuthorizationConfig.() -> Unit = {
            permissionSchema = ModelServerPermissionSchema.SCHEMA
            jwkUri = URI("$keycloakBaseUrl/realms/$REALM/protocol/openid-connect/certs")
        }
        val token = obtainTokenForTestUser()
        runAuthorizationTest(modelixAuthorizationConfig) {
            val modelClient = createModelClient(token)

            assertDoesNotThrow {
                modelClient.init()
            }
        }
    }

    @Test
    fun `authorization fails to use key ID if it does not exist at JWK URI`() {
        val modelixAuthorizationConfig: IModelixAuthorizationConfig.() -> Unit = {
            permissionSchema = ModelServerPermissionSchema.SCHEMA
            jwkUri = URI("$keycloakBaseUrl/realms/master/protocol/openid-connect/certs")
        }
        val token = obtainTokenForTestUser()
        runAuthorizationTest(modelixAuthorizationConfig) {
            val modelClient = createModelClient(token)

            val exception = assertThrows<ClientRequestException> { modelClient.init() }

            assertEquals(HttpStatusCode.Unauthorized, exception.response.status)
        }
    }

    @Test
    fun `authorization succeeds if key ID in token matches the configured key ID from JWK URI`() {
        val token = obtainTokenForTestUser()
        val decodedToken = JWT.decode(token)
        val keyIdInToken = decodedToken.keyId
        val modelixAuthorizationConfig: IModelixAuthorizationConfig.() -> Unit = {
            permissionSchema = ModelServerPermissionSchema.SCHEMA
            jwkUri = URI("$keycloakBaseUrl/realms/$REALM/protocol/openid-connect/certs")
            jwkKeyId = keyIdInToken
        }
        runAuthorizationTest(modelixAuthorizationConfig) {
            val modelClient = createModelClient(token)

            assertDoesNotThrow {
                modelClient.init()
            }
        }
    }

    @Test
    fun `authorization fails if key ID in token does not match the configured key ID from JWK URI`() {
        val token = obtainTokenForTestUser()
        val decodedToken = JWT.decode(token)
        val keyIdInToken = decodedToken.keyId
        // Use a fake key ID that doesn't exist instead of querying the admin API
        // The admin API requires HTTPS even in dev mode for the master realm
        val keyIdNotUsedInToken = "fake-key-id-that-does-not-exist"
        val modelixAuthorizationConfig: IModelixAuthorizationConfig.() -> Unit = {
            permissionSchema = ModelServerPermissionSchema.SCHEMA
            jwkUri = URI("$keycloakBaseUrl/realms/$REALM/protocol/openid-connect/certs")
            jwkKeyId = keyIdNotUsedInToken
        }
        runAuthorizationTest(modelixAuthorizationConfig) {
            val modelClient = createModelClient(token)

            val exception = assertThrows<ClientRequestException> { modelClient.init() }

            assertEquals(HttpStatusCode.Unauthorized, exception.response.status)
        }
    }

    private fun ApplicationTestBuilder.createModelClient(token: String): ModelClientV2 {
        val modelClient = ModelClientV2.builder()
            .url("http://localhost/v2")
            .client(client)
            .authToken { token }
            .build()
        return modelClient
    }
}

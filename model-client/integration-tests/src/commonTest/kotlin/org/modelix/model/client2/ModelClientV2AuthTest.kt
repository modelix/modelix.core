package org.modelix.model.client2

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelClientV2AuthTest {

    @Test
    fun modelClientUsesProvidedAuthToken() = runTest {
        AuthTestFixture.addExpectationsForSucceedingAuthenticationWithToken()
        val modelClient = ModelClientV2.builder()
            .url(AuthTestFixture.MODEL_SERVER_URL)
            .authToken { AuthTestFixture.AUTH_TOKEN }
            .build()

        // Test when the client can initialize itself successfully using the provided token.
        modelClient.init()
    }

    @Test
    fun modelClientFailsWithInitialNullValueForAuthToken() = runTest {
        AuthTestFixture.addExpectationsForFailingAuthenticationWithoutToken()
        val modelClient = ModelClientV2.builder()
            .url(AuthTestFixture.MODEL_SERVER_URL)
            .authToken { null }
            .build()

        val exception = assertFailsWith<ClientRequestException> {
            modelClient.init()
        }

        assertEquals(HttpStatusCode.Forbidden, exception.response.status)
    }

    @Test
    fun modelClientFailsWithoutAuthTokenProvider() = runTest {
        AuthTestFixture.addExpectationsForFailingAuthenticationWithoutToken()
        val modelClient = ModelClientV2.builder()
            .url(AuthTestFixture.MODEL_SERVER_URL)
            .build()

        val exception = assertFailsWith<ClientRequestException> {
            modelClient.init()
        }

        assertEquals(HttpStatusCode.Forbidden, exception.response.status)
    }
}

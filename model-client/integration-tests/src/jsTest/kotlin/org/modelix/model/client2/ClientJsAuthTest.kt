@file:OptIn(UnstableModelixFeature::class)

package org.modelix.model.client2

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import org.modelix.kotlin.utils.UnstableModelixFeature
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientJsAuthTest {

    @Test
    fun jsClientProvidesAuthToken() = runTest {
        AuthTestFixture.addExpectationsForSucceedingAuthenticationWithToken()

        // Test when the client can initialize itself successfully using the provided token.
        connectClient(AuthTestFixture.MODEL_SERVER_URL) { Promise.resolve(AuthTestFixture.AUTH_TOKEN) }.await()
    }

    @Test
    fun jsClientFailsWithoutAuthTokenProvider() = runTest {
        AuthTestFixture.addExpectationsForFailingAuthenticationWithoutToken()

        val exception = assertFailsWith<ClientRequestException> {
            connectClient(AuthTestFixture.MODEL_SERVER_URL).await()
        }

        assertEquals(HttpStatusCode.Forbidden, exception.response.status)
    }
}

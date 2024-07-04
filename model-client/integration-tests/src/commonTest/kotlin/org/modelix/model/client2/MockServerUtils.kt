package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom

/**
 * Wrapper to interact with the [mock-server](https://www.mock-server.com)
 * started by model-client-integration-tests/docker-compose.yaml
 *
 * Uses the REST API instead of JS and JAVA SDKs to be usable with multiplatform tests.
 * See https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi
 */
object MockServerUtils {
    // We do not start the mock server on a random port,
    // because we have no easy way to pass the port number into this test.
    // Reading the port from environment variables in KMP is not straight forward.
    // Therefore, a static port was chosen that will probably be free.
    const val MOCK_SERVER_BASE_URL = "http://0.0.0.0:55212"
    private val httpClient: HttpClient = HttpClient()

    suspend fun clearMockServer() {
        httpClient.put {
            expectSuccess = true
            url {
                takeFrom(MOCK_SERVER_BASE_URL)
                appendPathSegments("/mockserver/clear")
            }
        }
    }

    suspend fun addExpectation(expectationBody: String) {
        httpClient.put {
            expectSuccess = true
            url {
                takeFrom(MOCK_SERVER_BASE_URL)
                appendPathSegments("/mockserver/expectation")
            }
            contentType(ContentType.Application.Json)
            setBody(expectationBody)
        }
    }
}

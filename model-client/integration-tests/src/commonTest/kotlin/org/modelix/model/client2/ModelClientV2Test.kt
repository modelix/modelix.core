package org.modelix.model.client2

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ModelClientV2Test {

    @Test
    fun modelClientUsesProvidedAuthToken() = runTest {
        val tokenToUse = "someToken"
        MockServerUtils.clearMockServer()
        // language=json
        val postClientIdExpectation = """
        {
          "httpRequest": {
              "method": "POST",
              "path": "/modelClientUsesProvidedAuthToken/v2/generate-client-id",
              "headers": {
                "Authorization": [
                  "Bearer $tokenToUse"
                ]
              }
          },
          "httpResponse": {
            "body": {
              "not": true,
              "type": "STRING",
              "string": "3000"
            },
            "statusCode": 200
          }
        }
        """.trimIndent()
        // language=json
        val getUserIdExpectation = """
        {
          "httpRequest": {
              "method": "GET",
              "path": "/modelClientUsesProvidedAuthToken/v2/user-id",
              "headers": {
                "Authorization": [
                  "Bearer $tokenToUse"
                ]
              }
          },
          "httpResponse": {
            "body": {
              "not": true,
              "type": "STRING",
              "string": "someUser"
            },
            "statusCode": 200
          }
        }
        """.trimIndent()
        MockServerUtils.clearMockServer()
        MockServerUtils.addExpectation(postClientIdExpectation)
        MockServerUtils.addExpectation(getUserIdExpectation)
        val url = "${MockServerUtils.MOCK_SERVER_BASE_URL}/modelClientUsesProvidedAuthToken/v2"
        val modelClient = ModelClientV2.builder()
            .url(url)
            .authToken { tokenToUse }
            .build()

        // Test when the client can initialize itself successfully using the provided token.
        modelClient.init()
    }
}

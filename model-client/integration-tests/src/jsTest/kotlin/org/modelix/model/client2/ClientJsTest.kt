package org.modelix.model.client2

import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import org.modelix.kotlin.utils.UnstableModelixFeature
import kotlin.js.Promise
import kotlin.test.Test

class ClientJsTest {

    @OptIn(UnstableModelixFeature::class)
    @Test
    fun jsClientProvidesAuthToken() = runTest {
        val tokenToUse = "someToken"
        MockServerUtils.clearMockServer()
        // language=json
        val postClientIdExpectation = """
        {
          "httpRequest": {
              "method": "POST",
              "path": "/jsClientProvidesAuthToken/v2/generate-client-id",
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
              "path": "/jsClientProvidesAuthToken/v2/user-id",
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
        val url = "${MockServerUtils.MOCK_SERVER_BASE_URL}/JSClientProvidesAuthToken/v2"

        // Test when the client can initialize itself successfully using the provided token.
        connectClient(url) { Promise.resolve(tokenToUse) }.await()
    }
}

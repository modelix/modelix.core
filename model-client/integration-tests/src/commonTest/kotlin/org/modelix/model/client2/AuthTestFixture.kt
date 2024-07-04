/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.client2

/**
 * Common code between tests for authentication tests.
 */
object AuthTestFixture {
    const val AUTH_TOKEN = "someToken"
    const val MODEL_SERVER_URL: String = "${MockServerUtils.MOCK_SERVER_BASE_URL}/modelClientUsesProvidedAuthToken/v2"

    // language=json
    private val POST_CLIENT_ID_WITH_TOKEN_EXPECTATION = """
        {
          "httpRequest": {
              "method": "POST",
              "path": "/modelClientUsesProvidedAuthToken/v2/generate-client-id",
              "headers": {
                "Authorization": [
                  "Bearer $AUTH_TOKEN"
                ]
              }
          },
          "httpResponse": {
            "body": {
              "type": "STRING",
              "string": "3000"
            },
            "statusCode": 200
          }
        }
    """.trimIndent()

    // language=json
    private val GET_USER_ID_WITH_TOKEN_EXPECTATION = """
        {
          "httpRequest": {
              "method": "GET",
              "path": "/modelClientUsesProvidedAuthToken/v2/user-id",
              "headers": {
                "Authorization": [
                  "Bearer $AUTH_TOKEN"
                ]
              }
          },
          "httpResponse": {
            "body": {
              "type": "STRING",
              "string": "someUser"
            },
            "statusCode": 200
          }
        }
    """.trimIndent()

    // language=json
    private val POST_CLIENT_ID_WITHOUT_TOKEN_EXPECTATION = """
        {
          "httpRequest": {
              "method": "POST",
              "path": "/modelClientUsesProvidedAuthToken/v2/generate-client-id"
          },
          "httpResponse": {
            "body": {
              "type": "STRING",
              "string": "Forbidden"
            },
            "statusCode": 403
          }
        }
    """.trimIndent()

    suspend fun addExpectationsForSucceedingAuthenticationWithToken() {
        MockServerUtils.clearMockServer()
        MockServerUtils.addExpectation(POST_CLIENT_ID_WITH_TOKEN_EXPECTATION)
        MockServerUtils.addExpectation(GET_USER_ID_WITH_TOKEN_EXPECTATION)
    }

    suspend fun addExpectationsForFailingAuthenticationWithoutToken() {
        MockServerUtils.clearMockServer()
        MockServerUtils.addExpectation(POST_CLIENT_ID_WITHOUT_TOKEN_EXPECTATION)
    }
}

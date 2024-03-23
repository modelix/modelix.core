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

package org.modelix.authorization

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue


class WritePermissionOnRepositoryTest {
    val token = JWT.create()
        .withClaim("permissions", listOf("repository/myFirstRepo/write"))
        .sign(Algorithm.HMAC256("my-secret-key-8774567"))
        .let { JWT.decode(it) }
    val payloadJson = String(Base64.getUrlDecoder().decode(token.payload), StandardCharsets.UTF_8)
        .let { Json.parseToJsonElement(it).jsonObject }
    val input = DefaultAuthorizationInput(payloadJson)
    val evaluator = DefaultPermissionEvaluator(modelServerSchema, input)

    @BeforeTest
    fun beforeTest() {

    }

    @Test
    fun `can list the repository`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/list"))
    }

    @Test
    fun `can push to main branch`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/main/push"))
    }

    @Test
    fun `can write to repository`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/write"))
    }

}

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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class WritePermissionOnRepositoryTest : PermissionTestBase(listOf("repository/myFirstRepo/write")) {

    @Test
    fun `can list the repository`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/list"))
    }

    @Test
    fun `can push to main branch`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/main/push"))
    }

    @Test
    fun `can pull from main branch`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/main/pull"))
    }

    @Test
    fun `can write to repository`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/write"))
    }

    @Test
    fun `cannot force-push to main branch`() {
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/main/force-push"))
    }

    @Test
    fun `unknown permission throws exception`() {
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/myFirstRepo/branch/main/push/some-non-existent-permission")
        }
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/myFirstRepo/branch/main/some-non-existent-permission")
        }
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/myFirstRepo/branch/some-non-existent-permission")
        }
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/myFirstRepo/some-non-existent-permission")
        }
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/some-non-existent-permission")
        }
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("some-non-existent-permission")
        }
    }

}

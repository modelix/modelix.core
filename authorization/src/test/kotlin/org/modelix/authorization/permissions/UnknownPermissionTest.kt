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

package org.modelix.authorization.permissions

import org.junit.jupiter.api.assertThrows
import org.modelix.authorization.UnknownPermissionException
import kotlin.test.Test

class UnknownPermissionTest : PermissionTestBase(listOf("repository/myFirstRepo/write")) {

    @Test
    fun `unknown permission throws exception 6`() {
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/myFirstRepo/branch/main/push/some-non-existent-permission")
        }
    }

    @Test
    fun `unknown permission throws exception 5`() {
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/myFirstRepo/branch/main/some-non-existent-permission")
        }
    }

    @Test
    fun `unknown permission throws exception 4`() {
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/myFirstRepo/branch/some-non-existent-permission")
        }
    }

    @Test
    fun `unknown permission throws exception 3`() {
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/myFirstRepo/some-non-existent-permission")
        }
    }

    @Test
    fun `unknown permission throws exception 2`() {
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("repository/some-non-existent-permission")
        }
    }

    @Test
    fun `unknown permission throws exception 1`() {
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission("some-non-existent-permission")
        }
    }
}

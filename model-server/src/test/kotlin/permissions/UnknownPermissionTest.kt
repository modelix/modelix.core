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

package permissions

import org.junit.jupiter.api.assertThrows
import org.modelix.authorization.UnknownPermissionException
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.model.server.ModelServerPermissionSchema
import kotlin.test.Test

class UnknownPermissionTest : PermissionTestBase(listOf(ModelServerPermissionSchema.repository("myFirstRepo").write)) {

    @Test
    fun `unknown permission throws exception 6`() {
        `unknown permission throws exception`(5)
    }

    @Test
    fun `unknown permission throws exception 5`() {
        `unknown permission throws exception`(4)
    }

    @Test
    fun `unknown permission throws exception 4`() {
        `unknown permission throws exception`(3)
    }

    @Test
    fun `unknown permission throws exception 3`() {
        `unknown permission throws exception`(2)
    }

    @Test
    fun `unknown permission throws exception 2`() {
        `unknown permission throws exception`(1)
    }

    @Test
    fun `unknown permission throws exception 1`() {
        `unknown permission throws exception`(0)
    }

    fun `unknown permission throws exception`(n: Int) {
        assertThrows<UnknownPermissionException> {
            evaluator.hasPermission(PermissionParts(ModelServerPermissionSchema.repository("myFirstRepo").branch("main").push.parts.take(n)) + "some-non-existent-permission")
        }
    }
}

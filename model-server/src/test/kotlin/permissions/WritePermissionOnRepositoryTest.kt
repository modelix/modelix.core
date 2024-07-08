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

import org.modelix.model.server.ModelServerPermissionSchema
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WritePermissionOnRepositoryTest : PermissionTestBase(listOf(ModelServerPermissionSchema.repository("myFirstRepo").write)) {

    @Test
    fun `can list the repository`() {
        assertTrue(evaluator.hasPermission(ModelServerPermissionSchema.repository("myFirstRepo").list))
    }

    @Test
    fun `can push to main branch`() {
        assertTrue(evaluator.hasPermission(ModelServerPermissionSchema.repository("myFirstRepo").branch("main").push))
    }

    @Test
    fun `can pull from main branch`() {
        assertTrue(evaluator.hasPermission(ModelServerPermissionSchema.repository("myFirstRepo").branch("main").pull))
    }

    @Test
    fun `can write to repository`() {
        assertTrue(evaluator.hasPermission(ModelServerPermissionSchema.repository("myFirstRepo").write))
    }

    @Test
    fun `cannot force-push to main branch`() {
        assertFalse(evaluator.hasPermission(ModelServerPermissionSchema.repository("myFirstRepo").branch("main").forcePush))
    }
}

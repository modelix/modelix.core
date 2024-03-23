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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WritePermissionOnWorkspaceTest : PermissionTestBase(listOf("workspace/12345678/edit"), workspacesSchema) {

    @Test
    fun `can list the repository`() {
        assertTrue(evaluator.hasPermission("repository/workspace-12345678/list"))
    }

    @Test
    fun `can push to main branch`() {
        assertTrue(evaluator.hasPermission("repository/workspace-12345678/branch/main/push"))
    }

    @Test
    fun `can pull from main branch`() {
        assertTrue(evaluator.hasPermission("repository/workspace-12345678/branch/main/pull"))
    }

    @Test
    fun `can write to repository`() {
        assertTrue(evaluator.hasPermission("repository/workspace-12345678/write"))
    }

    @Test
    fun `cannot force-push to main branch`() {
        assertFalse(evaluator.hasPermission("repository/workspace-12345678/branch/main/force-push"))
    }

    @Test
    fun `check all granted permissions`() {
        evaluator.instantiatePermission("repository/workspace-12345678/branch/main/push")
        val allGranted = evaluator.getAllGrantedPermissions().map { it.toString() }.toSet()
        assertEquals(
            sortedSetOf(
                "repository/workspace-12345678/branch/main/create",
                "repository/workspace-12345678/branch/main/list",
                "repository/workspace-12345678/branch/main/pull",
                "repository/workspace-12345678/branch/main/push",
                "repository/workspace-12345678/branch/main/query",
                "repository/workspace-12345678/branch/main/read",
                "repository/workspace-12345678/branch/main/write",
                "repository/workspace-12345678/create",
                "repository/workspace-12345678/list",
                "repository/workspace-12345678/objects/read",
                "repository/workspace-12345678/read",
                "repository/workspace-12345678/write",
                "workspace/12345678/edit",
                "workspace/12345678/read-model",
                "workspace/12345678/start",
                "workspace/12345678/view",
                "workspace/12345678/write-model",
            ),
            allGranted.toSortedSet(),
        )
    }
}

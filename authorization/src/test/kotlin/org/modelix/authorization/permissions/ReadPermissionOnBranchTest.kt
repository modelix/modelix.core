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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadPermissionOnBranchTest : PermissionTestBase(listOf("repository/myFirstRepo/branch/myFeatureBranch/read")) {

    @Test
    fun `can list the repository`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/list"))
    }

    @Test
    fun `cannot list other repositories`() {
        assertFalse(evaluator.hasPermission("repository/some-other-repo/list"))
    }

    @Test
    fun `can read objects from the repository`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/objects/read"))
    }

    @Test
    fun `can list the branch`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/myFeatureBranch/list"))
    }

    @Test
    fun `cannot list other branches`() {
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/main/list"))
    }

    @Test
    fun `can pull from branch`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/myFeatureBranch/pull"))
    }

    @Test
    fun `cannot pull from other branches`() {
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/main/pull"))
    }

    @Test
    fun `cannot create new branches`() {
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/new-branch/create"))
    }

    @Test
    fun `cannot push to branch`() {
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/myFeatureBranch/push"))
    }
}

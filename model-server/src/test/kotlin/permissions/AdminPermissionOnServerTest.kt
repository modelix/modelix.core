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
import kotlin.test.assertEquals

class AdminPermissionOnServerTest : PermissionTestBase(listOf(ModelServerPermissionSchema.modelServer.admin)) {

    @Test
    fun `check all granted permissions`() {
        evaluator.instantiatePermission(ModelServerPermissionSchema.repository("my-repo").branch("my-branch").read)
        val allGranted = evaluator.getAllGrantedPermissions().map { it.toString() }.toSet()
        assertEquals(
            sortedSetOf(
                "legacy-global-objects/add",
                "legacy-global-objects/read",
                "legacy-user-defined-entries/read",
                "legacy-user-defined-entries/write",
                "model-server/admin",
                "permission-schema/read",
                "permission-schema/write",
                "repository/my-repo/admin",
                "repository/my-repo/branch/my-branch/admin",
                "repository/my-repo/branch/my-branch/create",
                "repository/my-repo/branch/my-branch/delete",
                "repository/my-repo/branch/my-branch/force-push",
                "repository/my-repo/branch/my-branch/list",
                "repository/my-repo/branch/my-branch/pull",
                "repository/my-repo/branch/my-branch/push",
                "repository/my-repo/branch/my-branch/query",
                "repository/my-repo/branch/my-branch/read",
                "repository/my-repo/branch/my-branch/rewrite",
                "repository/my-repo/branch/my-branch/write",
                "repository/my-repo/create",
                "repository/my-repo/delete",
                "repository/my-repo/list",
                "repository/my-repo/objects/add",
                "repository/my-repo/objects/read",
                "repository/my-repo/read",
                "repository/my-repo/rewrite",
                "repository/my-repo/write",
            ),
            allGranted.toSortedSet(),
        )
    }
}

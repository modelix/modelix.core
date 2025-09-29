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
                "repository/my-repo/any-branch/admin",
                "repository/my-repo/any-branch/read",
                "repository/my-repo/any-branch/rewrite",
                "repository/my-repo/any-branch/write",
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

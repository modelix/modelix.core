package permissions

import org.junit.jupiter.api.Disabled
import org.modelix.authorization.permissions.PermissionParts
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WildcardBranchPermissionsTest : PermissionTestBase(
    listOf(
        PermissionParts.fromString("repository/myFirstRepo/branch/explicitly-deletable/delete"),
        PermissionParts.fromString("repository/myFirstRepo/branch/*/write"),
        PermissionParts.fromString("repository/myFirstRepo/branch/*/delete"),
    ),
) {

    @Test
    fun `can push to branch matching wildcard`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/user-named%2Ffeature-123/push"))
    }

    @Test
    fun `cannot force push since that was granted nowhere`() {
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/user-named%2Ffeature-123/force-push"))
    }

    @Disabled
    @Test
    fun `cannot push to branch not matching wildcard`() {
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/not-allowed/push"))
    }

    @Test
    fun `can delete explicitly deletable branch`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/explicitly-deletable/delete"))
    }

    @Test
    fun `can delete branch matching wildcard`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/user-named%2Ffeature-123/delete"))
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/user-named%2Fbugfix-456/delete"))
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/user-named%2Fsubdir%2Ffeature/delete"))
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/user-named%2F/delete"))
    }

    @Disabled
    @Test
    fun `cannot delete branch not matching wildcard`() {
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/non-deletable-branch/delete"))
        assertFalse(evaluator.hasPermission("repository/myFirstRepo/branch/user-named/delete"))
    }
}

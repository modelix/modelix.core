package permissions

import org.modelix.model.server.ModelServerPermissionSchema
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadPermissionOnBranchTest : PermissionTestBase(listOf(ModelServerPermissionSchema.repository("myFirstRepo").branch("myFeatureBranch").read)) {

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

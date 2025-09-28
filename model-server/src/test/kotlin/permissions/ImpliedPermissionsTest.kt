package permissions

import org.modelix.model.server.ModelServerPermissionSchema
import kotlin.test.Test
import kotlin.test.assertTrue

class ImpliedPermissionsTest : PermissionTestBase(
    listOf(
        ModelServerPermissionSchema.repository("myFirstRepo").rewrite,
    ),
) {

    @Test
    fun `can delete any branch`() {
        assertTrue(evaluator.hasPermission("repository/myFirstRepo/branch/just-any/delete"))
    }
}

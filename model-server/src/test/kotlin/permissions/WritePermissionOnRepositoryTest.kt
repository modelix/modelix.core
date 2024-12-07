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

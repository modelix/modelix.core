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

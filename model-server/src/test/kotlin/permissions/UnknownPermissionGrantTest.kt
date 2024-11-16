package permissions

import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.SchemaInstance
import org.modelix.model.server.ModelServerPermissionSchema
import kotlin.test.Test

class UnknownPermissionGrantTest {
    /**
     * A token may contain granted permission of other services. They should not result in an exception.
     */
    @Test
    fun `unknown permission in token is ignored`() {
        val evaluator = PermissionEvaluator(SchemaInstance(ModelServerPermissionSchema.SCHEMA))
        for (i in 0..5) {
            evaluator.grantPermission(PermissionParts(ModelServerPermissionSchema.repository("myFirstRepo").branch("main").push.parts.take(i)) + "some-non-existent-permission")
        }
    }
}

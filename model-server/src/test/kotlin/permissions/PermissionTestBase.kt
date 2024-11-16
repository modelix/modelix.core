package permissions

import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.Schema
import org.modelix.authorization.permissions.SchemaInstance
import org.modelix.model.server.ModelServerPermissionSchema

abstract class PermissionTestBase(private val explicitlyGrantedPermissions: List<PermissionParts>, val schema: Schema = ModelServerPermissionSchema.SCHEMA) {
    val evaluator = PermissionEvaluator(SchemaInstance(schema)).also { evaluator ->
        explicitlyGrantedPermissions.forEach { evaluator.grantPermission(it) }
    }
}

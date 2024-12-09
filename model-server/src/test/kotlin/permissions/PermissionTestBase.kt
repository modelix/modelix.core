package permissions

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.Schema
import org.modelix.authorization.permissions.SchemaInstance
import org.modelix.model.server.ModelServerPermissionSchema
import java.nio.charset.StandardCharsets
import java.util.Base64

abstract class PermissionTestBase(private val explicitlyGrantedPermissions: List<PermissionParts>, val schema: Schema = ModelServerPermissionSchema.SCHEMA) {
    val token = JWT.create()
        .withClaim("permissions", explicitlyGrantedPermissions.map { it.toString() })
        .sign(Algorithm.HMAC256("my-secret-key-8774567"))
        .let { JWT.decode(it) }
    val payloadJson = String(Base64.getUrlDecoder().decode(token.payload), StandardCharsets.UTF_8)
        .let { Json.parseToJsonElement(it).jsonObject }
    val evaluator = PermissionEvaluator(SchemaInstance(schema)).also { evaluator ->
        explicitlyGrantedPermissions.forEach { evaluator.grantPermission(it) }
    }
}

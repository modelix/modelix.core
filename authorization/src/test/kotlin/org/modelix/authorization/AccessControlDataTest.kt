package org.modelix.authorization

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.modelix.authorization.permissions.AccessControlData
import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.SchemaInstance
import org.modelix.authorization.permissions.buildPermissionSchema
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessControlDataTest {
    private val schema = buildPermissionSchema {
        resource("r1") {
            permission("delete") {
                permission("write") {
                    permission("read")
                }
            }
        }
    }
    private val email = "unit-tests@example.com"

    @Test
    fun `can grant permissions to identity tokens`() {
        val token = JWT.create()
            .withClaim("email", email)
            .sign(Algorithm.HMAC256("unit-tests"))
            .let { JWT.decode(it) }
        val data = AccessControlData().withGrantToUser(email, PermissionParts("r1", "write").fullId)
        val evaluator = PermissionEvaluator(SchemaInstance(schema))

        assertFalse(evaluator.hasPermission(PermissionParts("r1", "read")))
        assertFalse(evaluator.hasPermission(PermissionParts("r1", "write")))
        assertFalse(evaluator.hasPermission(PermissionParts("r1", "delete")))

        data.load(token, evaluator)

        assertTrue(evaluator.hasPermission(PermissionParts("r1", "read")))
        assertTrue(evaluator.hasPermission(PermissionParts("r1", "write")))
        assertFalse(evaluator.hasPermission(PermissionParts("r1", "delete")))
    }

    @Test
    fun `granted permissions are not applied to access tokens`() {
        val email = "unit-tests@example.com"
        val token = ModelixJWTUtil()
            .also { it.setHmac512Key("xxx") }
            .createAccessToken(email, emptyList())
            .let { JWT.decode(it) }
        val data = AccessControlData().withGrantToUser(email, PermissionParts("r1", "write").fullId)
        val evaluator = PermissionEvaluator(SchemaInstance(schema))

        assertFalse(evaluator.hasPermission(PermissionParts("r1", "read")))
        assertFalse(evaluator.hasPermission(PermissionParts("r1", "write")))
        assertFalse(evaluator.hasPermission(PermissionParts("r1", "delete")))

        data.load(token, evaluator)

        assertFalse(evaluator.hasPermission(PermissionParts("r1", "read")))
        assertFalse(evaluator.hasPermission(PermissionParts("r1", "write")))
        assertFalse(evaluator.hasPermission(PermissionParts("r1", "delete")))
    }
}

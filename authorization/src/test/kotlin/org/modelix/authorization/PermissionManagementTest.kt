package org.modelix.authorization

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.authorization.permissions.buildPermissionSchema
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionManagementTest {
    private val schema = buildPermissionSchema {
        resource("server") {
            permission("admin")
            resource("repository") {
                parameter("name")
                permission("owner") {
                    permission("write") {
                        permission("read")
                    }
                }
            }
        }
    }
    private val hmacKey = "unit-test"

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ModelixAuthorization) {
                permissionSchema = schema
                hmac512Key = hmacKey
                permissionManagementEnabled = true
                installStatusPages = true
            }
        }
        block()
    }

    private fun createToken(user: String, vararg permissions: String): String {
        return ModelixJWTUtil().also { it.setHmac512Key(hmacKey) }.createAccessToken(user, permissions.toList())
    }

    @Test
    fun `direct resource owner can grant permission`() = runTest {
        val response = client.submitForm(
            url = "http://localhost/permissions/grant",
            formParameters = parameters {
                append("userId", "userB")
                append("permissionId", "server/repository/my-repo/read")
            },
            block = {
                bearerAuth(createToken("userA", "server/repository/my-repo/owner"))
            },
        )
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `indirect resource owner can grant permission`() = runTest {
        val response = client.submitForm(
            url = "http://localhost/permissions/grant",
            formParameters = parameters {
                append("userId", "userB")
                append("permissionId", "server/repository/my-repo/read")
            },
            block = {
                bearerAuth(createToken("userA", "server/admin"))
            },
        )
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `non-owners cannot grant permissions`() = runTest {
        val response = client.submitForm(
            url = "http://localhost/permissions/grant",
            formParameters = parameters {
                append("userId", "userB")
                append("permissionId", "server/repository/my-repo/read")
            },
            block = {
                bearerAuth(createToken("userA", "server/repository/my-repo/write"))
            },
        )
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}

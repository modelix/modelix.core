@file:Suppress("ClassName")

package org.modelix.authorization.permissions

object PermissionSchemaBase {
    val SCHEMA: Schema = SchemaBuilder().apply {
        resource("permission-data") {
            permission("write") {
                includedIn("cluster", "admin")
                permission("read")
            }
        }
        resource("cluster") {
            permission("admin") {
                permission("user")
            }
        }
    }.build()

    object permissionData {
        val resource = PermissionParts("permission-data")
        val write = resource + "write"
        val read = resource + "read"
    }

    object cluster {
        val resource = PermissionParts("cluster")
        val admin = resource + "admin"
        val user = resource + "user"
    }
}

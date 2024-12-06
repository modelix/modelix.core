/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

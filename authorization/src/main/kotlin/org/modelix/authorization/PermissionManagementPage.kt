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

package org.modelix.authorization

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.plugin
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.postForm
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.tr
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.PermissionSchemaBase

fun Route.installPermissionManagementHandlers() {
    route("permissions") {
        get("manage") {
            call.respondHtml {
                buildPermissionManagementPage(call, application.plugin(ModelixAuthorization))
            }
        }
        post("grant") {
            val formParameters = call.receiveParameters()
            val userId = formParameters["userId"]
            val roleId = formParameters["roleId"]
            require(userId != null || roleId != null) { "userId or roleId required" }
            val permissionId = requireNotNull(formParameters["permissionId"]) { "permissionId not specified" }

            // a user can grant his own permission to other users
            checkPermission(PermissionParts.fromString(permissionId))

            if (userId != null) {
                application.plugin(ModelixAuthorization).config.accessControlPersistence.update {
                    it.withGrantToUser(userId, permissionId)
                }
            }
            if (roleId != null) {
                application.plugin(ModelixAuthorization).config.accessControlPersistence.update {
                    it.withGrantToRole(roleId, permissionId)
                }
            }
            call.respond("Granted $permissionId to ${userId ?: roleId}")
        }
        post("remove-grant") {
            call.checkPermission(PermissionSchemaBase.permissionData.write)
            val formParameters = call.receiveParameters()
            val userId = formParameters["userId"]
            val roleId = formParameters["roleId"]
            require(userId != null || roleId != null) { "userId or roleId required" }
            val permissionId = requireNotNull(formParameters["permissionId"]) { "permissionId not specified" }
            if (userId != null) {
                application.plugin(ModelixAuthorization).config.accessControlPersistence.update {
                    it.withoutGrantToUser(userId, permissionId)
                }
            }
            if (roleId != null) {
                application.plugin(ModelixAuthorization).config.accessControlPersistence.update {
                    it.withoutGrantToUser(roleId, permissionId)
                }
            }
            call.respond("Removed $permissionId to ${userId ?: roleId}")
        }
    }
}

fun HTML.buildPermissionManagementPage(call: ApplicationCall, pluginInstance: ModelixAuthorizationPluginInstance) {
    val schemaInstance = pluginInstance.config.permissionSchema
    head {
        style {
            //language=CSS
            +"""
                table {
                    border: 1px solid #ccc;
                    border-collapse: collapse;
                }
                td, th {
                    border: 1px solid #ccc;
                    padding: 3px 12px;
                }
            """.trimIndent()
        }
    }
    body {
        h1 {
            +"Grant Permission"
        }
        postForm(action = "grant") {
            +"Grant permission"
            textInput {
                name = "permissionId"
            }
            +" to user "
            textInput {
                name = "userId"
            }
            submitInput {
                value = "Grant"
            }
        }

        h1 {
            +"Granted Permissions"
        }

        table {
            tr {
                th { +"User" }
                th { +"Permission" }
            }
            for ((userId, permission) in pluginInstance.config.accessControlPersistence.read().grantsToUsers.flatMap { entry -> entry.value.map { entry.key to it } }) {
                if (!call.hasPermission(PermissionParts.fromString(permission))) continue

                tr {
                    td {
                        +userId
                    }
                    td {
                        +permission
                    }
                    td {
                        postForm(action = "remove-grant") {
                            hiddenInput {
                                name = "userId"
                                value = userId
                            }
                            hiddenInput {
                                name = "permissionId"
                                value = permission
                            }
                            submitInput {
                                value = "Remove"
                            }
                        }
                    }
                }
            }
        }

        br {}

        table {
            tr {
                th { +"Role" }
                th { +"Permission" }
            }
            for ((roleId, permission) in pluginInstance.config.accessControlPersistence.read().grantsToRoles.flatMap { entry -> entry.value.map { entry.key to it } }) {
                if (!call.hasPermission(PermissionParts.fromString(permission))) continue

                tr {
                    td {
                        +roleId
                    }
                    td {
                        +permission
                    }
                    td {
                        postForm(action = "remove-grant") {
                            hiddenInput {
                                name = "roleId"
                                value = roleId
                            }
                            hiddenInput {
                                name = "permissionId"
                                value = permission
                            }
                            submitInput {
                                value = "Remove"
                            }
                        }
                    }
                }
            }
        }

        h1 {
            +"Denied Permissions"
        }

        table {
            tr {
                th { +"User" }
                th { +"Denied Permission" }
                th { +"Grant" }
            }
            for (deniedPermission in pluginInstance.getDeniedPermissions()) {
                if (!call.hasPermission(deniedPermission.permissionId)) continue

                val userId = deniedPermission.userId
                tr {
                    td {
                        +userId.orEmpty()
                    }
                    td {
                        +deniedPermission.permissionId.fullId
                    }
                    td {
                        if (userId != null) {
                            val evaluator = pluginInstance.createPermissionEvaluator()
                            val permissionInstance = evaluator.instantiatePermission(deniedPermission.permissionId)
                            val candidates = (setOf(permissionInstance) + permissionInstance.transitiveIncludedIn())
                            postForm(action = "grant") {
                                hiddenInput {
                                    name = "userId"
                                    value = userId
                                }
                                for (candidate in candidates) {
                                    div {
                                        submitInput {
                                            name = "permissionId"
                                            value = candidate.ref.toString()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

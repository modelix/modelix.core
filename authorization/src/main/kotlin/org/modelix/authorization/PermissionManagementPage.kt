package org.modelix.authorization

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.plugin
import io.ktor.server.auth.principal
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
import org.modelix.authorization.permissions.PermissionInstanceReference
import org.modelix.authorization.permissions.PermissionParser

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
            call.checkCanGranPermission(permissionId)

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
            val formParameters = call.receiveParameters()
            val userId = formParameters["userId"]
            val roleId = formParameters["roleId"]
            require(userId != null || roleId != null) { "userId or roleId required" }
            val permissionId = requireNotNull(formParameters["permissionId"]) { "permissionId not specified" }
            call.checkCanGranPermission(permissionId)
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
        br {}
        postForm(action = "grant") {
            +"Grant permission"
            textInput {
                name = "permissionId"
            }
            +" to role "
            textInput {
                name = "roleId"
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
                if (!call.canGrantPermission(permission)) continue

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
                if (!call.canGrantPermission(permission)) continue

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
                if (!call.canGrantPermission(deniedPermission.permissionRef)) continue

                val userId = deniedPermission.userId
                tr {
                    td {
                        +userId
                    }
                    td {
                        +deniedPermission.permissionRef.toPermissionParts().fullId
                    }
                    td {
                        val evaluator = pluginInstance.createPermissionEvaluator()
                        val permissionInstance = evaluator.instantiatePermission(deniedPermission.permissionRef)
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

fun ApplicationCall.canGrantPermission(permissionId: String): Boolean {
    return canGrantPermission(parsePermission(permissionId))
}

fun ApplicationCall.canGrantPermission(permissionRef: PermissionInstanceReference): Boolean {
    val plugin = application.plugin(ModelixAuthorization)
    val schema = plugin.config.permissionSchema
    val resources = generateSequence(permissionRef.resource) { it.parent }
    return resources.any {
        // hardcoded admin/owner to keep it simple and not having to introduce a permission schema for permissions
        val managers = listOf(
            PermissionInstanceReference("admin", it),
            PermissionInstanceReference("owner", it),
        )
        managers.any { it.isValid(schema) && plugin.hasPermission(this, it) }
    }
}

fun ApplicationCall.checkCanGranPermission(id: String) {
    if (!canGrantPermission(id)) {
        val principal = principal<AccessTokenPrincipal>()
        throw NoPermissionException(principal, null, null, "${principal?.getUserName()} has no permission '$id'")
    }
}

fun ApplicationCall.parsePermission(id: String): PermissionInstanceReference {
    return application.plugin(ModelixAuthorization).config.permissionSchema.let { PermissionParser(it) }.parse(id)
}

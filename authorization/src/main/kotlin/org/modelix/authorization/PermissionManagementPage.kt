package org.modelix.authorization

import io.ktor.http.encodeURLPathPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.application
import io.ktor.server.application.call
import io.ktor.server.application.plugin
import io.ktor.server.auth.principal
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.dataList
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.postForm
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe
import org.modelix.authorization.permissions.PermissionInstanceReference
import org.modelix.authorization.permissions.PermissionParser
import org.modelix.authorization.permissions.PermissionParts
import org.modelix.authorization.permissions.PermissionSchemaBase
import org.modelix.authorization.permissions.ResourceInstanceReference
import org.modelix.authorization.permissions.SchemaInstance

fun Route.installPermissionManagementHandlers() {
    route("permissions") {
        get("/") {
            call.respondHtml {
                body {
                    ul {
                        li { a(href = "schema") { +"Schema" } }
                        li { a(href = "resources/") { +"Resources" } }
                    }
                }
            }
        }
        get("schema") {
            call.respondHtml {
                buildSchemaPage(call.application.plugin(ModelixAuthorization).config.permissionSchema)
            }
        }
        route("resources") {
            get("/") {
                val schemaInstance = call.application.plugin(ModelixAuthorization).createSchemaInstance()
                val rootResources = schemaInstance.resources.values.filter { it.parent == null }
                call.respondHtml {
                    body {
                        h1 { +"Resources" }
                        ul {
                            for (resourceInstance in rootResources) {
                                val resourceId = resourceInstance.reference.toPermissionParts().fullId
                                li {
                                    a(href = "$resourceId/") {
                                        +resourceId
                                    }
                                }
                            }
                        }
                    }
                }
            }
            route("{resourceId}") {
                fun RoutingContext.resourceId(): String = call.parameters["resourceId"]!!
                get("/") {
                    val resourceId = resourceId()
                    val plugin = call.application.plugin(ModelixAuthorization)
                    val schemaInstance = plugin.createSchemaInstance()
                    val childResources = schemaInstance.resources.values.filter { it.parent?.reference?.toPermissionParts()?.fullId == resourceId }
                    val resourceRef = requireNotNull(PermissionParser(schemaInstance.schema).parseResource(PermissionParts.fromString(resourceId))) { "Unknown resource: $resourceId" }
                    val parentResourceRef = resourceRef.parent
                    val permissions = schemaInstance.instantiateResource(resourceRef).permissions.values
                        .map { it to it.transitiveIncludes().size }
                        .sortedByDescending { it.second }
                        .map { it.first }
                    val accessControlData = plugin.config.accessControlPersistence.read()
                    val permissionToUsers = accessControlData.grantsToUsers.inverse()
                    val permissionToRoles = accessControlData.grantsToRoles.inverse()
                    call.respondHtml {
                        head {
                            style {
                                unsafe {
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
                        }
                        body {
                            dataList {
                                id = "knownUsers"
                                for (userId in plugin.config.accessControlPersistence.read().knownUsers.sorted()) {
                                    option { value = userId }
                                }
                            }
                            dataList {
                                id = "knownRoles"
                                for (roleId in plugin.config.accessControlPersistence.read().knownRoles.sorted()) {
                                    option { value = roleId }
                                }
                            }

                            div {
                                a(href = "../") { +"Root Resources" }
                            }

                            h1 { +"Resource $resourceId" }

                            if (parentResourceRef != null) {
                                div {
                                    val parentId = parentResourceRef.toPermissionParts().fullId
                                    +"Parent: "
                                    a(href = "../${parentId.encodeURLPathPart()}/") { +parentId }
                                }
                            }

                            h2 { +"Child Resources" }
                            ul {
                                for (resourceInstance in childResources) {
                                    val resourceId = resourceInstance.reference.toPermissionParts().fullId
                                    li {
                                        a(href = "../${resourceId.encodeURLPathPart()}/") {
                                            +resourceId
                                        }
                                    }
                                }
                            }

                            val canManagePermissions = call.canManagePermissions(resourceRef)
                            h2 { +"Permissions" }
                            table {
                                tr {
                                    th { +"Permission" }
                                    th { +"Description" }
                                    th { +"Includes" }
                                    th { +"Included In" }
                                    if (canManagePermissions) {
                                        th { +"Existing Grants" }
                                        th { +"New Grant" }
                                    }
                                }
                                for (permission in permissions) {
                                    tr {
                                        td { +permission.ref.permissionName }
                                        td { +permission.permissionSchema.description.orEmpty() }
                                        td { buildPermissionIncludesList(resourceRef, permission.includes) }
                                        td { buildPermissionIncludesList(resourceRef, permission.includedIn) }
                                        if (canManagePermissions) {
                                            td {
                                                val users = (permissionToUsers[permission.ref.fullId] ?: emptySet()).sorted()
                                                val roles = (permissionToRoles[permission.ref.fullId] ?: emptySet()).sorted()
                                                for (user in users) {
                                                    div {
                                                        postForm(action = "permissions/${permission.ref.permissionName.encodeURLPathPart()}/remove-grant") {
                                                            hiddenInput {
                                                                name = "userId"
                                                                value = user
                                                            }
                                                            submitInput {
                                                                value = "Remove grant to user $user"
                                                            }
                                                        }
                                                    }
                                                }
                                                for (role in roles) {
                                                    div {
                                                        postForm(action = "permissions/${permission.ref.permissionName.encodeURLPathPart()}/remove-grant") {
                                                            hiddenInput {
                                                                name = "roleId"
                                                                value = role
                                                            }
                                                            submitInput {
                                                                value = "Remove grant to role $role"
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            td {
                                                postForm(action = "permissions/${permission.ref.permissionName.encodeURLPathPart()}/grant") {
                                                    +"User: "
                                                    textInput {
                                                        name = "userId"
                                                        list = "knownUsers"
                                                    }
                                                    +" "
                                                    submitInput {
                                                        value = "Grant"
                                                    }
                                                }
                                                br()
                                                postForm(action = "permissions/${permission.ref.permissionName.encodeURLPathPart()}/grant") {
                                                    +"Role: "
                                                    textInput {
                                                        name = "roleId"
                                                        list = "knownRoles"
                                                    }
                                                    +" "
                                                    submitInput {
                                                        value = "Grant"
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
                route("permissions") {
                    route("{permissionName}") {
                        fun RoutingContext.permissionName(): String = call.parameters["permissionName"]!!
                        fun RoutingContext.permissionId(): String = "${resourceId()}/${permissionName()}"
                        post("grant") {
                            val formParameters = call.receiveParameters()
                            grant(formParameters["userId"], formParameters["roleId"], permissionId())
                            call.respondRedirect("../../")
                        }
                        post("remove-grant") {
                            val formParameters = call.receiveParameters()
                            removeGrant(formParameters["userId"], formParameters["roleId"], permissionId())
                            call.respondRedirect("../../")
                        }
                    }
                }
            }
        }

        post("grant") {
            val formParameters = call.receiveParameters()
            val userId = formParameters["userId"]
            val roleId = formParameters["roleId"]
            val permissionId = requireNotNull(formParameters["permissionId"]) { "permissionId not specified" }
            grant(userId, roleId, permissionId)
            call.respond("Granted $permissionId to ${userId ?: roleId}")
        }
        post("remove-grant") {
            val formParameters = call.receiveParameters()
            val userId = formParameters["userId"]
            val roleId = formParameters["roleId"]
            val permissionId = requireNotNull(formParameters["permissionId"]) { "permissionId not specified" }
            removeGrant(userId, roleId, permissionId)
            call.respond("Removed $permissionId to ${userId ?: roleId}")
        }
    }
}

fun FlowContent.buildPermissionIncludesList(currentResource: ResourceInstanceReference, permissionList: Set<SchemaInstance.ResourceInstance.PermissionInstance>) {
    val items = permissionList.map {
        if (it.ref.resource == currentResource) {
            null to it.ref.permissionName
        } else {
            it.ref.resource.fullId to it.ref.fullId
        }
    }.sortedWith(compareBy<Pair<String?, String>> { it.first }.thenComparing { it.second })
    ul {
        for (item in items) {
            li {
                val resourceId = item.first
                if (resourceId == null) {
                    +item.second
                } else {
                    a(href = "../${resourceId.encodeURLPathPart()}/") {
                        +item.second
                    }
                }
            }
        }
    }
}

private fun RoutingContext.grant(userId: String?, roleId: String?, permissionId: String) {
    val userId = userId?.takeIf { it.isNotBlank() }
    val roleId = roleId?.takeIf { it.isNotBlank() }
    require(userId != null || roleId != null) { "userId or roleId required" }
    call.checkCanGranPermission(permissionId)

    if (userId != null) {
        call.application.plugin(ModelixAuthorization).config.accessControlPersistence.update {
            it.withGrantToUser(userId, permissionId)
        }
    }
    if (roleId != null) {
        call.application.plugin(ModelixAuthorization).config.accessControlPersistence.update {
            it.withGrantToRole(roleId, permissionId)
        }
    }
}

private fun RoutingContext.removeGrant(userId: String?, roleId: String?, permissionId: String) {
    require(userId != null || roleId != null) { "userId or roleId required" }
    call.checkCanGranPermission(permissionId)
    if (userId != null) {
        call.application.plugin(ModelixAuthorization).config.accessControlPersistence.update {
            it.withoutGrantToUser(userId, permissionId)
        }
    }
    if (roleId != null) {
        call.application.plugin(ModelixAuthorization).config.accessControlPersistence.update {
            it.withoutGrantToUser(roleId, permissionId)
        }
    }
}

fun ApplicationCall.canGrantPermission(permissionId: String): Boolean {
    return canGrantPermission(parsePermission(permissionId))
}

fun ApplicationCall.canGrantPermission(permissionRef: PermissionInstanceReference): Boolean {
    return canManagePermissions(permissionRef.resource)
}

fun ApplicationCall.canManagePermissions(resourceRef: ResourceInstanceReference): Boolean {
    val plugin = application.plugin(ModelixAuthorization)
    if (plugin.hasPermission(this, PermissionSchemaBase.permissionData.write)) return true
    val schema = plugin.config.permissionSchema
    val resources = generateSequence(resourceRef) { it.parent }
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

private fun <K, V> Map<K, Set<V>>.inverse(): Map<V, Set<K>> {
    return entries.asSequence()
        .flatMap { entry -> entry.value.map { it to entry.key } }
        .groupBy { it.first }
        .mapValues { it.value.asSequence().map { it.second }.toSet() }
}

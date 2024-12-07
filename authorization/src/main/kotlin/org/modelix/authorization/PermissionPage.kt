package org.modelix.authorization

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.UL
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.li
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul
import org.modelix.authorization.permissions.PermissionEvaluator
import org.modelix.authorization.permissions.Resource
import org.modelix.authorization.permissions.ResourceInstanceReference
import org.modelix.authorization.permissions.SchemaInstance

fun HTML.buildPermissionPage(evaluator: PermissionEvaluator) {
    buildPermissionPage(evaluator.schemaInstance)
}

fun HTML.buildPermissionPage(schemaInstance: SchemaInstance) {
    instantiateParameterizedResources(schemaInstance)
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
            +"Known Permissions"
        }
        schemaInstance.resources.values.filter { it.parent == null }.forEachIndexed { index, resourceInstance ->
            if (index != 0) br { }
            buildResourceInstance(resourceInstance)
        }

        h1 {
            +"Permission Schema"
        }
        schemaInstance.schema.resources.values.forEachIndexed { index, resource ->
            if (index != 0) br { }
            buildResource(resource)
        }
    }
}

private fun FlowContent.buildResourceInstance(resourceInstance: SchemaInstance.ResourceInstance) {
    div {
        style = "border:1px solid black;border-radius:12px"
        div {
            style = "background-color:#ccf;border-top-left-radius:12px;border-top-right-radius:12px;border-bottom:1px solid black;padding:6px"
            +"Resource: "
            +resourceInstance.reference.toPermissionParts().toString(noEscape = true)
        }

        div {
            style = "padding:24px"

            div {
                style = "border:1px solid black;border-radius:12px"
                div {
                    style = "background-color:#cfc;border-top-left-radius:12px;border-top-right-radius:12px;border-bottom:1px solid black;padding:6px"
                    +"Permissions"
                }
                ul {
                    resourceInstance.permissions.values.forEach { permissionInstance ->
                        buildPermissionInstance(permissionInstance)
                    }
                }
            }

            resourceInstance.childResources.values.forEach { childResource ->
                br { }
                buildResourceInstance(childResource)
            }
        }
    }
}

private fun UL.buildPermissionInstance(permissionInstance: SchemaInstance.ResourceInstance.PermissionInstance) {
    li {
        +permissionInstance.ref.toPermissionParts().toString(noEscape = true)
    }
}

private fun FlowContent.buildResource(resource: Resource) {
    div {
        style = "border:1px solid black;border-radius:12px"
        div {
            style = "background-color:#ccf;border-top-left-radius:12px;border-top-right-radius:12px;border-bottom:1px solid black;padding:6px"
            +"Resource: "
            +resource.name
            if (resource.parameters.isNotEmpty()) {
                +"("
                +resource.parameters.joinToString(", ")
                +")"
            }
        }

        div {
            style = "padding:24px"

            div {
                style = "border:1px solid black;border-radius:12px"
                div {
                    style = "background-color:#cfc;border-top-left-radius:12px;border-top-right-radius:12px;border-bottom:1px solid black;padding:6px"
                    +"Permissions"
                }
                div {
                    style = "padding:12px"
                    table {
                        tr {
                            th { +"Name" }
                            th { +"Includes" }
                            th { +"Included In" }
                            th { +"Description" }
                        }
                        resource.permissions.values.forEach { permission ->
                            tr {
                                td {
                                    +permission.name
                                }
                                td {
                                    permission.includes.forEach { otherPermission ->
                                        div {
                                            +otherPermission.toString()
                                        }
                                    }
                                }
                                td {
                                    permission.includedIn.forEach { otherPermission ->
                                        div {
                                            +otherPermission.toString()
                                        }
                                    }
                                }
                                td {
                                    +(permission.description.orEmpty())
                                }
                            }
                        }
                    }
                }
            }

            resource.resources.values.forEach { childResource ->
                br { }
                buildResource(childResource)
            }
        }
    }
}

private fun instantiateParameterizedResources(schemaInstance: SchemaInstance) {
    for (resource in schemaInstance.schema.resources.values) {
        if (resource.parameters.isEmpty()) continue
        val ref = ResourceInstanceReference(resource.name, resource.parameters.map { "<${resource.name}.$it>" }, null)
        val instance = schemaInstance.instantiateResource(ref)
        instantiateParameterizedResources(schemaInstance, instance, ref)
    }
}

private fun instantiateParameterizedResources(
    schemaInstance: SchemaInstance,
    parentInstance: SchemaInstance.ResourceInstance,
    parentRef: ResourceInstanceReference,
) {
    for (resource in parentInstance.resourceSchema.resources.values) {
        if (resource.parameters.isEmpty()) continue
        val ref = ResourceInstanceReference(resource.name, resource.parameters.map { "<${resource.name}.$it>" }, parentRef)
        val instance = schemaInstance.instantiateResource(ref)
        instantiateParameterizedResources(schemaInstance, instance, ref)
    }
}

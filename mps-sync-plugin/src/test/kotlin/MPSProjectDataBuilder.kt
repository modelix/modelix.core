/*
 * Copyright (c) 2023.
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

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IProperty
import org.modelix.model.api.IRole
import org.modelix.model.data.NodeData
import java.util.UUID

fun buildMPSProjectData(useRoleIds: Boolean = true, body: MPSProjectDataBuilder.() -> Unit): NodeData {
    return MPSProjectDataBuilder(NodeDataBuilderContext(useRoleIds)).also(body).data
}

class NodeDataBuilderContext(val useRoleIds: Boolean) {
    fun roleKey(role: IRole) = if (useRoleIds) role.getUID() else role.getSimpleName()
}

open class NodeDataBuilder(protected val context: NodeDataBuilderContext, concept: IConcept) {
    var data = concept.newNodeData()

    private fun IRole.key() = context.roleKey(this)

    fun property(name: String, value: String?) {
        data = if (value == null) {
            data.copy(properties = data.properties - name)
        } else {
            data.copy(properties = data.properties + (name to value))
        }
    }

    fun role(role: IChildLink?) {
        role(role?.key())
    }

    fun role(role: String?) {
        data = data.copy(role = role)
    }

    fun property(property: IProperty, value: String?) {
        property(property.key(), value)
    }

    fun name(value: String?) {
        property(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, value)
    }

    fun addChild(childData: NodeData) {
        data = data.copy(children = data.children + childData)
    }

    fun child(role: IChildLink, subconcept: IConcept, body: NodeDataBuilder.() -> Unit) {
        addChild(NodeDataBuilder(context, subconcept).also(body).data)
    }

    fun child(role: IChildLink, body: NodeDataBuilder.() -> Unit) {
        child(role, role.targetConcept, body)
    }
}

class MPSProjectDataBuilder(context: NodeDataBuilderContext) :
    NodeDataBuilder(context, BuiltinLanguages.MPSRepositoryConcepts.Project) {

    fun module(body: MPSModuleDataBuilder.() -> Unit) {
        module(BuiltinLanguages.MPSRepositoryConcepts.Module, body)
    }

    fun module(type: IConcept, body: MPSModuleDataBuilder.() -> Unit) {
        addChild(
            MPSModuleDataBuilder(context, type).apply {
                role(BuiltinLanguages.MPSRepositoryConcepts.Project.modules)
                body()
            }.data,
        )
    }
}

class MPSModuleDataBuilder(context: NodeDataBuilderContext, subconcept: IConcept) : NodeDataBuilder(context, subconcept) {
    init {
        property(BuiltinLanguages.MPSRepositoryConcepts.Module.id, UUID.randomUUID().toString())
    }
}

private fun IConcept.newNodeData() = NodeData(concept = getUID())

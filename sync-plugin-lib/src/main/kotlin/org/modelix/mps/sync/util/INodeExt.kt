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

package org.modelix.mps.sync.util

import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PropertyFromName
import org.modelix.mps.sync.binding.ModelSynchronizer

// status: ready to test

val MPS_NODE_ID_PROPERTY_NAME: String = ModelSynchronizer.MPS_NODE_ID_PROPERTY_NAME

fun INode.mapToMpsNode(mpsNode: SNode) {
    val property = PropertyFromName(MPS_NODE_ID_PROPERTY_NAME)
    this.setPropertyValue(property, mpsNode.nodeId.toString())
}

fun INode.mappedMpsNodeID(): String? {
    return try {
        val property = PropertyFromName(MPS_NODE_ID_PROPERTY_NAME)
        this.getPropertyValue(property)
    } catch (e: RuntimeException) {
        throw RuntimeException(
            "Failed to retrieve the $MPS_NODE_ID_PROPERTY_NAME property in mappedMpsNodeID. The INode is $this , concept: ${this.concept}",
            e,
        )
    }
}

fun INode.isMappedToMpsNode() = this.mappedMpsNodeID() != null

fun INode.copyPropertyIfNecessary(original: INode, property: IProperty) {
    if (original.getPropertyValue(property) != this.getPropertyValue(property)) {
        this.copyProperty(original, property)
    }
}

fun INode.copyProperty(original: INode, property: IProperty) {
    try {
        this.setPropertyValue(property, original.getPropertyValue(property))
    } catch (ex: Exception) {
        throw RuntimeException("Unable to copy property ${property.name} from $original to $this", ex)
    }
}

fun INode.replicateChild(role: String, original: INode): INode {
    try {
        val equivalenceMap = mutableMapOf<INode, INode>()
        val postponedReferencesAssignments = mutableListOf<Triple<INode, String, INode>>()
        val result = this.replicateChildHelper(role, original, equivalenceMap, postponedReferencesAssignments)
        postponedReferencesAssignments.forEach { postponedRefAssignment ->
            var target = postponedRefAssignment.third
            if (equivalenceMap.containsKey(target)) {
                target = equivalenceMap[target]!!
            }
            postponedRefAssignment.first.setReferenceTarget(postponedRefAssignment.second, target)
        }
        return result
    } catch (ex: Exception) {
        throw RuntimeException("Unable to replicate child in role $role. Original: $original, This: $this", ex)
    }
}

fun INode.replicateChildHelper(
    role: String,
    original: INode,
    equivalenceMap: Map<INode, INode>,
    postponedReferencesAssignments: MutableList<Triple<INode, String, INode>>,
): INode {
    val concept = original.concept
    val copy: INode
    try {
        copy = this.addNewChild(role, -1, concept)
    } catch (ex: Exception) {
        throw RuntimeException("Unable to add child to $this with role $role and concept $concept", ex)
    }

    concept?.getAllProperties()?.forEach { property ->
        copy.setPropertyValue(property, original.getPropertyValue(property))
    }
    concept?.getAllChildLinks()?.forEach { childLink ->
        original.getChildren(childLink).forEach { child ->
            copy.replicateChildHelper(childLink.name, child, equivalenceMap, postponedReferencesAssignments)
        }
    }
    concept?.getAllReferenceLinks()?.forEach { refLink ->
        val target = original.getReferenceTarget(refLink)
        target?.let { postponedReferencesAssignments.add(Triple(copy, refLink.name, target)) }
    }
    return copy
}

fun INode.nodeIdAsLong(): Long = (this as PNodeAdapter).nodeId

fun INode.containingModule(): INode? {
    if (this.isModule()) {
        return this
    }
    val parent: INode = this.parent ?: return null
    return parent.containingModule()
}

fun INode.containingModel(): INode? {
    if (this.isModel()) {
        return this
    }
    val parent: INode = this.parent ?: return null
    return parent.containingModel()
}

fun INode.isModule(): Boolean {
    val concept = this.concept ?: return false
    // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
    // SConceptAdapter.wrap(concept/Module/)
    val parentConcept: IConcept? = null
    return concept.isSubConceptOf(parentConcept)
}

fun INode.isModel(): Boolean {
    val concept = this.concept ?: return false
    // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
    // SConceptAdapter.wrap(concept/Model/)
    val parentConcept: IConcept? = null
    return concept.isSubConceptOf(parentConcept)
}

fun INode.removeAllChildrenWithRole(role: String) {
    this.getChildren(role).forEach { child ->
        this.removeChild(child)
    }
}

fun INode.cloneChildren(original: INode, role: String) {
    this.removeAllChildrenWithRole(role)
    original.getChildren(role).forEach { originalChild ->
        this.replicateChild(role, originalChild)
    }
}

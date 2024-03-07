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

package org.modelix.model.mpsadapters

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IDeprecatedNodeDefaults
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink

interface IDefaultNodeAdapter : IDeprecatedNodeDefaults {
    override val allChildren: Iterable<INode>
        get() = emptyList()

    override val isValid: Boolean
        get() = true

    override fun tryGetConcept(): IConcept? = concept

    override fun getConceptReference(): IConceptReference? {
        return concept?.getReference()
    }

    override fun getOriginalReference(): String? {
        return reference.serialize()
    }

    override fun getPropertyLinks(): List<IProperty> {
        return concept?.getAllProperties() ?: emptyList()
    }

    override fun getReferenceLinks(): List<IReferenceLink> {
        return concept?.getAllReferenceLinks() ?: emptyList()
    }

    override fun getChildren(link: IChildLink): Iterable<INode> {
        throw UnsupportedOperationException("Concept $concept does not have children.")
    }

    override fun moveChild(role: IChildLink, index: Int, child: INode) {
        throw UnsupportedOperationException("Children in role $role of concept $concept cannot be moved.")
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode {
        throw UnsupportedOperationException("Children cannot be added to role $role in concept ${this.concept}.")
    }

    override fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode {
        throw UnsupportedOperationException("Children cannot be added to role $role in concept ${this.concept}.")
    }

    override fun getReferenceTarget(link: IReferenceLink): INode? {
        return null
    }

    override fun setReferenceTarget(link: IReferenceLink, target: INode?) {
        require(target == null) { "$concept doesn't contain a reference link $link" }
    }

    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) {
        require(target == null) { "$concept doesn't contain a reference link $role" }
    }

    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference? {
        return null
    }

    override fun getPropertyValue(property: IProperty): String? {
        return null
    }

    override fun setPropertyValue(property: IProperty, value: String?) {
        if (getPropertyValue(property) != value) {
            throw UnsupportedOperationException("Concept $concept does not contain property $property or it is read-only.")
        }
    }

    override fun removeChild(child: INode) {
        val link = child.getContainmentLink() ?: error("ContainmentLink not found for node $child")
        throw UnsupportedOperationException("Cannot remove child in link $link of concept $concept.")
    }
}

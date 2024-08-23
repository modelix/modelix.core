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

package org.modelix.model.api

interface IMutableNode {
    fun moveChild(role: IChildLinkReference, index: Int, child: INode)
    fun addNewChild(role: IChildLinkReference, index: Int, concept: IConcept): INode
    fun addNewChild(role: IChildLinkReference, index: Int, concept: ConceptReference): INode

    fun setReferenceTarget(link: IReferenceLinkReference, target: INode)
    fun setReferenceTargetRef(role: IReferenceLinkReference, target: INodeReference)
    fun removeReference(role: IReferenceLinkReference)

    fun setPropertyValue(property: IPropertyReference, value: String)
    fun removeProperty(property: IPropertyReference)
}

fun IMutableNode.setPropertyValue(property: IPropertyReference, value: String?) {
    if (value == null) {
        removeProperty(property)
    } else {
        setPropertyValue(property, value)
    }
}

fun IMutableNode.setReferenceTarget(link: IReferenceLinkReference, target: INode?) {
    if (target == null) {
        removeReference(link)
    } else {
        setReferenceTarget(link, target)
    }
}

fun IMutableNode.setReferenceTargetRef(link: IReferenceLinkReference, target: INodeReference?) {
    if (target == null) {
        removeReference(link)
    } else {
        setReferenceTargetRef(link, target)
    }
}

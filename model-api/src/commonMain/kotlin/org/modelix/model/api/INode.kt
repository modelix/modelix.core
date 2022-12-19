/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License. 
 */

package org.modelix.model.api

import org.modelix.model.area.IArea

interface INode {
    fun getArea(): IArea
    val isValid: Boolean
    val reference: INodeReference
    val concept: IConcept?
    val roleInParent: String?
    val parent: INode?
    fun getConceptReference(): IConceptReference?

    fun getChildren(role: String?): Iterable<INode>
    val allChildren: Iterable<INode>
    fun moveChild(role: String?, index: Int, child: INode)
    fun addNewChild(role: String?, index: Int, concept: IConcept?): INode
    fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode {
        return addNewChild(role, index, concept?.resolve())
    }
    fun removeChild(child: INode)

    fun getReferenceTarget(role: String): INode?
    fun getReferenceTargetRef(role: String): INodeReference? {
        return getReferenceTarget(role)?.reference
    }
    fun setReferenceTarget(role: String, target: INode?)
    fun setReferenceTarget(role: String, target: INodeReference?) {
        // Default implementation for backward compatibility only.
        setReferenceTarget(role, target?.resolveNode(getArea()))
    }

    fun getPropertyValue(role: String): String?
    fun setPropertyValue(role: String, value: String?)
    fun getPropertyRoles(): List<String>
    fun getReferenceRoles(): List<String>
}

private fun IRole.key(): String = name
fun INode.getChildren(link: IChildLink): Iterable<INode> = getChildren(link.key())
fun INode.moveChild(role: IChildLink, index: Int, child: INode): Unit = moveChild(role.key(), index, child)
fun INode.addNewChild(role: IChildLink, index: Int, concept: IConcept?) = addNewChild(role.key(), index, concept)
fun INode.getReferenceTarget(link: IReferenceLink): INode? = getReferenceTarget(link.key())
fun INode.setReferenceTarget(link: IReferenceLink, target: INode?): Unit = setReferenceTarget(link.key(), target)
fun INode.getPropertyValue(property: IProperty): String? = getPropertyValue(property.key())
fun INode.setPropertyValue(property: IProperty, value: String?): Unit = setPropertyValue(property.key(), value)
fun INode.getConcept(): IConcept? = getConceptReference()?.resolve()
fun INode.getResolvedReferenceTarget(role: String): INode? = getReferenceTargetRef(role)?.resolveNode(getArea())
fun INode.getResolvedConcept(): IConcept? = getConceptReference()?.resolve()

fun INode.addNewChild(role: String?, index: Int): INode = addNewChild(role, index, null as IConceptReference?)
fun INode.addNewChild(role: String?): INode = addNewChild(role, -1, null as IConceptReference?)
fun INode.addNewChild(role: String?, concept: IConceptReference?): INode = addNewChild(role, -1, concept)
fun INode.addNewChild(role: String?, concept: IConcept?): INode = addNewChild(role, -1, concept)

fun INode.remove() {
    parent?.removeChild(this)
}

fun INode.index(): Int {
    return (parent ?: return 0).getChildren(roleInParent).indexOf(this)
}

fun INode.getContainmentLink() = roleInParent?.let { parent?.concept?.getChildLink(it) }
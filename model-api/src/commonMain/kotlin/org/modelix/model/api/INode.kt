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

interface INodeEx : INode {
    fun usesRoleIds(): Boolean
    fun getContainmentLink(): IChildLink? = roleInParent?.let { role ->
        parent?.concept?.getAllChildLinks()?.find { (if (usesRoleIds()) it.getUID() else it.getSimpleName()) == role }
    }
    fun getChildren(link: IChildLink): Iterable<INode> = getChildren(link.key(this))
    fun moveChild(role: IChildLink, index: Int, child: INode) = moveChild(role.key(this), index, child)
    fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode = addNewChild(role.key(this), index, concept)
    fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode = addNewChild(role.key(this), index, concept)
    fun getReferenceTarget(link: IReferenceLink): INode? = getReferenceTarget(link.key(this))
    fun setReferenceTarget(link: IReferenceLink, target: INode?) = setReferenceTarget(link.key(this), target)
    fun getReferenceTargetRef(role: IReferenceLink): INodeReference? = getReferenceTargetRef(role.key(this))
    fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) = setReferenceTarget(role.key(this), target)
    fun getPropertyValue(property: IProperty): String? = getPropertyValue(property.key(this))
    fun setPropertyValue(property: IProperty, value: String?) = setPropertyValue(property.key(this), value)
}

@Deprecated("Use .key(INode), .key(IBranch), .key(ITransaction) or .key(ITree)")
fun IRole.key(): String = RoleAccessContext.getKey(this)
fun IRole.key(node: INode): String = if (node.usesRoleIds()) getUID() else getSimpleName()
fun INode.usesRoleIds(): Boolean = if (this is INodeEx) this.usesRoleIds() else false
fun INode.getChildren(link: IChildLink): Iterable<INode> = if (this is INodeEx) getChildren(link) else getChildren(link.key(this))
fun INode.moveChild(role: IChildLink, index: Int, child: INode): Unit = if (this is INodeEx) moveChild(role, index, child) else moveChild(role.key(this), index, child)
fun INode.addNewChild(role: IChildLink, index: Int, concept: IConcept?) = if (this is INodeEx) addNewChild(role, index, concept) else addNewChild(role.key(this), index, concept)
fun INode.getReferenceTarget(link: IReferenceLink): INode? = if (this is INodeEx) getReferenceTarget(link) else getReferenceTarget(link.key(this))
fun INode.getReferenceTargetRef(link: IReferenceLink): INodeReference? = if (this is INodeEx) getReferenceTargetRef(link) else getReferenceTargetRef(link.key(this))
fun INode.setReferenceTarget(link: IReferenceLink, target: INode?): Unit = if (this is INodeEx) setReferenceTarget(link, target) else setReferenceTarget(link.key(this), target)
fun INode.setReferenceTarget(link: IReferenceLink, target: INodeReference?): Unit = if (this is INodeEx) setReferenceTarget(link, target) else setReferenceTarget(link.key(this), target)
fun INode.getPropertyValue(property: IProperty): String? = if (this is INodeEx) getPropertyValue(property) else getPropertyValue(property.key(this))
fun INode.setPropertyValue(property: IProperty, value: String?): Unit = if (this is INodeEx) setPropertyValue(property, value) else setPropertyValue(property.key(this), value)

fun INode.getConcept(): IConcept? = getConceptReference()?.resolve()
fun INode.getResolvedReferenceTarget(role: String): INode? = getReferenceTargetRef(role)?.resolveNode(getArea())
fun INode.getResolvedConcept(): IConcept? = getConceptReference()?.resolve()

fun INode.addNewChild(role: String?, index: Int): INode = addNewChild(role, index, null as IConceptReference?)
fun INode.addNewChild(role: String?): INode = addNewChild(role, -1, null as IConceptReference?)
fun INode.addNewChild(role: String?, concept: IConceptReference?): INode = addNewChild(role, -1, concept)
fun INode.addNewChild(role: String?, concept: IConcept?): INode = addNewChild(role, -1, concept)

fun INode.resolveChildLink(role: String): IChildLink {
    val c = this.concept ?: throw RuntimeException("Node has no concept")
    return c.getAllChildLinks().find { it.key(this) == role }
        ?: throw RuntimeException("Child link '$role' not found in concept ${c.getLongName()}")
}
fun INode.resolveReferenceLink(role: String): IReferenceLink {
    val c = this.concept ?: throw RuntimeException("Node has no concept")
    return c.getAllReferenceLinks().find { it.key(this) == role }
        ?: throw RuntimeException("Reference link '$role' not found in concept ${c.getLongName()}")
}
fun INode.resolveProperty(role: String): IProperty {
    val c = this.concept ?: throw RuntimeException("Node has no concept")
    return c.getAllProperties().find { it.key(this) == role }
        ?: throw RuntimeException("Property '$role' not found in concept ${c.getLongName()}")
}

fun INode.remove() {
    parent?.removeChild(this)
}

fun INode.index(): Int {
    return (parent ?: return 0).getChildren(roleInParent).indexOf(this)
}

fun INode.getContainmentLink() = if (this is INodeEx) {
    getContainmentLink()
} else {
    roleInParent?.let { role ->
        parent?.concept?.getAllChildLinks()?.find { (if (usesRoleIds()) it.getUID() else it.getSimpleName()) == role }
    }
}
fun INode.getRoot(): INode = parent?.getRoot() ?: this
fun INode.isInstanceOf(superConcept: IConcept?): Boolean = concept.let { it != null && it.isSubConceptOf(superConcept) }
fun INode.isInstanceOfSafe(superConcept: IConcept): Boolean = tryGetConcept()?.isSubConceptOf(superConcept) ?: false
fun INode.tryGetConcept(): IConcept? = getConceptReference()?.tryResolve()

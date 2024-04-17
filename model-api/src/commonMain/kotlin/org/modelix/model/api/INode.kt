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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import org.modelix.model.area.IArea
import org.modelix.model.data.NodeData

/**
 * Representation of a model element.
 */
interface INode {

    /**
     * Returns the area of which this node is part of.
     */
    fun getArea(): IArea
    val isValid: Boolean

    /**
     * Reference targeting this node.
     */
    val reference: INodeReference

    /**
     * Concept, of which this node is instance of, or null if the node is not instance of any concept.
     */
    val concept: IConcept?

    fun tryGetConcept(): IConcept? = getConceptReference()?.tryResolve()

    /**
     * Role of this node in its parent node if it exists,or null otherwise.
     */
    @Deprecated("use getContainmentLink()")
    val roleInParent: String?

    /**
     * Parent node of this node if it exists, or null if this node has no parent node.
     */
    val parent: INode?

    /**
     * Returns a concept reference to the concept of which this node is instance of.
     *
     * @return concept reference or null if this node is not instance of any concept.
     */
    fun getConceptReference(): IConceptReference?

    /**
     * Returns children of this node for the given role.
     *
     * @param role the desired role
     * @return iterable over the child nodes
     */
    @Deprecated("use IChildLink instead of String")
    fun getChildren(role: String?): Iterable<INode>

    /**
     * Iterable over all child nodes of this node.
     */
    val allChildren: Iterable<INode>

    /**
     * Moves a node to this node's children with the given role and index.
     * The child node can originate from a different parent.
     *
     * @param role target role
     * @param index target index within the role
     * @param child child node to be moved
     */
    @Deprecated("use IChildLink instead of String")
    fun moveChild(role: String?, index: Int, child: INode)

    /**
     * Adds a new child node to this node.
     *
     * Creates and adds a new child node to this node at the specified index.
     *
     * @param role role, where the node should be added
     * @param index index, where the node should be added
     * @param concept concept, of which the new node is instance of
     * @return new child node
     *
     * @see addNewChild
     */
    @Deprecated("use IChildLink instead of String")
    fun addNewChild(role: String?, index: Int, concept: IConcept?): INode

    /**
     * Adds a new child node to this node.
     *
     * Creates and adds a new child node to this node at the specified index.
     *
     * @param role role, where the node should be added
     * @param index index within the role, where the node should be added
     * @param concept reference to a concept, of which the new node is instance of
     * @return new child node
     */
    @Deprecated("use IChildLink instead of String")
    fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode {
        return addNewChild(role, index, concept?.resolve())
    }

    fun addNewChildren(role: String?, index: Int, concepts: List<IConceptReference?>): List<INode> {
        return concepts.mapIndexed { i, it -> addNewChild(role, if (index >= 0) index + i else index, it) }
    }

    fun addNewChildren(link: IChildLink, index: Int, concepts: List<IConceptReference?>): List<INode> {
        return concepts.mapIndexed { i, it -> addNewChild(link, if (index >= 0) index + i else index, it) }
    }

    /**
     * Removes the given node from this node's children.
     *
     * @param child node to be removed
     */
    fun removeChild(child: INode)

    /**
     * Returns the target of the given reference role for this node.
     *
     * @param role the desired reference role
     * @return target node, or null if the target could not be found
     */
    @Deprecated("use IReferenceLink instead of String")
    fun getReferenceTarget(role: String): INode?

    /**
     * Returns a node reference to the target of the reference.
     *
     * @param role the desired reference role
     * @return node reference to the target, or null if the target could not be found
     */
    @Deprecated("use IReferenceLink instead of String")
    fun getReferenceTargetRef(role: String): INodeReference? {
        return getReferenceTarget(role)?.reference
    }

    /**
     * Sets the target of the given role reference for this node to the specified target node.
     *
     * @param role the desired reference role
     * @param target new target for this node's reference
     */
    @Deprecated("use IReferenceLink instead of String")
    fun setReferenceTarget(role: String, target: INode?)

    /**
     * Sets the target of the given role reference for this node
     * to the node referenced by the specified target reference.
     *
     * @param role the desired reference role
     * @param target reference to the new target for this node's reference
     */
    @Deprecated("use IReferenceLink instead of String")
    fun setReferenceTarget(role: String, target: INodeReference?) {
        // Default implementation for backward compatibility only.
        setReferenceTarget(role, target?.resolveIn(getArea()!!))
    }

    /**
     * Returns the value of the given property role for this node.
     *
     * @param role the desired property role
     * @return property value, or null if there is no value
     */
    @Deprecated("use getPropertyValue(IProperty)")
    fun getPropertyValue(role: String): String?

    /**
     * Sets the value of the given property role for this node to the specified role.
     *
     * @param role the desired property role
     * @param value the new property value
     */
    @Deprecated("use setPropertyValue(IProperty, String?)")
    fun setPropertyValue(role: String, value: String?)

    /**
     * Returns all property roles of this node.
     *
     * @return list of all property roles
     */
    @Deprecated("use getPropertyLinks()")
    fun getPropertyRoles(): List<String>

    /**
     * Returns all reference roles of this node.
     *
     * @return list of all reference roles
     */
    @Deprecated("use getReferenceLinks()")
    fun getReferenceRoles(): List<String>

    /**
     * @return the serialized reference of the source node, if this one was created during an import
     */
    fun getOriginalReference(): String? = getPropertyValue(IProperty.fromName(NodeData.ID_PROPERTY_KEY))
        ?: getPropertyValue(IProperty.fromName("#mpsNodeID#")) // for backwards compatibility

    // <editor-fold desc="non-string based API">
    fun usesRoleIds(): Boolean = false
    fun getContainmentLink(): IChildLink? = roleInParent?.let { role ->
        parent?.concept?.getAllChildLinks()?.find { (if (usesRoleIds()) it.getUID() else it.getSimpleName()) == role }
    }
    fun getChildren(link: IChildLink): Iterable<INode> = getChildren(link.key(this))
    fun moveChild(role: IChildLink, index: Int, child: INode) = moveChild(role.key(this), index, child)
    fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode = addNewChild(role.key(this), index, concept)
    fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode = addNewChild(role.key(this), index, concept)
    fun getReferenceTarget(link: IReferenceLink): INode? = getReferenceTarget(link.key(this))
    fun setReferenceTarget(link: IReferenceLink, target: INode?) = setReferenceTarget(link.key(this), target)
    fun setReferenceTarget(role: IReferenceLink, target: INodeReference?) = setReferenceTarget(role.key(this), target)
    fun removeReference(role: IReferenceLink) = setReferenceTarget(role, null as INodeReference?)
    fun getReferenceTargetRef(role: IReferenceLink): INodeReference? = getReferenceTargetRef(role.key(this))
    fun getPropertyValue(property: IProperty): String? = getPropertyValue(property.key(this))
    fun setPropertyValue(property: IProperty, value: String?) = setPropertyValue(property.key(this), value)

    fun getReferenceLinks(): List<IReferenceLink> = getReferenceRoles().map { tryResolveReferenceLink(it) ?: ReferenceLinkFromName(it) }
    fun getPropertyLinks(): List<IProperty> = getPropertyRoles().map { tryResolveProperty(it) ?: PropertyFromName(it) }
    fun getAllProperties(): List<Pair<IProperty, String>> = getPropertyLinks().map { it to getPropertyValue(it) }.filterSecondNotNull()
    fun getAllReferenceTargets(): List<Pair<IReferenceLink, INode>> = getReferenceLinks().map { it to getReferenceTarget(it) }.filterSecondNotNull()
    fun getAllReferenceTargetRefs(): List<Pair<IReferenceLink, INodeReference>> = getReferenceLinks().map { it to getReferenceTargetRef(it) }.filterSecondNotNull()
    // </editor-fold>

    // <editor-fold desc="flow API">
    fun getParentAsFlow(): Flow<INode> = flowOf(parent).filterNotNull()
    fun getPropertyValueAsFlow(role: IProperty): Flow<String?> = flowOf(getPropertyValue(role))
    fun getAllChildrenAsFlow(): Flow<INode> = allChildren.asFlow()
    fun getAllReferenceTargetsAsFlow(): Flow<Pair<IReferenceLink, INode>> = getAllReferenceTargets().asFlow()
    fun getAllReferenceTargetRefsAsFlow(): Flow<Pair<IReferenceLink, INodeReference>> = getAllReferenceTargetRefs().asFlow()
    fun getChildrenAsFlow(role: IChildLink): Flow<INode> = getChildren(role).asFlow()
    fun getReferenceTargetAsFlow(role: IReferenceLink): Flow<INode> = flowOf(getReferenceTarget(role)).filterNotNull()
    fun getReferenceTargetRefAsFlow(role: IReferenceLink): Flow<INodeReference> = flowOf(getReferenceTargetRef(role)).filterNotNull()

    fun getDescendantsAsFlow(includeSelf: Boolean = false): Flow<INode> {
        return if (includeSelf) {
            flowOf(flowOf(this), getDescendantsAsFlow(false)).flattenConcat()
        } else {
            getAllChildrenAsFlow().flatMapConcat { it.getDescendantsAsFlow(true) }
        }
    }
    // </editor-fold>
}

fun <T1, T2> List<Pair<T1, T2?>>.filterSecondNotNull(): List<Pair<T1, T2>> = filter { it.second != null } as List<Pair<T1, T2>>

@Deprecated("all members moved to INode", ReplaceWith("INode"))
interface INodeEx : INode

interface IDeprecatedNodeDefaults : INode {
    override val roleInParent: String? get() = getContainmentLink()?.getUID()
    override fun getChildren(role: String?): Iterable<INode> = getChildren(resolveChildLinkOrFallback(role))
    override fun moveChild(role: String?, index: Int, child: INode) = moveChild(resolveChildLinkOrFallback(role), index, child)
    override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode = addNewChild(resolveChildLinkOrFallback(role), index, concept)
    override fun addNewChild(role: String?, index: Int, concept: IConceptReference?): INode = addNewChild(resolveChildLinkOrFallback(role), index, concept)
    override fun getReferenceTarget(role: String): INode? = getReferenceTarget(resolveReferenceLinkOrFallback(role))
    override fun getReferenceTargetRef(role: String): INodeReference? = getReferenceTargetRef(resolveReferenceLinkOrFallback(role))
    override fun setReferenceTarget(role: String, target: INode?) = setReferenceTarget(resolveReferenceLinkOrFallback(role), target)
    override fun setReferenceTarget(role: String, target: INodeReference?) = setReferenceTarget(resolveReferenceLinkOrFallback(role), target)
    override fun getPropertyValue(role: String): String? = getPropertyValue(resolvePropertyOrFallback(role))
    override fun setPropertyValue(role: String, value: String?) = setPropertyValue(resolvePropertyOrFallback(role), value)
    override fun getPropertyRoles(): List<String> = getPropertyLinks().map { it.key(this) }
    override fun getReferenceRoles(): List<String> = getReferenceLinks().map { it.key(this) }

    override fun usesRoleIds(): Boolean = true
    override fun getContainmentLink(): IChildLink?
    override fun getChildren(link: IChildLink): Iterable<INode>
    override fun moveChild(role: IChildLink, index: Int, child: INode)
    override fun addNewChild(role: IChildLink, index: Int, concept: IConcept?): INode
    override fun addNewChild(role: IChildLink, index: Int, concept: IConceptReference?): INode
    override fun getReferenceTarget(link: IReferenceLink): INode?
    override fun setReferenceTarget(link: IReferenceLink, target: INode?)
    override fun setReferenceTarget(role: IReferenceLink, target: INodeReference?)
    override fun getReferenceTargetRef(role: IReferenceLink): INodeReference?
    override fun getPropertyValue(property: IProperty): String?
    override fun setPropertyValue(property: IProperty, value: String?)
    override fun getPropertyLinks(): List<IProperty>
    override fun getReferenceLinks(): List<IReferenceLink>
}

/**
 *  Interface for nodes that can be replaced by a new instance.
 */
interface IReplaceableNode : INode {
    /**
     * Replaces this node with a new node of the given concept that uses the same id as this node.
     * Properties, references and children will be the same.
     *
     * @param concept the concept of the new node
     * @return replacement for this node with the new given concept
     */
    fun replaceNode(concept: ConceptReference?): INode
}

@Deprecated("Use .key(INode), .key(IBranch), .key(ITransaction) or .key(ITree)")
fun IRole.key(): String = RoleAccessContext.getKey(this)
fun IRole.key(node: INode): String = if (node.usesRoleIds()) getUID() else getSimpleName()
fun IChildLink.key(node: INode): String? = when (this) {
    is NullChildLink -> null
    else -> (this as IRole).key(node)
}
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

@Deprecated("use INode.concept", ReplaceWith("concept"))
fun INode.getConcept(): IConcept? = getConceptReference()?.resolve()
fun INode.getResolvedReferenceTarget(role: String): INode? = getReferenceTargetRef(role)?.resolveIn(getArea()!!)
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

/**
 * Attempts to resolve the child link.
 * @return resolved child link
 *         or null, if this concept has no child link or an exception was thrown during concept resolution
 */
fun INode.tryResolveChildLink(role: String): IChildLink? {
    val c = this.tryGetConcept() ?: return null
    val allLinks = c.getAllChildLinks()
    return allLinks.find { it.key(this) == role }
        ?: allLinks.find { it.getSimpleName() == role }
        ?: allLinks.find { it.getUID() == role }
}
fun INode.resolveChildLinkOrFallback(role: String?): IChildLink {
    if (role == null) return NullChildLink
    return tryResolveChildLink(role) ?: IChildLink.fromName(role)
}

/**
 * Attempts to resolve the reference link.
 * @return resolved reference link
 *         or null, if this node has no reference link or an exception was thrown during concept resolution
 */
fun INode.tryResolveReferenceLink(role: String): IReferenceLink? {
    val c = this.tryGetConcept() ?: return null
    val allLinks = c.getAllReferenceLinks()
    return allLinks.find { it.key(this) == role }
        ?: allLinks.find { it.getSimpleName() == role }
        ?: allLinks.find { it.getUID() == role }
}
fun INode.resolveReferenceLinkOrFallback(role: String): IReferenceLink {
    return tryResolveReferenceLink(role) ?: IReferenceLink.fromName(role)
}

/**
 * Attempts to resolve the property.
 * @return resolved property
 *         or null, if this node has no concept or an exception was thrown during concept resolution
 */
fun INode.tryResolveProperty(role: String): IProperty? {
    val c = this.tryGetConcept() ?: return null
    val allLinks = c.getAllProperties()
    return allLinks.find { it.key(this) == role }
        ?: allLinks.find { it.getSimpleName() == role }
        ?: allLinks.find { it.getUID() == role }
}

/**
 * Resolves whether the child link is ordered or not.
 *
 * Assume children to be ordered by default.
 * Unordered children are the special case that can be declared by setting [[IChildLink.isOrdered]] to `false`.
 */
fun INode.isChildRoleOrdered(role: String?): Boolean {
    return if (role == null) {
        true
    } else {
        this.tryResolveChildLink(role)?.isOrdered ?: true
    }
}

fun INode.resolvePropertyOrFallback(role: String): IProperty {
    return tryResolveProperty(role) ?: IProperty.fromName(role)
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

fun INode.addNewChild(role: IChildLink, index: Int) = addNewChild(role, index, null as IConceptReference?)

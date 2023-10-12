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

import jetbrains.mps.util.IterableUtil
import jetbrains.mps.util.containers.EmptyIterable
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.ResolveInfo
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.model.SReference
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.getAncestor

class NodeAsMPSNode(private val node: INode, private val repository: SRepository?) : SNode {

    companion object {

        fun wrap(nodeToWrap: INode?) = wrap(nodeToWrap, null)

        fun wrap(nodeToWrap: INode?, repository: SRepository?) =
            when (nodeToWrap) {
                null -> {
                    null
                }

                is MPSNode -> {
                    nodeToWrap.node
                }

                else -> {
                    NodeAsMPSNode(nodeToWrap, repository)
                }
            }
    }

    var modelMode: EModelMode = EModelMode.NULL
    val wrapped: INode = node

    private val logger = mu.KotlinLogging.logger {}
    private var nodeId: NodeId = NodeId()
    private var nodeReference: NodeReference = NodeReference()
    private var userObjects: MutableMap<Any, Any?> = mutableMapOf()

    init {
        if (node.concept == null) {
            throw RuntimeException("Node has no concept: $node")
        }
    }

    private fun wrap(nodeToWrap: INode?): SNode? {
        val wrapped = wrap(nodeToWrap, repository)
        if (wrapped is NodeAsMPSNode) {
            wrapped.modelMode = modelMode
        }
        return wrapped
    }

    override fun getModel() =
        when (modelMode) {
            EModelMode.NULL -> null
            EModelMode.ADAPTER -> {
                val modelNode =
                    this.node.getAncestor(BuiltinLanguages.MPSRepositoryConcepts.Model, false)
                if (modelNode == null) {
                    null
                } else {
                    NodeAsMPSModel.wrap(modelNode, repository)
                }
            }
        }

    override fun getNodeId() = nodeId

    override fun getReference() = nodeReference

    override fun getConcept(): SConcept {
        val concept = node.concept
        if (concept is MPSConcept && concept.concept is SConcept) {
            return concept.concept
        }
        val unwrapped = MPSConcept.unwrap(concept)
        if (unwrapped is SConcept) {
            return unwrapped
        }
        return MPSConcept.unwrap(BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept) as SConcept
    }

    override fun isInstanceOfConcept(concept: SAbstractConcept) = this.concept.isSubConceptOf(concept)

    override fun getPresentation(): String {
        var result: String? = null
        try {
            val sINamedConcept =
                MPSConcept.unwrap(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept) as SAbstractConcept
            if (this.isInstanceOfConcept(sINamedConcept)) {
                result = getProperty(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getSimpleName())
            }
        } catch (ex: Exception) {
            logger.debug(ex.message, ex)
        }
        if (result == null) {
            result = toString()
        }
        return result
    }

    override fun getName() = node.getPropertyValue("name")

    override fun addChild(role: SContainmentLink, child: SNode) =
        node.moveChild(MPSChildLink(role), -1, MPSNode.wrap(child)!!)

    override fun insertChildBefore(role: SContainmentLink, child: SNode, anchor: SNode?) {
        if (anchor == null) {
            addChild(role, child)
            return
        }
        val children = getChildren(role)
        val index = children.indexOf(anchor)
        node.moveChild(MPSChildLink(role), index, MPSNode.wrap(child)!!)
    }

    override fun insertChildAfter(role: SContainmentLink, child: SNode, anchor: SNode?) {
        if (anchor == null) {
            addChild(role, child)
            return
        }
        val children = getChildren(role)
        val index = children.indexOf(anchor)
        node.moveChild(MPSChildLink(role), index + 1, MPSNode.wrap(child)!!)
    }

    override fun removeChild(child: SNode) = this.node.removeChild(MPSNode.wrap(child)!!)

    override fun delete() {
        node.parent?.removeChild(node)
    }

    override fun getParent(): SNode? {
        val parent = node.parent ?: return null
        if (parent.concept == null) {
            return null
        }
        return wrap(parent)
    }

    override fun getContainingRoot(): SNode {
        var n1: INode?
        var n2: INode? = node
        do {
            n1 = n2
            n2 = n1!!.parent
        } while (n2 != null)
        return wrap(n1)!!
    }

    override fun getContainmentLink() = (this.node.getContainmentLink() as? MPSChildLink)?.link

    override fun getFirstChild(): SNode? {
        val first = node.allChildren.first()
        return wrap(first)
    }

    override fun getLastChild(): SNode? {
        val last = node.allChildren.last()
        return wrap(last)
    }

    override fun getPrevSibling(): SNode? {
        val parent = node.parent ?: return null
        var sibling1: INode?
        var sibling2: INode? = null
        parent.allChildren.forEach { sibling ->
            sibling1 = sibling2
            sibling2 = sibling
            if (node == sibling2) {
                return wrap(sibling1)
            }
        }
        return null
    }

    override fun getNextSibling(): SNode? {
        val parent = node.parent ?: return null
        var sibling1: INode?
        var sibling2: INode? = null
        parent.allChildren.forEach { sibling ->
            sibling1 = sibling2
            sibling2 = sibling
            if (node == sibling1) {
                return wrap(sibling2)
            }
        }
        return null
    }

    override fun getChildren(role: SContainmentLink) = this.node.getChildren(MPSChildLink(role)).map { wrap(it) }

    override fun getChildren() = this.node.allChildren.map { wrap(it) }

    override fun setReferenceTarget(role: SReferenceLink, target: SNode?) =
        this.node.setReferenceTarget(MPSReferenceLink(role), MPSNode.wrap(target))

    override fun getReferenceTarget(role: SReferenceLink) = wrap(this.node.getReferenceTarget(MPSReferenceLink(role)))

    override fun getReference(role: SReferenceLink) = Reference(role)

    override fun setReference(role: SReferenceLink, resolveInfo: ResolveInfo?) =
        throw UnsupportedOperationException("Not implemented")

    override fun setReference(role: SReferenceLink, reference: SReference?) =
        throw UnsupportedOperationException("Not implemented")

    override fun setReference(role: SReferenceLink, target: SNodeReference) =
        throw UnsupportedOperationException("Not implemented")

    override fun dropReference(role: SReferenceLink) = throw UnsupportedOperationException("Not implemented")

    override fun getReferences() =
        MPSConcept.unwrap(node.concept)?.referenceLinks?.filter { getReferenceTarget(it) != null }
            ?.map { Reference(it) } ?: EmptyIterable()

    override fun getProperties() =
        MPSConcept.unwrap(node.concept)?.properties?.filter { getProperty(it) != null } ?: EmptyIterable()

    override fun hasProperty(property: SProperty) = getProperty(property) != null

    override fun getProperty(property: SProperty): String? = this.node.getPropertyValue(MPSProperty(property))

    override fun setProperty(property: SProperty, propertyValue: String?) =
        this.node.setPropertyValue(MPSProperty(property), propertyValue)

    override fun getUserObject(key: Any) = userObjects[key]

    override fun putUserObject(key: Any, value: Any?) {
        userObjects[key] = value
    }

    override fun getUserObjectKeys() = userObjects.keys

    @Deprecated("Deprecated in Java")
    override fun getRoleInParent() = containmentLink?.name

    @Deprecated("Deprecated in Java")
    override fun hasProperty(propertyName: String) = getProperty(propertyName) != null

    @Deprecated("Deprecated in Java")
    override fun getProperty(propertyName: String): String? = getProperty(findProperty(propertyName))

    @Deprecated("Deprecated in Java")
    override fun setProperty(propertyName: String, propertyValue: String?) =
        setProperty(findProperty(propertyName), propertyValue)

    @Deprecated("Deprecated in Java")
    override fun getPropertyNames() = properties.map { it.name }

    @Deprecated("Deprecated in Java")
    override fun setReferenceTarget(role: String?, target: SNode?) = setReferenceTarget(findReferenceLink(role), target)

    @Deprecated("Deprecated in Java")
    override fun getReferenceTarget(role: String?) = getReferenceTarget(findReferenceLink(role))

    private fun findReferenceLink(name: String?) = concept.referenceLinks.firstOrNull { it.name == name }
        ?: throw RuntimeException("${concept.name} doesn't have a reference link '$name'")

    private fun findChildLink(name: String?) = concept.containmentLinks.firstOrNull { it.name == name }
        ?: throw RuntimeException("${concept.name} doesn't have a reference link '$name'")

    private fun findProperty(name: String): SProperty {
        val properties = getConcept().properties
        return properties.firstOrNull { it.name == name }
            ?: throw RuntimeException("${getConcept().name} doesn't have a property '$name'")
    }

    @Deprecated("Deprecated in Java")
    override fun getReference(role: String?) = getReference(findReferenceLink(role))

    @Deprecated("Deprecated in Java")
    override fun setReference(role: String?, reference: SReference?) =
        setReference(findReferenceLink(role), reference)

    @Deprecated("Deprecated in Java")
    override fun insertChildBefore(role: String?, child: SNode, anchor: SNode?) {
        val link = findChildLink(role)
        val children = IterableUtil.asIterable(node.getChildren(role).iterator())
        var index = -1
        if (anchor != null) {
            index = children.indexOf(MPSNode.wrap(anchor))
            if (index == -1) {
                throw RuntimeException("$anchor is not a child of $node")
            }
        }
        node.addNewChild(MPSChildLink(link), index, MPSConcept.wrap(child.concept))
    }

    @Deprecated("Deprecated in Java")
    override fun addChild(role: String?, child: SNode) = addChild(findChildLink(role), child)

    @Deprecated("Deprecated in Java")
    override fun getChildren(role: String?) = getChildren(findChildLink(role))

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || other !is NodeAsMPSNode) {
            return false
        }
        return node == other.node
    }

    override fun hashCode() = 31 + node.hashCode()

    override fun toString() = "NodeToSNodeAdapter[$node]"

    inner class NodeReference : SNodeReference {
        override fun resolve(repository: SRepository) = this@NodeAsMPSNode

        override fun getModelReference() = null

        override fun getNodeId() = this@NodeAsMPSNode.nodeId

        override fun toString() = ":${node.reference}"

        override fun hashCode() = this@NodeAsMPSNode.hashCode()

        override fun equals(other: Any?): Boolean =
            if (other is NodeReference) {
                getNode() == other.getNode()
            } else {
                false
            }

        fun getNode(): NodeAsMPSNode = this@NodeAsMPSNode
    }

    inner class NodeId : SNodeId {
        override fun getType() = "shadowmodelsAdapter"

        override fun toString() = ":${node.reference}"
    }

    inner class Reference(private val link: SReferenceLink) : SReference {

        @Deprecated("")
        override fun getRole(): String = link.name

        override fun getLink(): SReferenceLink = link

        override fun getSourceNode() = this@NodeAsMPSNode

        override fun getTargetNode() = getReferenceTarget(link)

        override fun getTargetNodeReference(): SNodeReference? = getTargetNode()?.reference

        override fun getTargetSModelReference(): SModelReference? = getTargetNode()?.model?.reference

        override fun getTargetNodeId(): SNodeId? = getTargetNode()?.nodeId
    }
}

enum class EModelMode {
    ADAPTER,
    NULL,
}

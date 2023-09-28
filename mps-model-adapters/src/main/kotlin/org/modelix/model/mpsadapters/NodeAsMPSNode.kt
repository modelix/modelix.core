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

import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.ResolveInfo
import org.jetbrains.mps.openapi.model.SModel
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
    private var nodeId: NodeId = NodeId()
    private var nodeReference: NodeReference = NodeReference()

    init {
        if (node.concept == null) {
            throw RuntimeException("Node has no concept: $node")
        }
    }

    fun getWrapped() = node

    override fun getModel(): SModel? {
        return when (modelMode) {
            EModelMode.NULL -> null
            EModelMode.ADAPTER -> {
                val modelNode =
                    this.node.getAncestor(BuiltinLanguages.MPSRepositoryConcepts.Model, false) ?: return null
                return NodeAsMPSModel.wrap(modelNode, repository)
            }
        }
    }

    override fun getNodeId() = nodeId

    override fun getReference() = nodeReference

    override fun getReference(role: SReferenceLink) = Reference(role)

    @Deprecated("Deprecated in Java")
    override fun getReference(role: String?) = getReference(findReferenceLink(role))
    private fun findReferenceLink(role: String?) = concept.referenceLinks.firstOrNull { it.name == name }
        ?: throw RuntimeException("${concept.name} doesn't have a reference link '$name'")

    override fun getConcept(): SConcept {
        TODO("Not yet implemented")
    }

    override fun isInstanceOfConcept(c: SAbstractConcept): Boolean {
        TODO("Not yet implemented")
    }

    override fun getPresentation(): String {
        TODO("Not yet implemented")
    }

    override fun getName(): String? {
        TODO("Not yet implemented")
    }

    override fun addChild(role: SContainmentLink, child: SNode) {
        TODO("Not yet implemented")
    }

    override fun addChild(role: String?, child: SNode?) {
        TODO("Not yet implemented")
    }

    override fun insertChildBefore(role: SContainmentLink, child: SNode, anchor: SNode?) {
        TODO("Not yet implemented")
    }

    override fun insertChildBefore(role: String?, child: SNode?, anchor: SNode?) {
        TODO("Not yet implemented")
    }

    override fun insertChildAfter(role: SContainmentLink, child: SNode, anchor: SNode?) {
        TODO("Not yet implemented")
    }

    override fun removeChild(child: SNode) {
        TODO("Not yet implemented")
    }

    override fun delete() {
        TODO("Not yet implemented")
    }

    override fun getParent(): SNode? {
        TODO("Not yet implemented")
    }

    override fun getContainingRoot(): SNode {
        TODO("Not yet implemented")
    }

    override fun getContainmentLink(): SContainmentLink? {
        TODO("Not yet implemented")
    }

    override fun getFirstChild(): SNode? {
        TODO("Not yet implemented")
    }

    override fun getLastChild(): SNode? {
        TODO("Not yet implemented")
    }

    override fun getPrevSibling(): SNode? {
        TODO("Not yet implemented")
    }

    override fun getNextSibling(): SNode? {
        TODO("Not yet implemented")
    }

    override fun getChildren(role: SContainmentLink?): MutableIterable<SNode> {
        TODO("Not yet implemented")
    }

    override fun getChildren(): MutableIterable<SNode> {
        TODO("Not yet implemented")
    }

    override fun getChildren(role: String?): MutableIterable<SNode> {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(role: SReferenceLink, target: SNode?) {
        TODO("Not yet implemented")
    }

    override fun setReferenceTarget(role: String?, target: SNode?) {
        TODO("Not yet implemented")
    }

    override fun setReference(role: SReferenceLink, resolveInfo: ResolveInfo?) {
        TODO("Not yet implemented")
    }

    override fun setReference(role: SReferenceLink, target: SNodeReference) {
        TODO("Not yet implemented")
    }

    override fun setReference(role: SReferenceLink, reference: SReference?) {
        TODO("Not yet implemented")
    }

    override fun setReference(role: String?, reference: SReference?) {
        TODO("Not yet implemented")
    }

    override fun getReferenceTarget(role: SReferenceLink): SNode? {
        TODO("Not yet implemented")
    }

    override fun getReferenceTarget(role: String?): SNode {
        TODO("Not yet implemented")
    }

    override fun dropReference(role: SReferenceLink) {
        TODO("Not yet implemented")
    }

    override fun getReferences(): MutableIterable<SReference> {
        TODO("Not yet implemented")
    }

    override fun getProperties(): MutableIterable<SProperty> {
        TODO("Not yet implemented")
    }

    override fun hasProperty(property: SProperty): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasProperty(propertyName: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getProperty(property: SProperty): String? {
        TODO("Not yet implemented")
    }

    override fun getProperty(propertyName: String?): String {
        TODO("Not yet implemented")
    }

    override fun setProperty(property: SProperty, propertyValue: String?) {
        TODO("Not yet implemented")
    }

    override fun setProperty(propertyName: String?, propertyValue: String?) {
        TODO("Not yet implemented")
    }

    override fun getUserObject(key: Any?): Any {
        TODO("Not yet implemented")
    }

    override fun putUserObject(key: Any?, value: Any?) {
        TODO("Not yet implemented")
    }

    override fun getUserObjectKeys(): MutableIterable<Any> {
        TODO("Not yet implemented")
    }

    override fun getRoleInParent(): String {
        TODO("Not yet implemented")
    }

    override fun getPropertyNames(): MutableIterable<String> {
        TODO("Not yet implemented")
    }

    inner class NodeId : SNodeId {
        override fun getType() = "shadowmodelsAdapter"

        override fun toString() = ":${node.reference}"
    }

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

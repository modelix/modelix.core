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

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.deepUnwrap

@JsExport
object JSNodeConverter {
    fun isSameNode(node1: Any, node2: Any): Boolean {
        return toINode(node1).deepUnwrap() == toINode(node2).deepUnwrap()
    }

    fun nodeToJs(node: INode?): Any? {
        if (node == null) return node
        // return type is Any because the import for INodeJS is missing in the generated .d.ts
        return NodeAdapterJS(node)
    }

    fun nodeFromJs(node: Any?): Any? {
        if (node == null) return node
        return (node as NodeAdapterJS).node
    }

    fun unwrapNode(node: Any?): Any? {
        if (node == null) return node
        return if (node is NodeAdapterJS) node.node else node
    }

    fun isJsNode(node: Any?): Boolean {
        return node is NodeAdapterJS
    }

    fun toINode(node: Any): INode {
        if (node is INode) return node
        if (node is NodeAdapterJS) return node.node
        if (node is INodeJS) return node.unwrap()

        // handle ReactiveINodeJS in vue-model-api
        if (node.asDynamic().unwrap != null) return node.asDynamic().unwrap()

        throw IllegalArgumentException("Unsupported node type: $node")
    }
}

@JsExport
class NodeAdapterJS(val node: INode) : INodeJS {
    init {
        // This is called from JS, so this check is not redundant.
        @Suppress("USELESS_IS_CHECK")
        require(node is INode) { "Not an INode: $node" }
    }

    override fun unwrap(): INode {
        return node
    }

    override fun getConcept(): IConceptJS? {
        return node.concept?.toJS()
    }

    override fun getConceptUID(): String? {
        return node.getConceptReference()?.getUID()
    }

    override fun getReference(): INodeReferenceJS {
        return node.reference.serialize()
    }

    override fun getRoleInParent(): String? = node.roleInParent

    override fun getParent(): INodeJS? = node.parent?.let { NodeAdapterJS(it) }

    override fun getChildren(role: String?): Array<INodeJS> {
        // TODO use IChildLink instead of String
        return node.getChildren(role).map { NodeAdapterJS(it) }.toTypedArray()
    }

    override fun getAllChildren(): Array<INodeJS> {
        return node.allChildren.map { NodeAdapterJS(it) }.toTypedArray()
    }

    override fun moveChild(role: String?, index: Int, child: INodeJS) {
        // TODO use IChildLink instead of String
        node.moveChild(role, index.toInt(), (child as NodeAdapterJS).node)
    }

    override fun addNewChild(role: String?, index: Int, concept: IConceptJS?): INodeJS {
        val conceptRef = concept?.getUID()?.let { ConceptReference(it) }
        // TODO use IChildLink instead of String
        return NodeAdapterJS(node.addNewChild(role, index.toInt(), conceptRef))
    }

    override fun removeChild(child: INodeJS) {
        node.removeChild((child as NodeAdapterJS).node)
    }

    override fun getReferenceRoles(): Array<String> {
        return node.getReferenceRoles().toTypedArray()
    }

    override fun getReferenceTargetNode(role: String): INodeJS? {
        // TODO use IReferenceLink instead of String
        return node.getReferenceTarget(role)?.let { NodeAdapterJS(it) }
    }

    override fun getReferenceTargetRef(role: String): INodeReferenceJS? {
        // TODO use IReferenceLink instead of String
        return node.getReferenceTargetRef(role)?.serialize()
    }

    override fun setReferenceTargetNode(role: String, target: INodeJS?) {
        val unwrappedTarget = if (target == null) null else (target as NodeAdapterJS).node
        // TODO use IReferenceLink instead of String
        node.setReferenceTarget(role, unwrappedTarget)
    }

    override fun setReferenceTargetRef(role: String, target: INodeReferenceJS?) {
        // TODO use IReferenceLink instead of String
        node.setReferenceTarget(role, target?.let { INodeReferenceSerializer.deserialize(it as String) })
    }

    override fun getPropertyRoles(): Array<String> {
        return node.getPropertyRoles().toTypedArray()
    }

    override fun getPropertyValue(role: String): String? {
        // TODO use IProperty instead of String
        return node.getPropertyValue(role) ?: undefined
    }

    override fun setPropertyValue(role: String, value: String?) {
        // TODO use IProperty instead of String
        node.setPropertyValue(role, value)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as NodeAdapterJS

        if (node != other.node) return false

        return true
    }

    override fun hashCode(): Int {
        return node.hashCode()
    }
}

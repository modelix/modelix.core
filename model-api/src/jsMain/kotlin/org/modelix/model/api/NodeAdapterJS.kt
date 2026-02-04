package org.modelix.model.api

import ChildRole
import IConceptJS
import INodeJS
import INodeReferenceJS
import LanguageRegistry
import PropertyRole
import ReferenceRole
import TypedNode

@ExperimentalJsExport
@JsExport
object JSNodeConverter {
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
        if (node is TypedNode) return toINode(node._node)

        // Workaround, because ts-model-api is loaded twice by webpack making the instanceof check on TypedNode fail.
        val unwrapped = node.asDynamic().node
        if (unwrapped != null) return toINode(unwrapped)

        throw IllegalArgumentException("Unsupported node type: $node")
    }
}

// workaround: because of the missing import for INodeJS, this intermediate interface prevents it from being generated
// into the .d.ts file.
@Suppress("ClassName")
interface INodeJS_ : INodeJS

@JsExport // this is only required to prevent the compiler from renaming the methods in the generated JS
class NodeAdapterJS(val node: INode) : INodeJS_ {
    init {
        // This is called from JS, so this check is not redundant.
        require(node is INode) { "Not an INode: $node" }
    }
    override fun getConcept(): IConceptJS? {
        return getConceptUID()?.let { LanguageRegistry.INSTANCE.resolveConcept(it) }
    }

    override fun getConceptUID(): String? {
        return node.getConceptReference()?.getUID()
    }

    override fun getReference(): INodeReferenceJS {
        return node.reference.serialize()
    }

    override fun getRoleInParent(): ChildRole? = node.roleInParent

    override fun getParent(): INodeJS? = node.parent?.let { NodeAdapterJS(it) }

    override fun getChildren(role: ChildRole?): Array<INodeJS> {
        // TODO use IChildLink instead of String
        return node.getChildren(role as String?).map { NodeAdapterJS(it) }.toTypedArray()
    }

    override fun getAllChildren(): Array<INodeJS> {
        // TODO use IChildLink instead of String
        return node.allChildren.map { NodeAdapterJS(it) }.toTypedArray()
    }

    override fun moveChild(role: ChildRole?, index: Number, child: INodeJS) {
        // TODO use IChildLink instead of String
        node.moveChild(role as String?, index.toInt(), (child as NodeAdapterJS).node)
    }

    override fun addNewChild(role: ChildRole?, index: Number, concept: IConceptJS?): INodeJS {
        val conceptRef = concept?.getUID()?.let { ConceptReference(it) }
        // TODO use IChildLink instead of String
        return NodeAdapterJS(node.addNewChild(role as String?, index.toInt(), conceptRef))
    }

    override fun removeChild(child: INodeJS) {
        node.removeChild((child as NodeAdapterJS).node)
    }

    override fun remove() {
        node.remove()
    }

    override fun getReferenceRoles(): Array<ReferenceRole> {
        return node.getReferenceRoles().toTypedArray()
    }

    override fun getReferenceTargetNode(role: ReferenceRole): INodeJS? {
        // TODO use IReferenceLink instead of String
        return node.getReferenceTarget(role as String)?.let { NodeAdapterJS(it) }
    }

    override fun getReferenceTargetRef(role: ReferenceRole): INodeReferenceJS? {
        // TODO use IReferenceLink instead of String
        return node.getReferenceTargetRef(role as String)?.serialize()
    }

    @OptIn(ExperimentalJsExport::class)
    override fun setReferenceTargetNode(role: ReferenceRole, target: INodeJS?) {
        val unwrappedTarget = if (target == null) null else JSNodeConverter.toINode(target)
        // TODO use IReferenceLink instead of String
        node.setReferenceTarget(role as String, unwrappedTarget)
    }

    override fun setReferenceTargetRef(role: ReferenceRole, target: INodeReferenceJS?) {
        // TODO use IReferenceLink instead of String
        node.setReferenceTarget(role as String, target?.let { INodeReferenceSerializer.deserialize(it as String) })
    }

    override fun getPropertyRoles(): Array<PropertyRole> {
        return node.getPropertyRoles().toTypedArray()
    }

    override fun getPropertyValue(role: PropertyRole): String? {
        // TODO use IProperty instead of String
        return node.getPropertyValue(role as String) ?: undefined
    }

    override fun setPropertyValue(role: PropertyRole, value: String?) {
        // TODO use IProperty instead of String
        node.setPropertyValue(role as String, value)
    }

    @OptIn(ExperimentalJsExport::class)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        if (node != JSNodeConverter.toINode(other)) return false

        return true
    }

    override fun hashCode(): Int {
        return node.hashCode()
    }
}

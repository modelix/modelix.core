package org.modelix.model.api

import ChildRole
import IConceptJS
import INodeJS
import INodeReferenceJS
import LanguageRegistry
import PropertyRole
import ReferenceRole
import TypedNode
import org.modelix.model.api.meta.NullConcept

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
        return node.asReadableNode().getChildren(IChildLinkReference.fromRoleOrString(role)).map { NodeAdapterJS(it.asLegacyNode()) }.toTypedArray()
    }

    override fun getAllChildren(): Array<INodeJS> {
        return node.asReadableNode().getAllChildren().map { NodeAdapterJS(it.asLegacyNode()) }.toTypedArray()
    }

    override fun moveChild(role: ChildRole?, index: Number, child: INodeJS) {
        node.asWritableNode().moveChild(IChildLinkReference.fromRoleOrString(role), index.toInt(), (child as NodeAdapterJS).node.asWritableNode())
    }

    override fun addNewChild(role: ChildRole?, index: Number, concept: IConceptJS?): INodeJS {
        val conceptRef = concept?.getUID()?.let { ConceptReference(it) } ?: NullConcept.getReference()
        return node.asWritableNode().addNewChild(IChildLinkReference.fromRoleOrString(role), index.toInt(), conceptRef)
            .let { NodeAdapterJS(it.asLegacyNode()) }
    }

    override fun removeChild(child: INodeJS) {
        node.removeChild((child as NodeAdapterJS).node)
    }

    override fun remove() {
        node.remove()
    }

    override fun getReferenceRoles(): Array<ReferenceRole> {
        return node.asReadableNode().getReferenceLinks().toTypedArray()
    }

    override fun getReferenceTargetNode(role: ReferenceRole): INodeJS? {
        return node.asReadableNode().getReferenceTarget(IReferenceLinkReference.fromRoleOrString(role))
            ?.let { NodeAdapterJS(it.asLegacyNode()) }
    }

    override fun getReferenceTargetRef(role: ReferenceRole): INodeReferenceJS? {
        return node.asReadableNode().getReferenceTargetRef(IReferenceLinkReference.fromRoleOrString(role))?.serialize()
    }

    override fun setReferenceTargetNode(role: ReferenceRole, target: INodeJS?) {
        val unwrappedTarget = if (target == null) null else (target as NodeAdapterJS).node
        node.asWritableNode().setReferenceTarget(IReferenceLinkReference.fromRoleOrString(role), unwrappedTarget?.asWritableNode())
    }

    override fun setReferenceTargetRef(role: ReferenceRole, target: INodeReferenceJS?) {
        node.asWritableNode().setReferenceTargetRef(
            IReferenceLinkReference.fromRoleOrString(role),
            target
                ?.let { INodeReferenceSerializer.deserialize(it as String) },
        )
    }

    override fun getPropertyRoles(): Array<PropertyRole> {
        return node.getPropertyRoles().toTypedArray()
    }

    override fun getPropertyValue(role: PropertyRole): String? {
        return node.asReadableNode().getPropertyValue(IPropertyReference.fromRoleOrString(role)) ?: undefined
    }

    override fun setPropertyValue(role: PropertyRole, value: String?) {
        node.asWritableNode().setPropertyValue(IPropertyReference.fromRoleOrString(role), value)
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

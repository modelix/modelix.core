package org.modelix.model.api

import IConceptJS
import INodeJS
import INodeReferenceJS

@ExperimentalJsExport
@JsExport
object JSNodeConverter {
    fun nodeToJs(node: INode): Any {
        // return type is Any because the import for INodeJS is missing in the generated .d.ts
        return NodeAdapterJS(node)
    }

    fun nodeFromJs(node: Any): Any {
        return (node as NodeAdapterJS).node
    }

    fun unwrapNode(node: Any): Any {
        return if (node is NodeAdapterJS) node.node else node
    }

    fun isJsNode(node: Any): Boolean {
        return node is NodeAdapterJS
    }
}



// workaround: because of the missing import for INodeJS, this intermediate interface prevents it from being generated
// into the .d.ts file.
interface INodeJS_ : INodeJS

@JsExport // this is only required to prevent the compiler from renaming the methods in the generated JS
class NodeAdapterJS(val node: INode) : INodeJS_ {
    init {
        require(node is INode) { "Not an INode: $node" }
    }
    override fun getConcept(): IConceptJS? {
        TODO("Not yet implemented")
    }

    override fun getConceptUID(): String? {
        return node.getConceptReference()?.getUID()
    }

    override fun getReference(): INodeReferenceJS {
        TODO("Not yet implemented")
    }

    override fun getRoleInParent(): String? = node.roleInParent

    override fun getParent(): INodeJS? = node.parent?.let { NodeAdapterJS(it) }

    override fun getChildren(role: String?): Array<INodeJS> {
        return node.getChildren(role).map { NodeAdapterJS(it) }.toTypedArray()
    }

    override fun getAllChildren(): Array<INodeJS> {
        return node.allChildren.map { NodeAdapterJS(it) }.toTypedArray()
    }

    override fun moveChild(role: String?, index: Number, child: INodeJS) {
        TODO("Not yet implemented")
    }

    override fun addNewChild(role: String?, index: Number, concept: IConceptJS?): INodeJS {
        TODO("Not yet implemented")
    }

    override fun removeChild(child: INodeJS) {
        node.removeChild((child as NodeAdapterJS).node)
    }

    override fun getReferenceRoles(): Array<String> {
        return node.getReferenceRoles().toTypedArray()
    }

    override fun getReferenceTargetNode(role: String): INodeJS? {
        TODO("Not yet implemented")
    }

    override fun getReferenceTargetRef(role: String): INodeReferenceJS? {
        TODO("Not yet implemented")
    }

    override fun setReferenceTargetNode(role: String, target: INodeJS?) {
        TODO("Not yet implemented")
    }

    override fun setReferenceTargetRef(role: String, target: INodeReferenceJS?) {
        TODO("Not yet implemented")
    }

    override fun getPropertyRoles(): Array<String> {
        return node.getPropertyRoles().toTypedArray()
    }

    override fun getPropertyValue(role: String): String? {
        return node.getPropertyValue(role)
    }

    override fun setPropertyValue(role: String, value: String?) {
        node.setPropertyValue(role, value)
    }
}
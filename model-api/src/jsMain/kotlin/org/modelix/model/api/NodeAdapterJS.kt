package org.modelix.model.api

import IConceptJS
import IConceptReferenceJS
import INodeJS
import INodeReferenceJS

@ExperimentalJsExport
@JsExport
fun nodeToJs(node: INode): Any {
    // return type is Any because the import for INodeJS is missing in the generated .d.ts
    return NodeAdapterJS(node)
}

class NodeAdapterJS(val node: INode) : INodeJS {
    override fun getConcept(): IConceptJS? {
        TODO("Not yet implemented")
    }

    override fun getConceptReference(): IConceptReferenceJS? {
        TODO("Not yet implemented")
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
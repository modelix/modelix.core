package org.modelix.model.api

interface IMutableNode {
    fun moveChild(role: IChildLinkReference, index: Int, child: INode)
    fun addNewChild(role: IChildLinkReference, index: Int, concept: IConcept): INode
    fun addNewChild(role: IChildLinkReference, index: Int, concept: ConceptReference): INode

    fun setReferenceTarget(link: IReferenceLinkReference, target: INode)
    fun setReferenceTargetRef(role: IReferenceLinkReference, target: INodeReference)
    fun removeReference(role: IReferenceLinkReference)

    fun setPropertyValue(property: IPropertyReference, value: String)
    fun removeProperty(property: IPropertyReference)
}

fun IMutableNode.setPropertyValue(property: IPropertyReference, value: String?) {
    if (value == null) {
        removeProperty(property)
    } else {
        setPropertyValue(property, value)
    }
}

fun IMutableNode.setReferenceTarget(link: IReferenceLinkReference, target: INode?) {
    if (target == null) {
        removeReference(link)
    } else {
        setReferenceTarget(link, target)
    }
}

fun IMutableNode.setReferenceTargetRef(link: IReferenceLinkReference, target: INodeReference?) {
    if (target == null) {
        removeReference(link)
    } else {
        setReferenceTargetRef(link, target)
    }
}

package org.modelix.model.api

fun INode.getDescendants(includeSelf: Boolean): Sequence<INode> {
    return if (includeSelf) {
        (sequenceOf(this) + this.getDescendants(false))
    } else {
        this.allChildren.asSequence().flatMap { it.getDescendants(true) }
    }
}

fun IReadableNode.getDescendants(includeSelf: Boolean): Sequence<IReadableNode> {
    return if (includeSelf) {
        (sequenceOf(this) + this.getDescendants(false))
    } else {
        this.getAllChildren().asSequence().flatMap { it.getDescendants(true) }
    }
}

fun IWritableNode.getDescendants(includeSelf: Boolean): Sequence<IWritableNode> {
    return if (includeSelf) {
        (sequenceOf(this) + this.getDescendants(false))
    } else {
        this.getAllChildren().asSequence().flatMap { it.getDescendants(true) }
    }
}

fun INode?.getAncestor(concept: IConcept?, includeSelf: Boolean): INode? {
    if (this == null) {
        return null
    }
    return if (includeSelf && this.concept!!.isSubConceptOf(concept)) {
        this
    } else {
        this.parent.getAncestor(concept, true)
    }
}

fun INode.getAncestors(includeSelf: Boolean = false): Sequence<INode> {
    return generateSequence(if (includeSelf) this else parent) { it.parent }
}

fun deepUnwrapNode(node: INode): INode {
    return if (node is INodeWrapper) deepUnwrapNode(node.getWrappedNode()) else node
}

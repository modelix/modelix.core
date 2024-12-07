package org.modelix.metamodel

import org.modelix.model.api.INode
import org.modelix.model.api.getDescendants
import kotlin.jvm.JvmName

@JvmName("nodesOfType")
inline fun <reified NodeT : ITypedNode> Iterable<INode>.ofType(): List<NodeT> = map { it.typed() }.filterIsInstance<NodeT>()

@JvmName("nodesOfType")
inline fun <reified NodeT : ITypedNode> Sequence<INode>.ofType(): Sequence<NodeT> = map { it.typed() }.filterIsInstance<NodeT>()

@JvmName("typedNodesOfType")
inline fun <reified NodeT : ITypedNode> Iterable<ITypedNode>.ofType(): List<NodeT> = filterIsInstance<NodeT>()

@JvmName("typedNodesOfType")
inline fun <reified NodeT : ITypedNode> Sequence<ITypedNode>.ofType(): Sequence<NodeT> = filterIsInstance<NodeT>()

@JvmName("descendantsOfType")
inline fun <reified NodeT : ITypedNode> ITypedNode.descendants(includeSelf: Boolean = false): Sequence<NodeT> {
    return descendants(includeSelf).ofType<NodeT>()
}

fun ITypedNode.descendants(includeSelf: Boolean = false): Sequence<ITypedNode> {
    return untyped().getDescendants(includeSelf).map { it.typed() }
}

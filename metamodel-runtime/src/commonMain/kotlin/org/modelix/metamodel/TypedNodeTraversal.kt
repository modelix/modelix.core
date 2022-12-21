/*
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
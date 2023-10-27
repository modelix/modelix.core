import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.typed
import org.modelix.model.api.INode

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

@JsExport
object TypedNodeConverter {
    fun toTypedNode(node: Any): ITypedNode {
        if (node is ITypedNode) return node
        return JSNodeConverter.toINode(node).typed()
    }

    fun toINode(node: Any): INode {
        if (node is ITypedNode) return toINode(node.unwrap())
        return JSNodeConverter.toINode(node)
    }

    fun isSameNode(node1: Any, node2: Any) = JSNodeConverter.isSameNode(toINode(node1), toINode(node2))
}

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

package org.modelix.metamodel

import org.modelix.model.api.INode

actual fun <NodeT : ITypedNode> createNodeProxy(
    node: INode,
    concept: IConceptOfTypedNode<NodeT>,
    handler: IProxyNodeHandler
): NodeT {
    return js("""
        new Proxy(node, {
            get: function(target, property) {
                return target[property];
            },
            set: function(target, property, value) {
                target[property] = value;
            },
            apply: function(target, thisArg, args) {
                
            }
        });
    """)
}
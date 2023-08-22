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
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

actual fun <NodeT : ITypedNode> createNodeProxy(
    node: INode,
    concept: IConceptOfTypedNode<NodeT>,
    handler: IProxyNodeHandler
): NodeT {
    return Proxy.newProxyInstance(concept.javaClass.classLoader, arrayOf(concept.getInstanceInterface().java), object : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
            require(method != null)
            return handler.handleMethodCall(method.name)
        }
    }) as NodeT
}
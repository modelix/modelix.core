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

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink

expect fun <NodeT : ITypedNode> createNodeProxy(node: INode, concept: IConceptOfTypedNode<NodeT>, handler: IProxyNodeHandler): NodeT

fun <NodeT : ITypedNode> createNodeProxy(node: INode, concept: IConceptOfTypedNode<NodeT>): NodeT {
    return createNodeProxy(node, concept, DefaultProxyNodeHandler(node, concept))
}

interface IProxyNodeHandler {
    fun handleMethodCall(methodName: String): Any?
}

class DefaultProxyNodeHandler<NodeT : ITypedNode>(val node: INode, val concept: IConceptOfTypedNode<NodeT>) : IProxyNodeHandler {
    override fun handleMethodCall(methodName: String): Any? {
        if (methodName.startsWith("get")) {
            val roleName = methodName.substring(3, 4).lowercase() + methodName.substring(4)
            val generatedConcept = concept.untyped() as GeneratedConcept<NodeT, IConceptOfTypedNode<NodeT>>
            val role = generatedConcept.getRoleByName(roleName)
            when (role) {
                is IProperty -> return node.getPropertyValue(role)
                is IReferenceLink -> return node.getReferenceTarget(role)
                is IChildLink -> return node.getChildren(role)
            }
        } else if (methodName.startsWith("set")) {
            val roleName = methodName.substring(3, 4).lowercase() + methodName.substring(4)
        }
        throw UnsupportedOperationException("Not yet implemented: $methodName")
    }
}
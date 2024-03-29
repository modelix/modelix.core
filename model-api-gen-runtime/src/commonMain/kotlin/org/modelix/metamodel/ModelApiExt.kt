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
import org.modelix.model.api.addNewChild

inline fun <reified NodeT : ITypedNode> INode.addNewChild(concept: INonAbstractConcept<NodeT>) =
    addNewChild(null, concept.untyped()).typed<NodeT>()

inline fun <reified NodeT : ITypedNode> INode.addNewChild(role: String, concept: INonAbstractConcept<NodeT>) =
    addNewChild(role, concept.untyped()).typed<NodeT>()

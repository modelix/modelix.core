package org.modelix.metamodel

import org.modelix.model.api.INode
import org.modelix.model.api.addNewChild

inline fun <reified NodeT : ITypedNode> INode.addNewChild(concept: INonAbstractConcept<NodeT>) =
    addNewChild(null, concept.untyped()).typed<NodeT>()

inline fun <reified NodeT : ITypedNode> INode.addNewChild(role: String, concept: INonAbstractConcept<NodeT>) =
    addNewChild(role, concept.untyped()).typed<NodeT>()

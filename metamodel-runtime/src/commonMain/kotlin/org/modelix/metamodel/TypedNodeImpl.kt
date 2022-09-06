package org.modelix.metamodel

import org.modelix.model.api.INode

abstract class TypedNodeImpl(override val _node: INode) : ITypedNode {

    init {
        require(_node.concept == _concept) { "Concept of node $_node expected to be $_concept, but was ${_node.concept}" }
        (_concept._concept.language as? GeneratedLanguage)?.assertRegistered()
    }
}


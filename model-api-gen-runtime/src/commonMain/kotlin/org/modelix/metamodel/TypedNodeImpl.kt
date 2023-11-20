package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode

abstract class TypedNodeImpl(val wrappedNode: INode) : ITypedNode {

    init {
        val expected: IConcept = _concept._concept
        val actual: IConcept? = unwrap().concept
        require(actual != null && actual.isSubConceptOf(expected)) {
            "Concept of node ${unwrap()} expected to be a sub-concept of $expected, but was $actual"
        }
        (expected.language as? GeneratedLanguage)?.assertRegistered()
    }

    override fun unwrap(): INode {
        return wrappedNode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TypedNodeImpl

        if (wrappedNode != other.wrappedNode) return false

        return true
    }

    override fun hashCode(): Int {
        return wrappedNode.hashCode()
    }
}

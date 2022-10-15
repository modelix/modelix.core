package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.js.JsExport

@JsExport
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
}


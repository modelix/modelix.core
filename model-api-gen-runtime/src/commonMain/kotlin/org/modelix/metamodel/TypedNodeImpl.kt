/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.metamodel

import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.getConcept

abstract class TypedNodeImpl(val wrappedNode: INode) : ITypedNode {

    init {
        val expected: IConcept = _concept._concept
        val actual: IConcept? = unwrap().getConcept()
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

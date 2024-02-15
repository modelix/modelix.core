/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.api

/**
 * Representation of a parent-child relationship between [IConcept]s.
 */
interface IChildLink : ILink {
    /**
     * Specifies if the parent-child relation ship is 1:n.
     */
    val isMultiple: Boolean

    @Deprecated("use .targetConcept")
    val childConcept: IConcept

    companion object {
        fun fromName(name: String): IChildLink = ChildLinkFromName(name)
    }

    /**
     * Whether children with this role are returned in a meaningful order and whether they are allowed to be reordered.
     *
     * Children returned for an unordered role might be returned in a different order in subsequent request.
     * If a child role is not ordered, implementations of [[INode.moveChild]] are allowed to fail
     * when instructed to move a node in between existing nodes.
     */
    val isOrdered
        get() = true
}

data class ChildLinkFromName(override val name: String) : LinkFromName(), IChildLink {
    override val isMultiple: Boolean
        get() = throw UnsupportedOperationException()
    override val childConcept: IConcept
        get() = throw UnsupportedOperationException()
}

object NullChildLink : IChildLink {
    override val isMultiple: Boolean
        get() = true
    override val childConcept: IConcept
        get() = throw UnsupportedOperationException()
    override val targetConcept: IConcept
        get() = throw UnsupportedOperationException()

    override fun getConcept(): IConcept {
        throw UnsupportedOperationException()
    }

    override fun getUID(): String {
        throw UnsupportedOperationException()
    }

    override fun getSimpleName(): String {
        throw UnsupportedOperationException()
    }

    override val isOptional: Boolean
        get() = true
}
